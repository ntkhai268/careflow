#!/usr/bin/env python3
"""
Normalize Treatment Protocol Markdown Files for CareFlow Clinical RAG.
Converts administrative/legal document numbering to standard GitHub Flavored Markdown headings (#, ##, ###, ####),
cleans up standalone page numbers, and standardizes breadcrumb navigation.
"""

import re
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
PROTOCOL_DIR = BASE_DIR / "docs" / "rag-docs" / "treatment-protocol" / "markdown"


def normalize_tang_huyet_ap():
    file_path = PROTOCOL_DIR / "tang_huyet_ap.md"
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()

    lines = text.split("\n")
    out = []
    
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Handle header at the top
        if i == 0:
            out.append("# HƯỚNG DẪN CHẨN ĐOÁN VÀ ĐIỀU TRỊ TĂNG HUYẾT ÁP\n")
            if "HƯỚNG DẪN" in stripped:
                if i + 1 < len(lines) and "CHẨN ĐOÁN VÀ ĐIỀU TRỊ TĂNG HUYẾT ÁP" in lines[i+1]:
                    i += 2
                    continue
                i += 1
                continue

        # Level 1 Main Sections: 1. ĐỊNH NGHĨA, 2. NGUYÊN NHÂN, 3. CHẨN ĐOÁN, 4. ĐIỀU TRỊ, 5. TIẾN TRIỂN, 6. PHÒNG BỆNH
        if re.match(r"^[1-6]\.\s+[A-ZÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ\s]+$", stripped):
            out.append(f"\n## {stripped}\n")
            i += 1
            continue

        # Appendices: PHỤ LỤC 1, PHỤ LỤC 2...
        if re.match(r"^PHỤ LỤC\s+\d+", stripped):
            app_title = stripped
            if i + 1 < len(lines) and lines[i+1].strip() and not lines[i+1].strip().startswith("(") and not lines[i+1].strip().startswith("|"):
                app_title += f": {lines[i+1].strip()}"
                i += 1
                while i + 1 < len(lines) and lines[i+1].strip() and not lines[i+1].strip().startswith("(") and not lines[i+1].strip().startswith("|") and not re.match(r"^[0-9]+\.", lines[i+1].strip()):
                    app_title += f" {lines[i+1].strip()}"
                    i += 1
            out.append(f"\n## {app_title}\n")
            i += 1
            continue

        # Level 2 Subsections: 3.1. Chẩn đoán xác định THA: ...
        sub_match = re.match(r"^([1-6]\.[1-9])\.\s+([^:]+):(.*)$", stripped)
        if sub_match:
            code = sub_match.group(1)
            title = sub_match.group(2).strip()
            rest = sub_match.group(3).strip()
            out.append(f"\n### {code}. {title}\n")
            if rest:
                out.append(rest)
            i += 1
            continue

        # Level 2 Subsections without colon: 3.1. ...
        sub_match2 = re.match(r"^([1-6]\.[1-9])\.\s+(.+)$", stripped)
        if sub_match2:
            code = sub_match2.group(1)
            title = sub_match2.group(2).strip()
            out.append(f"\n### {code}. {title}\n")
            i += 1
            continue

        out.append(line)
        i += 1

    normalized_text = "\n".join(out)
    normalized_text = re.sub(r"\n{3,}", "\n\n", normalized_text).strip() + "\n"
    
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(normalized_text)
    print("✓ Normalized tang_huyet_ap.md")


def normalize_dai_thao_duong():
    file_path = PROTOCOL_DIR / "dai_thao_duong_tip2.md"
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()

    lines = text.split("\n")
    out = []
    
    in_main_content = False

    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Handle header at the top
        if i == 0:
            out.append("# HƯỚNG DẪN CHẨN ĐOÁN VÀ ĐIỀU TRỊ ĐÁI THÁO ĐƯỜNG TÍP 2\n")

        # Remove standalone page numbers
        if re.match(r"^[0-9]{1,3}$", stripped):
            i += 1
            continue

        # Check if we reached the end of front matter (after abbreviations "YTNC Yếu tố nguy cơ")
        if not in_main_content:
            if "YTNC Yếu tố nguy cơ" in stripped:
                in_main_content = True
            out.append(line)
            i += 1
            continue

        # Skip converting any TOC-like lines that have dot leaders "..." or "…"
        if "..." in stripped or "…" in stripped:
            out.append(line)
            i += 1
            continue

        # Major Section: PHẦN X: ... or PHẦN X
        phan_match = re.match(r"^PHẦN\s+(\d+)\s*(:|\.)?\s*(.*)$", stripped)
        if phan_match:
            p_num = phan_match.group(1)
            p_title = phan_match.group(3).strip()
            if not p_title and i + 1 < len(lines) and lines[i+1].strip() and not lines[i+1].strip().startswith("(") and not re.match(r"^[0-9]+\.", lines[i+1].strip()):
                p_title = lines[i+1].strip()
                i += 1
            out.append(f"\n## PHẦN {p_num}: {p_title}\n")
            i += 1
            continue

        # Major Appendices: PHỤ LỤC XX: ...
        pl_match = re.match(r"^PHỤ LỤC\s+(\d+)\s*(:|\.)?\s*(.*)$", stripped)
        if pl_match:
            pl_num = pl_match.group(1)
            pl_title = pl_match.group(3).strip()
            if not pl_title and i + 1 < len(lines) and lines[i+1].strip() and not lines[i+1].strip().startswith("(") and not re.match(r"^[0-9]+\.", lines[i+1].strip()):
                pl_title = lines[i+1].strip()
                i += 1
            out.append(f"\n## PHỤ LỤC {pl_num}: {pl_title}\n")
            i += 1
            continue

        # Subsections in main content: e.g. 1. Chẩn đoán, 2. Phân loại đái tháo đường...
        sub1_match = re.match(r"^([1-9][0-9]?)\.\s+([A-ZÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ][^.!?]{2,80})$", stripped)
        if sub1_match and not stripped.endswith("."):
            s_num = sub1_match.group(1)
            s_title = sub1_match.group(2).strip()
            out.append(f"\n### {s_num}. {s_title}\n")
            i += 1
            continue

        # Sub-subsections: e.g. 1.1. Chẩn đoán đái tháo đường, 4.2. Lựa chọn cụ thể...
        sub2_match = re.match(r"^([1-9][0-9]?\.\s*[1-9][0-9]?)\.\s+([A-ZÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ][^:]{2,80})(:)?(.*)$", stripped)
        if sub2_match:
            s_code = sub2_match.group(1).replace(" ", "")
            s_title = sub2_match.group(2).strip()
            rest = sub2_match.group(4).strip()
            out.append(f"\n#### {s_code}. {s_title}\n")
            if rest:
                out.append(rest)
            i += 1
            continue

        # Sub-sub-subsections: e.g. 1.1.1. ...
        sub3_match = re.match(r"^([1-9][0-9]?\.[1-9][0-9]?\.[1-9][0-9]?)\.\s+([A-ZÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ].*)$", stripped)
        if sub3_match:
            s_code = sub3_match.group(1)
            s_title = sub3_match.group(2).strip()
            out.append(f"\n##### {s_code}. {s_title}\n")
            i += 1
            continue

        out.append(line)
        i += 1

    normalized_text = "\n".join(out)
    normalized_text = re.sub(r"\n{3,}", "\n\n", normalized_text).strip() + "\n"

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(normalized_text)
    print("✓ Normalized dai_thao_duong_tip2.md")


def normalize_viem_phoi_cong_dong():
    file_path = PROTOCOL_DIR / "viem_phoi_cong_dong.md"
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()

    lines = text.split("\n")
    out = []
    
    in_main_content = False

    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Handle header at the top
        if i == 0:
            out.append("# HƯỚNG DẪN CHẨN ĐOÁN VÀ ĐIỀU TRỊ VIÊM PHỔI MẮC PHẢI CỘNG ĐỒNG Ở NGƯỜI LỚN\n")
            if "HƯỚNG DẪN" in stripped:
                i += 1
                continue

        # Remove standalone page numbers
        if re.match(r"^[0-9]{1,3}$", stripped):
            i += 1
            continue

        # Check if we passed the abbreviations section
        if not in_main_content:
            if "VPMPCĐ Viêm phổi mắc phải cộng đồng" in stripped:
                in_main_content = True
            out.append(line)
            i += 1
            continue

        # Skip converting any TOC-like lines that have dot leaders "..." or "…"
        if "..." in stripped or "…" in stripped:
            out.append(line)
            i += 1
            continue

        # Major Chapters: CHƯƠNG X. ...
        ch_match = re.match(r"^CHƯƠNG\s+(\d+)\.\s*(.*)$", stripped)
        if ch_match:
            ch_num = ch_match.group(1)
            ch_title = ch_match.group(2).strip()
            if not ch_title or len(ch_title) < 5:
                if i + 1 < len(lines) and lines[i+1].strip() and not re.match(r"^[0-9]+\.", lines[i+1].strip()):
                    ch_title += " " + lines[i+1].strip()
                    i += 1
            out.append(f"\n## CHƯƠNG {ch_num}. {ch_title}\n")
            i += 1
            continue

        # Major Appendices: PHỤ LỤC X. ...
        pl_match = re.match(r"^PHỤ LỤC\s+(\d+)\.\s*(.*)$", stripped)
        if pl_match:
            pl_num = pl_match.group(1)
            pl_title = pl_match.group(2).strip()
            if not pl_title or len(pl_title) < 5:
                if i + 1 < len(lines) and lines[i+1].strip() and not re.match(r"^[0-9]+\.", lines[i+1].strip()):
                    pl_title += " " + lines[i+1].strip()
                    i += 1
            out.append(f"\n## PHỤ LỤC {pl_num}. {pl_title}\n")
            i += 1
            continue

        # Subsections: 1.1. Định nghĩa, 1.2. Dịch tễ học...
        sub1_match = re.match(r"^([1-9][0-9]?\.[1-9][0-9]?)\.\s+([A-ZÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ][^:]{2,100})(:)?(.*)$", stripped)
        if sub1_match:
            s_code = sub1_match.group(1)
            s_title = sub1_match.group(2).strip()
            rest = sub1_match.group(4).strip()
            out.append(f"\n### {s_code}. {s_title}\n")
            if rest:
                out.append(rest)
            i += 1
            continue

        # Sub-subsections: 1.3.1. Nguyên nhân thường gặp...
        sub2_match = re.match(r"^([1-9][0-9]?\.[1-9][0-9]?\.[1-9][0-9]?)\.\s+([A-ZÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ].*)$", stripped)
        if sub2_match:
            s_code = sub2_match.group(1)
            s_title = sub2_match.group(2).strip()
            out.append(f"\n#### {s_code}. {s_title}\n")
            i += 1
            continue

        out.append(line)
        i += 1

    normalized_text = "\n".join(out)
    normalized_text = re.sub(r"\n{3,}", "\n\n", normalized_text).strip() + "\n"

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(normalized_text)
    print("✓ Normalized viem_phoi_cong_dong.md")


if __name__ == "__main__":
    print("Starting markdown protocol normalization...")
    normalize_tang_huyet_ap()
    normalize_dai_thao_duong()
    normalize_viem_phoi_cong_dong()
    print("Done!")
