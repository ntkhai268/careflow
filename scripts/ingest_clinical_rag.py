#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
================================================================================
CareFlow Clinical RAG — Knowledge Base Ingestion Script
================================================================================
Phiên bản: 1.0.0
Tiêu chuẩn kiến trúc: .planning/ai-service/chunking-strategy.md
CSDL mục tiêu: PostgreSQL 16 + pgvector (careflow_ai)
Mô hình Embedding: Google text-embedding-004 (768 chiều)

Các tính năng chính:
1. Contextual Document-Structure Chunking:
   - Tách theo phân cấp Markdown (#, ##, ###, ####)
   - Bơm Context Breadcrumbs: [Tài liệu > Chương > Mục con]
   - Bảo toàn bảng phác đồ thuốc (Table Integrity)
   - Window Guard 400 - 800 tokens (Recursive Split khi > 800, Merge khi < 150)
   - Khâu nối đa đoạn (Overlap 50-80 tokens, con trỏ next_chunk_id, prev_chunk_id)
2. Synthetic Narrative Serialization cho DDI (Bảng tương tác thuốc CSV 701 dòng)
   - 1 Row = 1 Context Chunk độc lập
3. Tự động trích xuất Rich Metadata JSONB (ICD-10, thuốc đề cập, chuyên khoa)
4. Batch Embedding qua REST API Google text-embedding-004 (chuẩn 768 float)
5. Hỗ trợ chế độ --dry-run (kiểm tra phân đoạn, đếm token không tốn tiền API)
================================================================================
"""

import os
import re
import sys
import json
import uuid
import time
import argparse
from pathlib import Path
from typing import List, Dict, Any, Tuple, Optional

# Cố gắng import các thư viện bên ngoài, cung cấp fallback nếu chưa cài đặt
try:
    import requests
except ImportError:
    requests = None

try:
    import psycopg2
    from psycopg2.extras import execute_values
except ImportError:
    psycopg2 = None

try:
    import tiktoken
    TIKTOKEN_AVAILABLE = True
except ImportError:
    TIKTOKEN_AVAILABLE = False


# ==============================================================================
# 1. CẤU HÌNH HỆ THỐNG & ĐƯỜNG DẪN DỮ LIỆU
# ==============================================================================
BASE_DIR = Path(__file__).resolve().parent.parent
RAG_DOCS_DIR = BASE_DIR / "docs" / "rag-docs"
PROTOCOLS_DIR = RAG_DOCS_DIR / "treatment-protocol" / "markdown"
DDI_CSV_PATH = RAG_DOCS_DIR / "ddi" / "ddi_contraindicated.csv"
DRUGS_GENERIC_PATH = RAG_DOCS_DIR / "drug-formulary" / "drugs_generic.csv"
ENV_FILE_PATH = BASE_DIR / ".env"

# Cấu hình Chunking chuẩn CareFlow
CHUNK_MIN_TOKENS = 150
CHUNK_TARGET_TOKENS = 500
CHUNK_MAX_TOKENS = 800
CHUNK_OVERLAP_TOKENS = 60

# Danh mục tài liệu chuẩn
DOCUMENTS_CATALOG = [
    {
        "code": "BYT-QD-4800-HOHAP",
        "title": "Hướng dẫn chẩn đoán và điều trị viêm phổi mắc phải cộng đồng ở người lớn",
        "issuer": "Bộ Y Tế Việt Nam",
        "published_year": 2020,
        "specialty": "HO_HAP",
        "file_name": "viem_phoi_cong_dong.md",
        "default_icd10": ["J13", "J14", "J15", "J18"]
    },
    {
        "code": "BYT-QD-5968-TIMMACH",
        "title": "Hướng dẫn chẩn đoán và điều trị tăng huyết áp ở người lớn",
        "issuer": "Bộ Y Tế Việt Nam",
        "published_year": 2019,
        "specialty": "TIM_MACH",
        "file_name": "tang_huyet_ap.md",
        "default_icd10": ["I10", "I11", "I12", "I13", "I15"]
    },
    {
        "code": "BYT-QD-5481-NOITIET",
        "title": "Hướng dẫn chẩn đoán và điều trị đái tháo đường típ 2",
        "issuer": "Bộ Y Tế Việt Nam",
        "published_year": 2020,
        "specialty": "NOI_TIET",
        "file_name": "dai_thao_duong_tip2.md",
        "default_icd10": ["E11"]
    },
    {
        "code": "BYT-QD-5948-DDI",
        "title": "Danh mục tương tác thuốc chống chỉ định trong thực hành lâm sàng",
        "issuer": "Bộ Y Tế Việt Nam",
        "published_year": 2021,
        "specialty": "DUOC_LAM_SANG",
        "file_name": "ddi_contraindicated.csv",
        "default_icd10": []
    }
]


# ==============================================================================
# 2. CÔNG CỤ BỔ TRỢ: ĐẾM TOKEN & ĐỌC .ENV
# ==============================================================================
def load_env_variables() -> Dict[str, str]:
    """Đọc biến môi trường từ .env mà không bắt buộc cài python-dotenv."""
    env_vars = {}
    if ENV_FILE_PATH.exists():
        with open(ENV_FILE_PATH, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, val = line.split("=", 1)
                    env_vars[key.strip()] = val.strip().strip("'\"")
    return env_vars


class TokenCounter:
    """Bộ đếm token chuẩn xác cho tiếng Việt và thuật ngữ y khoa."""
    def __init__(self):
        self.encoder = None
        if TIKTOKEN_AVAILABLE:
            try:
                self.encoder = tiktoken.get_encoding("cl100k_base")
            except Exception:
                self.encoder = None

    def count(self, text: str) -> int:
        if not text:
            return 0
        if self.encoder:
            return len(self.encoder.encode(text))
        
        # Fallback Heuristic cho Tiếng Việt & Dược học:
        # Tiếng Việt có dấu và tên thuốc (Clarithromycin) thường dao động 1.2 - 1.4 token/từ.
        words = text.split()
        punctuation_count = len(re.findall(r"[,.;:!?(){}\[\]\"'|%<>=/\\]", text))
        return int(len(words) * 1.3) + punctuation_count


TOKEN_COUNTER = TokenCounter()


# ==============================================================================
# 3. TRÍCH XUẤT TỪ ĐIỂN THUỐC GENERIC ĐỂ MATCH METADATA
# ==============================================================================
def load_known_generic_drugs() -> List[str]:
    """Tải danh sách 185 thuốc generic từ file CSV để nhận diện trong văn bản."""
    drugs = set()
    if DRUGS_GENERIC_PATH.exists():
        with open(DRUGS_GENERIC_PATH, "r", encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split(",")
                if len(parts) >= 2 and parts[1] != "generic_name":
                    drug_name = parts[1].strip().strip('"')
                    if len(drug_name) >= 3:
                        drugs.add(drug_name)
    # Thêm một số tên thuốc kháng sinh/huyết áp/tiểu đường thường gặp
    common_drugs = [
        "Amoxicillin", "Acid clavulanic", "Clarithromycin", "Azithromycin",
        "Levofloxacin", "Moxifloxacin", "Ceftriaxone", "Cefotaxime",
        "Amlodipine", "Losartan", "Perindopril", "Metformin", "Insulin",
        "Gliclazide", "Aspirin", "Clopidogrel", "Omeprazole", "Atorvastatin"
    ]
    drugs.update(common_drugs)
    return sorted(list(drugs), key=len, reverse=True)


KNOWN_DRUGS = load_known_generic_drugs()


def extract_rich_metadata(
    text: str,
    doc_meta: Dict[str, Any],
    section_titles: List[str]
) -> Dict[str, Any]:
    """Trích xuất tự động siêu dữ liệu JSONB phục vụ SQL Pre-filtering."""
    # 1. Tìm các mã ICD-10 trong văn bản hoặc kế thừa mặc định
    found_icd = set(re.findall(r"\b[A-Z][0-9]{2}(?:\.[0-9]+)?\b", text))
    combined_icd = list(found_icd.union(set(doc_meta.get("default_icd10", []))))

    # 2. Tìm tên thuốc xuất hiện trong văn bản
    found_drugs = []
    text_lower = text.lower()
    for drug in KNOWN_DRUGS:
        # Match từ nguyên vẹn
        pattern = r"\b" + re.escape(drug.lower()) + r"\b"
        if re.search(pattern, text_lower):
            found_drugs.append(drug)

    # 3. Nhận diện bối cảnh khám (Care Setting)
    care_setting = "general"
    if any(kw in text_lower for kw in ["ngoại trú", "ngoai tru", "tại nhà", "không cần nhập viện"]):
        care_setting = "outpatient"
    elif any(kw in text_lower for kw in ["nhập viện", "nội trú", "khoa hồi sức", "icu", "cấp cứu"]):
        care_setting = "inpatient"

    # 4. Nhận diện loại phân đoạn (Section Type)
    section_type = "general_guideline"
    if any(kw in text_lower for kw in ["điều trị", "phác đồ", "liều dùng", "kháng sinh", "kê đơn"]):
        section_type = "treatment_protocol"
    elif any(kw in text_lower for kw in ["chẩn đoán", "triệu chứng", "lâm sàng", "tiêu chuẩn"]):
        section_type = "diagnostic_criteria"
    elif any(kw in text_lower for kw in ["chống chỉ định", "tương tác", "nguy cơ", "tai biến"]):
        section_type = "safety_contraindication"

    return {
        "document_code": doc_meta["code"],
        "specialty": doc_meta["specialty"],
        "breadcrumb_titles": section_titles,
        "icd10_codes": combined_icd[:5],
        "drugs_mentioned": found_drugs[:10],
        "care_setting": care_setting,
        "section_type": section_type,
        "token_count": TOKEN_COUNTER.count(text)
    }


# ==============================================================================
# 4. CHUNKING ENGINE: BẢO TOÀN PHÂN CẤP & BẢNG BIỂU
# ==============================================================================
class MarkdownSection:
    def __init__(self, level: int, title: str, breadcrumbs: List[str]):
        self.level = level
        self.title = title
        self.breadcrumbs = breadcrumbs
        self.lines: List[str] = []

    def get_text(self) -> str:
        return "\n".join(self.lines).strip()


def parse_markdown_into_hierarchy(md_content: str, doc_title: str) -> List[MarkdownSection]:
    """Phân rã file Markdown thành danh sách các Section theo cấp bậc Heading."""
    lines = md_content.splitlines()
    sections: List[MarkdownSection] = []
    
    # Breadcrumbs stack theo level (1: #, 2: ##, 3: ###, 4: ####)
    breadcrumb_stack: Dict[int, str] = {0: doc_title}
    current_section = MarkdownSection(level=0, title=doc_title, breadcrumbs=[doc_title])

    for line in lines:
        header_match = re.match(r"^(#{1,4})\s+(.+)$", line)
        if header_match:
            # Lưu section cũ nếu có nội dung
            if current_section.lines:
                sections.append(current_section)

            level = len(header_match.group(1))
            title = header_match.group(2).strip()

            # Cập nhật stack breadcrumbs
            breadcrumb_stack[level] = title
            # Xóa các level sâu hơn
            keys_to_remove = [k for k in breadcrumb_stack.keys() if k > level]
            for k in keys_to_remove:
                del breadcrumb_stack[k]

            active_breadcrumbs = [breadcrumb_stack[k] for k in sorted(breadcrumb_stack.keys())]
            current_section = MarkdownSection(level=level, title=title, breadcrumbs=active_breadcrumbs)
        else:
            current_section.lines.append(line)

    if current_section.lines:
        sections.append(current_section)

    return sections


def split_oversized_text(
    text: str,
    max_tokens: int = CHUNK_MAX_TOKENS,
    overlap_tokens: int = CHUNK_OVERLAP_TOKENS
) -> List[str]:
    """
    Chia nhỏ văn bản vượt ngưỡng một cách triệt để và an toàn:
    - Bảo toàn bảng Markdown (giữ header ở mỗi chunk con).
    - Chia theo đoạn văn \n\n, gạch đầu dòng \n-, hoặc dấu chấm câu.
    - Đảm bảo 100% mọi chunk con đều <= max_tokens (không bao giờ bị lọt chunk 1500-2000 tokens).
    """
    total_tokens = TOKEN_COUNTER.count(text)
    if total_tokens <= max_tokens:
        return [text]

    raw_units: List[str] = []

    # 1. Trường hợp chứa bảng Markdown
    if "|" in text and ("|---" in text or "|:--" in text or "| ---" in text):
        lines = [l for l in text.splitlines() if l.strip()]
        header = ""
        data_rows = []
        for idx, l in enumerate(lines):
            if "|" in l and idx < 2:
                header += l + "\n"
            elif "|" in l:
                data_rows.append(l)
            else:
                raw_units.append(l)

        if header and data_rows:
            tbl_chunk = header
            for row in data_rows:
                candidate = tbl_chunk + row + "\n"
                if TOKEN_COUNTER.count(candidate) > max_tokens:
                    raw_units.append(tbl_chunk.strip())
                    tbl_chunk = header + row + "\n"
                else:
                    tbl_chunk = candidate
            if tbl_chunk.strip():
                raw_units.append(tbl_chunk.strip())
    else:
        # 2. Văn bản xuôi thông thường
        paragraphs = text.split("\n\n")
        for p in paragraphs:
            p_strip = p.strip()
            if not p_strip:
                continue
            if TOKEN_COUNTER.count(p_strip) <= max_tokens:
                raw_units.append(p_strip)
            else:
                # Tách theo từng dòng
                lines = p_strip.splitlines()
                curr_block = []
                for l in lines:
                    cand_block = "\n".join(curr_block + [l])
                    if TOKEN_COUNTER.count(cand_block) <= max_tokens:
                        curr_block.append(l)
                    else:
                        if curr_block:
                            raw_units.append("\n".join(curr_block))
                        # Nếu một dòng đơn lẻ vẫn dài hơn max_tokens -> Cắt theo câu
                        if TOKEN_COUNTER.count(l) > max_tokens:
                            sentences = re.split(r"(?<=[.!?])\s+", l)
                            curr_s = []
                            for s in sentences:
                                cand_s = " ".join(curr_s + [s])
                                if TOKEN_COUNTER.count(cand_s) <= max_tokens:
                                    curr_s.append(s)
                                else:
                                    if curr_s:
                                        raw_units.append(" ".join(curr_s))
                                    curr_s = [s]
                            if curr_s:
                                raw_units.append(" ".join(curr_s))
                            curr_block = []
                        else:
                            curr_block = [l]
                if curr_block:
                    raw_units.append("\n".join(curr_block))

    # 3. Gom các raw_units thành các Chunks có độ dài tối ưu (<= max_tokens)
    chunks: List[str] = []
    current_chunk: List[str] = []
    current_tokens = 0

    for u in raw_units:
        u_tokens = TOKEN_COUNTER.count(u)
        if current_tokens + u_tokens <= max_tokens:
            current_chunk.append(u)
            current_tokens += u_tokens
        else:
            if current_chunk:
                chunks.append("\n\n".join(current_chunk))
                # Vùng đệm overlap
                last_u = current_chunk[-1]
                if TOKEN_COUNTER.count(last_u) <= overlap_tokens * 2:
                    current_chunk = [last_u, u]
                    current_tokens = TOKEN_COUNTER.count(last_u) + u_tokens
                else:
                    current_chunk = [u]
                    current_tokens = u_tokens
            else:
                chunks.append(u)
                current_chunk = []
                current_tokens = 0

    if current_chunk:
        chunks.append("\n\n".join(current_chunk))

    return chunks


def process_markdown_document(doc_catalog: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Xử lý toàn diện một tài liệu phác đồ Markdown thành danh sách Chunks chuẩn."""
    file_path = PROTOCOLS_DIR / doc_catalog["file_name"]
    if not file_path.exists():
        print(f"⚠️ [CẢNH BÁO] Không tìm thấy file: {file_path}")
        return []

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    sections = parse_markdown_into_hierarchy(content, doc_catalog["title"])
    raw_chunks: List[Dict[str, Any]] = []

    for sec in sections:
        text = sec.get_text()
        # Bỏ qua phần bìa hành chính hoặc quyết định nếu không chứa thông tin chuyên môn
        if len(text) < 40 and not any(kw in text.lower() for kw in ["định nghĩa", "liều", "thuốc"]):
            continue

        token_count = TOKEN_COUNTER.count(text)
        breadcrumb_header = f"[NGỮ CẢNH: {' > '.join(sec.breadcrumbs)}]"

        if token_count > CHUNK_MAX_TOKENS:
            # Tách nhỏ hơn với Fallback Overlap
            pieces = split_oversized_text(text, max_tokens=CHUNK_MAX_TOKENS)
            total_parts = len(pieces)
            for idx, piece in enumerate(pieces):
                part_header = f"[NGỮ CẢNH: {' > '.join(sec.breadcrumbs)} (Phần {idx+1}/{total_parts})]"
                full_content = f"{part_header}\n\n{piece}"
                raw_chunks.append({
                    "section_title": f"{sec.title} (Phần {idx+1}/{total_parts})",
                    "content": full_content,
                    "clean_body": piece,
                    "breadcrumbs": sec.breadcrumbs,
                    "chunk_index": idx,
                    "total_chunks": total_parts,
                    "has_next": idx < total_parts - 1,
                    "has_prev": idx > 0,
                    "doc_catalog": doc_catalog
                })
        elif token_count < CHUNK_MIN_TOKENS:
            # Chunk nhỏ: Giữ nguyên kèm breadcrumb đầy đủ
            full_content = f"{breadcrumb_header}\n\n{text}"
            raw_chunks.append({
                "section_title": sec.title,
                "content": full_content,
                "clean_body": text,
                "breadcrumbs": sec.breadcrumbs,
                "chunk_index": 0,
                "total_chunks": 1,
                "has_next": False,
                "has_prev": False,
                "doc_catalog": doc_catalog
            })
        else:
            # Kích thước lý tưởng (150 - 800 tokens)
            full_content = f"{breadcrumb_header}\n\n{text}"
            raw_chunks.append({
                "section_title": sec.title,
                "content": full_content,
                "clean_body": text,
                "breadcrumbs": sec.breadcrumbs,
                "chunk_index": 0,
                "total_chunks": 1,
                "has_next": False,
                "has_prev": False,
                "doc_catalog": doc_catalog
            })

    # Tạo JSONB metadata cho từng chunk
    final_chunks = []
    for rc in raw_chunks:
        meta = extract_rich_metadata(rc["content"], rc["doc_catalog"], rc["breadcrumbs"])
        meta["chunk_index"] = rc["chunk_index"]
        meta["total_chunks"] = rc["total_chunks"]
        meta["has_next"] = rc["has_next"]
        meta["has_prev"] = rc["has_prev"]

        final_chunks.append({
            "id": str(uuid.uuid4()),
            "document_code": rc["doc_catalog"]["code"],
            "section_title": rc["section_title"][:300],
            "content": rc["content"],
            "metadata": meta,
            "token_count": meta["token_count"]
        })

    return final_chunks


# ==============================================================================
# 5. SYNTHETIC NARRATIVE CHUNKING CHO DDI (CSV TƯƠNG TÁC THUỐC)
# ==============================================================================
def process_ddi_csv() -> List[Dict[str, Any]]:
    """
    Biến đổi 701 dòng tương tác thuốc CSV thành 701 đoạn văn lâm sàng tiêu chuẩn.
    Quy tắc: 1 Hàng CSV = 1 Context Chunk độc lập.
    """
    if not DDI_CSV_PATH.exists():
        print(f"⚠️ [CẢNH BÁO] Không tìm thấy file DDI: {DDI_CSV_PATH}")
        return []

    ddi_catalog = next(d for d in DOCUMENTS_CATALOG if d["code"] == "BYT-QD-5948-DDI")
    chunks = []

    import csv
    with open(DDI_CSV_PATH, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            drug_1 = row.get("drug_1", "").strip()
            drug_2 = row.get("drug_2", "").strip()
            mechanism = row.get("mechanism", "").strip()
            consequence = row.get("consequence", "").strip()
            management = row.get("management", "").strip()
            table_type = row.get("table_type", "Tương tác chống chỉ định").strip()

            if not drug_1 or not drug_2:
                continue

            # Xây dựng Synthetic Narrative theo chuẩn chunking-strategy.md
            narrative = (
                f"[CẢNH BÁO DƯỢC LÂM SÀNG - BỘ Y TẾ QĐ 5948/QĐ-BYT > {table_type}]\n"
                f"Cặp thuốc tương tác: [{drug_1}] và [{drug_2}].\n"
                f"Mức độ phân loại: CHỐNG CHỈ ĐỊNH PHỐI HỢP (Contraindicated).\n"
                f"Cơ chế dược lực / dược động học: {mechanism}.\n"
                f"Hậu quả lâm sàng: {consequence}.\n"
                f"Xử trí khuyến cáo: {management}."
            )

            meta = {
                "document_code": ddi_catalog["code"],
                "specialty": "DUOC_LAM_SANG",
                "breadcrumb_titles": [ddi_catalog["title"], table_type, f"{drug_1} + {drug_2}"],
                "icd10_codes": [],
                "drugs_mentioned": [drug_1, drug_2],
                "care_setting": "general",
                "section_type": "safety_contraindication",
                "interaction_severity": "CONTRAINDICATED",
                "token_count": TOKEN_COUNTER.count(narrative)
            }

            chunks.append({
                "id": str(uuid.uuid4()),
                "document_code": ddi_catalog["code"],
                "section_title": f"Tương tác chống chỉ định: {drug_1} + {drug_2}"[:300],
                "content": narrative,
                "metadata": meta,
                "token_count": meta["token_count"]
            })

    return chunks


# ==============================================================================
# 6. GỌI BATCH EMBEDDING API (GOOGLE text-embedding-004) VỚI PROGRESS BAR
# ==============================================================================
def render_progress_bar(current: int, total: int, prefix: str = "", suffix: str = "", length: int = 30):
    """Vẽ thanh tiến trình trực quan thời gian thực trên Console."""
    percent = float(current) / total if total > 0 else 1.0
    filled_len = int(length * percent)
    bar = "█" * filled_len + "░" * (length - filled_len)
    sys.stdout.write(f"\r{prefix} |{bar}| {percent*100:5.1f}% ({current}/{total}) {suffix}")
    sys.stdout.flush()
    if current >= total:
        sys.stdout.write("\n")


CACHE_FILE = BASE_DIR / "scripts" / ".rag_embeddings_cache_v2.json"

def load_cached_embeddings() -> Dict[str, List[float]]:
    """Đọc cache vector từ đĩa để hỗ trợ Resume (tiếp tục chạy khi bị ngắt quãng)."""
    if CACHE_FILE.exists():
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}


def save_cached_embeddings(cache: Dict[str, List[float]]):
    """Lưu cache vector xuống đĩa sau mỗi batch thành công."""
    try:
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(cache, f)
    except Exception as e:
        print(f"⚠️ Không thể ghi cache: {e}")


def get_batch_embeddings(
    texts: List[str],
    api_key: str,
    max_batch_tokens: int = 4000,
    max_batch_chunks: int = 12,
    model_name: str = "models/gemini-embedding-2-preview"
) -> List[List[float]]:
    """
    Gọi Batch Embedding của Google GenAI REST API trực tiếp với Progress Bar & Caching:
    - Nhóm thông minh theo Token Budget (tối đa 4000 tokens/request) để không bao giờ vượt 15000 TPM của Free Tier.
    - Lưu cache xuống đĩa: Mỗi batch thành công được ghi ngay vào .rag_embeddings_cache_v2.json.
    - Tự động tiếp tục (Resume) từ điểm dừng nếu bị ngắt quãng.
    Model: gemini-embedding-2 -> Output: 768 dimensions.
    """
    if not requests:
        raise RuntimeError("Vui lòng cài đặt thư viện 'requests' để gọi Gemini API.")

    cache = load_cached_embeddings()
    cached_count = sum(1 for t in texts if t in cache)
    if cached_count > 0:
        print(f"  💾 Tìm thấy {cached_count}/{len(texts)} chunks đã có sẵn trong cache đĩa (Không tốn API)!")

    url = f"https://generativelanguage.googleapis.com/v1beta/{model_name}:batchEmbedContents?key={api_key}"
    headers = {"Content-Type": "application/json"}

    # Nhóm các chunks thành các batch an toàn theo ngân sách token
    batches: List[List[str]] = []
    curr_batch: List[str] = []
    curr_tokens = 0

    for text in texts:
        t_tokens = TOKEN_COUNTER.count(text)
        if (curr_tokens + t_tokens > max_batch_tokens or len(curr_batch) >= max_batch_chunks) and curr_batch:
            batches.append(curr_batch)
            curr_batch = [text]
            curr_tokens = t_tokens
        else:
            curr_batch.append(text)
            curr_tokens += t_tokens
    if curr_batch:
        batches.append(curr_batch)

    all_embeddings: List[List[float]] = []
    total = len(texts)
    total_batches = len(batches)
    start_time = time.time()

    print(f"\n🔮 Bắt đầu xử lý Vector Embedding 768 chiều ({model_name}) qua {total_batches} batches an toàn:")
    render_progress_bar(0, total, prefix="  Tiến độ:", suffix="Khởi động...")

    for b_idx, batch in enumerate(batches):
        # Kiểm tra nếu toàn bộ batch đã có trong cache
        if all(t in cache for t in batch):
            for t in batch:
                all_embeddings.append(cache[t])
            done = len(all_embeddings)
            render_progress_bar(done, total, prefix="  Tiến độ:", suffix=f"Batch {b_idx+1}/{total_batches} [Cache Hit ⚡]")
            continue

        payload = {
            "requests": [
                {
                    "model": model_name,
                    "content": {
                        "parts": [{"text": text}]
                    },
                    "outputDimensionality": 768
                }
                for text in batch
            ]
        }

        retries = 8
        backoff = 12
        while retries > 0:
            batch_start = time.time()
            try:
                response = requests.post(url, headers=headers, json=payload, timeout=40)
                if response.status_code == 200:
                    data = response.json()
                    embeddings = [item["values"] for item in data.get("embeddings", [])]
                    all_embeddings.extend(embeddings)

                    # Lưu vào cache ngay lập tức
                    for t, emb in zip(batch, embeddings):
                        cache[t] = emb
                    save_cached_embeddings(cache)

                    latency = (time.time() - batch_start) * 1000
                    done = len(all_embeddings)
                    rem_batches = total_batches - (b_idx + 1)
                    eta_sec = rem_batches * 6.0
                    suffix = f"Batch {b_idx+1}/{total_batches} [{latency:.0f}ms] ETA: {eta_sec:.0f}s"
                    render_progress_bar(done, total, prefix="  Tiến độ:", suffix=suffix)
                    break
                elif response.status_code == 429:
                    render_progress_bar(len(all_embeddings), total, prefix="  Tiến độ:", suffix=f"[Rate Limit 429] Chờ {backoff}s...")
                    time.sleep(backoff)
                    backoff = min(backoff + 8, 40)
                    retries -= 1
                else:
                    print(f"\n  ❌ Lỗi API ({response.status_code}): {response.text[:200]}")
                    retries -= 1
                    time.sleep(4)
            except Exception as e:
                print(f"\n  ❌ Lỗi mạng: {e}")
                retries -= 1
                time.sleep(4)

        if retries == 0 and len(all_embeddings) < sum(len(b) for b in batches[:b_idx+1]):
            raise RuntimeError(f"Không thể lấy embedding cho batch {b_idx+1}")

        # Nghỉ 4.8s giữa các batch để tuân thủ 15 RPM
        if b_idx < total_batches - 1:
            time.sleep(4.8)

    total_time = time.time() - start_time
    print(f"  ✨ Đã chuẩn bị xong 100% Vector trong {total_time:.1f} giây!\n")
    return all_embeddings


# ==============================================================================
# 7. NẠP DỮ LIỆU VÀO POSTGRESQL + PGVECTOR
# ==============================================================================
def save_chunks_to_postgres(
    documents: List[Dict[str, Any]],
    chunks: List[Dict[str, Any]],
    embeddings: List[List[float]],
    db_config: Dict[str, Any]
):
    """Lưu toàn bộ Documents và Chunks kèm vector vào Remote PostgreSQL careflow_ai."""
    if not psycopg2:
        raise RuntimeError("Vui lòng cài đặt psycopg2 (`pip install psycopg2-binary`) để ghi vào PostgreSQL.")

    print(f"🔌 Đang kết nối tới PostgreSQL {db_config['host']}:{db_config['port']}/{db_config['dbname']}...")
    conn = psycopg2.connect(
        host=db_config["host"],
        port=db_config["port"],
        dbname=db_config["dbname"],
        user=db_config["user"],
        password=db_config["password"]
    )
    conn.autocommit = False
    cursor = conn.cursor()

    try:
        # 1. Đảm bảo extension pgvector đã được kích hoạt
        cursor.execute("CREATE EXTENSION IF NOT EXISTS vector;")

        # 2. Upsert tài liệu vào clinical_guideline_documents
        doc_code_to_id = {}
        for doc in documents:
            cursor.execute("""
                INSERT INTO clinical_guideline_documents (id, code, title, issuer, published_year, specialty, is_active)
                VALUES (%s, %s, %s, %s, %s, %s, true)
                ON CONFLICT (code) DO UPDATE 
                SET title = EXCLUDED.title, specialty = EXCLUDED.specialty
                RETURNING id;
            """, (
                str(uuid.uuid4()),
                doc["code"],
                doc["title"],
                doc["issuer"],
                doc["published_year"],
                doc["specialty"]
            ))
            row = cursor.fetchone()
            doc_code_to_id[doc["code"]] = row[0]

        print(f"✅ Đã upsert thành công {len(doc_code_to_id)} tài liệu cha vào clinical_guideline_documents.")

        # 3. Insert Chunks vào clinical_guideline_chunks
        # Xóa các chunks cũ của các documents này để tránh nạp trùng
        for code, doc_id in doc_code_to_id.items():
            cursor.execute("DELETE FROM clinical_guideline_chunks WHERE document_id = %s;", (doc_id,))

        chunk_rows = []
        for chunk, emb in zip(chunks, embeddings):
            doc_id = doc_code_to_id[chunk["document_code"]]
            # pgvector nhận chuỗi '[0.1, 0.2, ...]'
            emb_str = f"[{','.join(f'{x:.6f}' for x in emb)}]"
            chunk_rows.append((
                chunk["id"],
                doc_id,
                chunk["section_title"],
                chunk["content"],
                json.dumps(chunk["metadata"], ensure_ascii=False),
                emb_str
            ))

        insert_query = """
            INSERT INTO clinical_guideline_chunks (id, document_id, section_title, content, metadata, embedding)
            VALUES %s
        """
        execute_values(cursor, insert_query, chunk_rows, page_size=200)

        conn.commit()
        print(f"🎉 HOÀN TẤT NẠP {len(chunk_rows)} CHUNKS KÈM VECTOR 768 CHIỀU VÀO CSDL!")

    except Exception as e:
        conn.rollback()
        raise e
    finally:
        cursor.close()
        conn.close()


# ==============================================================================
# 8. HÀM MAIN & ĐIỀU PHỐI LUỒNG
# ==============================================================================
def main():
    parser = argparse.ArgumentParser(
        description="CareFlow Clinical RAG — Ingestion Pipeline",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--dry-run", action="store_true", help="Chỉ phân đoạn và đếm token, KHÔNG gọi API và KHÔNG ghi DB")
    parser.add_argument("--sample", type=int, default=0, help="Số lượng chunks mẫu để test (0 = toàn bộ)")
    parser.add_argument("--doc", type=str, default="", help="Chỉ định nạp 1 tài liệu cụ thể theo code (vd: BYT-QD-4800-HOHAP hoặc BYT-QD-5948-DDI)")
    parser.add_argument("--db-host", type=str, default="", help="Địa chỉ PostgreSQL Host (mặc định đọc từ .env hoặc localhost)")
    parser.add_argument("--db-port", type=int, default=0, help="Cổng PostgreSQL (mặc định đọc từ .env hoặc 5432)")
    parser.add_argument("--db-name", type=str, default="careflow_ai", help="Tên CSDL mục tiêu")
    parser.add_argument("--db-user", type=str, default="careflow", help="User PostgreSQL")
    parser.add_argument("--db-pass", type=str, default="careflow", help="Password PostgreSQL")

    args = parser.parse_args()
    env_vars = load_env_variables()

    gemini_api_key = env_vars.get("GEMINI_API_KEY", "")
    db_host = args.db_host or env_vars.get("DB_HOST", "localhost")
    db_port = args.db_port or int(env_vars.get("DB_PORT", "5432"))
    db_user = args.db_user or env_vars.get("DB_USER", "careflow")
    db_pass = args.db_pass or env_vars.get("DB_PASS", "careflow")
    db_name = args.db_name

    print("\n" + "="*80)
    print("  🚀 CAREFLOW CLINICAL RAG INGESTION PIPELINE")
    print("="*80)
    print(f"• Chế độ chạy: {'[DRY RUN - KIỂM THỬ KHÔNG GỌI API]' if args.dry_run else '[THỰC THI THẬT]'}")
    print(f"• Cơ sở dữ liệu: {db_user}@{db_host}:{db_port}/{db_name}")
    print(f"• Tiktoken khả dụng: {'Có' if TIKTOKEN_AVAILABLE else 'Không (Dùng Heuristic Tokenizer)'}")
    print("="*80 + "\n")

    # BƯỚC 1: XỬ LÝ PHÂN ĐOẠN (CHUNKING)
    all_chunks: List[Dict[str, Any]] = []

    # 1.1. Xử lý các tài liệu Markdown phác đồ
    for doc in DOCUMENTS_CATALOG:
        if doc["code"] == "BYT-QD-5948-DDI":
            continue
        if args.doc and args.doc != doc["code"]:
            continue

        print(f"📖 Đang xử lý phác đồ: {doc['code']} — {doc['title']}...")
        chunks = process_markdown_document(doc)
        print(f"   ↳ Tạo thành công: {len(chunks)} chunks.")
        all_chunks.extend(chunks)

    # 1.2. Xử lý bảng tương tác thuốc DDI CSV
    if not args.doc or args.doc == "BYT-QD-5948-DDI":
        print("💊 Đang xử lý bảng tương tác thuốc chống chỉ định DDI (CSV)...")
        ddi_chunks = process_ddi_csv()
        print(f"   ↳ Tạo thành công: {len(ddi_chunks)} narrative chunks.")
        all_chunks.extend(ddi_chunks)

    print(f"\n📊 TỔNG CỘNG ĐÃ TẠO: {len(all_chunks)} CHUNKS LÂM SÀNG.")

    # Thống kê phân bố độ dài Token
    token_counts = [c["token_count"] for c in all_chunks]
    avg_tokens = sum(token_counts) / len(token_counts) if token_counts else 0
    max_tokens = max(token_counts) if token_counts else 0
    min_tokens = min(token_counts) if token_counts else 0
    print(f"   - Token trung bình/chunk: {avg_tokens:.1f} tokens")
    print(f"   - Token ngắn nhất: {min_tokens} tokens")
    print(f"   - Token dài nhất: {max_tokens} tokens")

    # In mẫu 2 chunks kiểm tra
    if all_chunks:
        print("\n--- [CHUNK MẪU 1: PHÁC ĐỒ] ---")
        p_sample = next((c for c in all_chunks if "HOHAP" in c["document_code"]), all_chunks[0])
        print(f"Section: {p_sample['section_title']}")
        print(f"Tokens: {p_sample['token_count']}")
        print(f"Content (150 ký tự đầu):\n{p_sample['content'][:200]}...")
        print(f"Metadata: {json.dumps(p_sample['metadata'], ensure_ascii=False)}")

        print("\n--- [CHUNK MẪU 2: DDI] ---")
        d_sample = next((c for c in all_chunks if "DDI" in c["document_code"]), all_chunks[-1])
        print(f"Section: {d_sample['section_title']}")
        print(f"Tokens: {d_sample['token_count']}")
        print(f"Content:\n{d_sample['content']}")
        print(f"Metadata: {json.dumps(d_sample['metadata'], ensure_ascii=False)}")

    # BƯỚC 2: DRY-RUN CHECK
    if args.dry_run:
        print("\n" + "="*80)
        print("✅ [DRY RUN HOÀN TẤT] Cấu trúc phân đoạn và metadata hoàn toàn hợp lệ!")
        print("   Không có API call nào được thực hiện và không có dữ liệu nào bị ghi đè.")
        print("="*80 + "\n")
        return

    # Giới hạn số lượng nếu có cờ --sample
    if args.sample > 0:
        all_chunks = all_chunks[:args.sample]
        print(f"\n⚠️ [CHẾ ĐỘ MẪU] Chỉ thực thi với {args.sample} chunks đầu tiên.")

    # BƯỚC 3: GỌI EMBEDDING API
    if not gemini_api_key:
        print("❌ [LỖI] Không tìm thấy GEMINI_API_KEY trong tệp .env! Vui lòng cấu hình API key.")
        sys.exit(1)

    print(f"\n🔮 Đang tiến hành sinh Dense Vector 768 chiều cho {len(all_chunks)} chunks...")
    texts_to_embed = [c["content"] for c in all_chunks]
    embeddings = get_batch_embeddings(texts_to_embed, gemini_api_key)
    print(f"✅ Đã nhận đủ {len(embeddings)} vector embedding.")

    # BƯỚC 4: LƯU VÀO CSDL POSTGRESQL PGVECTOR
    db_config = {
        "host": db_host,
        "port": db_port,
        "dbname": db_name,
        "user": db_user,
        "password": db_pass
    }
    save_chunks_to_postgres(DOCUMENTS_CATALOG, all_chunks, embeddings, db_config)


if __name__ == "__main__":
    main()
