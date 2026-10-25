import pdfplumber
import os
import re

def clean_text(t):
    if not t:
        return ""
    t = t.replace("(cid:1233)", "ọ")
    t = re.sub(r'[ \t]+', ' ', t)
    return t.strip()

def extract_clean_vpmpcd():
    pdf_path = "docs/rag-docs/treatment-protocol/Huong_dan_CD_v__DT_VPMPCD_singed_5fa4c.pdf"
    out_md = "docs/rag-docs/treatment-protocol/markdown/viem_phoi_cong_dong.md"
    
    print(f"🚀 Đang tái tạo sạch viem_phoi_cong_dong.md (loại bỏ chữ ký số Times-Roman)...")
    
    sections = []
    
    with pdfplumber.open(pdf_path) as pdf:
        total = len(pdf.pages)
        for i, page in enumerate(pdf.pages):
            # Lọc bỏ font chữ ký số 'Times-Roman'
            filtered_p = page.filter(lambda obj: obj.get("fontname") != "Times-Roman")
            
            # Kiểm tra xem trang có bảng không
            tables = filtered_p.extract_tables()
            
            if not tables:
                text = filtered_p.extract_text()
                if text:
                    sections.append(clean_text(text))
            else:
                # Nếu trang có bảng, trích xuất text ngoài bảng và format bảng sạch
                # Cách tiếp cận an toàn: trích xuất toàn bộ text của trang và format bảng
                text = filtered_p.extract_text()
                if text:
                    sections.append(clean_text(text))
                
                for t_idx, table in enumerate(tables):
                    if not table or len(table) < 2:
                        continue
                    md_table = []
                    # Header
                    header = [clean_text(c).replace("\n", " ").replace("|", "\\|") for c in table[0]]
                    if not any(header):
                        continue
                    md_table.append("| " + " | ".join(header) + " |")
                    md_table.append("| " + " | ".join([":---"] * len(header)) + " |")
                    
                    for row in table[1:]:
                        r = [clean_text(c).replace("\n", " ").replace("|", "\\|") for c in row]
                        if any(r):
                            md_table.append("| " + " | ".join(r) + " |")
                    
                    if len(md_table) > 2:
                        sections.append("\n\n" + "\n".join(md_table) + "\n\n")

    full_content = "\n\n".join(sections)
    
    # Dọn dẹp khoảng trắng dư thừa
    full_content = re.sub(r'\n{3,}', '\n\n', full_content)
    
    with open(out_md, "w", encoding="utf-8") as f:
        f.write("# HƯỚNG DẪN CHẨN ĐOÁN VÀ ĐIỀU TRỊ VIÊM PHỔI MẮC PHẢI CỘNG ĐỒNG Ở NGƯỜI LỚN\n\n")
        f.write("> **Căn cứ pháp lý**: Quyết định số 4800/QĐ-BYT của Bộ trưởng Bộ Y tế\n\n")
        f.write("---\n\n")
        f.write(full_content)
        
    size_kb = os.path.getsize(out_md) / 1024
    print(f"✅ Hoàn tất viem_phoi_cong_dong.md: {size_kb:.1f} KB (Đã loại bỏ 100% watermark)")

def clean_drugs_generic_csv():
    raw_csv = "docs/rag-docs/drug-formulary/drugs_generic.csv"
    print(f"🚀 Đang làm sạch file {raw_csv}...")
    import csv
    
    with open(raw_csv, "r", encoding="utf-8") as f:
        rows = list(csv.reader(f))
        
    cleaned_rows = []
    # Header chuẩn
    header = ["stt", "category", "bid_code", "active_ingredient", "concentration", "route", "unit", "group", "dosage_form"]
    
    for r in rows:
        if not r or len(r) < 4:
            continue
        # Bỏ qua các dòng tiêu đề hành chính
        if "Công văn" in r[0] or "STT" in r[0] or "(1)" in r[0]:
            continue
        stt = r[0].strip()
        if not stt.isdigit():
            continue
            
        category = r[1].strip() if len(r) > 1 else ""
        bid_code = r[2].strip() if len(r) > 2 else ""
        active_ing = r[3].strip() if len(r) > 3 else ""
        conc = r[4].strip() if len(r) > 4 else ""
        route = r[5].strip() if len(r) > 5 else ""
        unit = r[6].strip() if len(r) > 6 else ""
        grp = r[7].strip() if len(r) > 7 else ""
        form = r[8].strip() if len(r) > 8 else ""
        
        cleaned_rows.append({
            "stt": stt,
            "category": category,
            "bid_code": bid_code,
            "active_ingredient": active_ing,
            "concentration": conc,
            "route": route,
            "unit": unit,
            "group": grp,
            "dosage_form": form
        })
        
    with open(raw_csv, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=header)
        writer.writeheader()
        writer.writerows(cleaned_rows)
        
    print(f"✅ Đã chuẩn hóa {len(cleaned_rows)} dòng dữ liệu thuốc trong {raw_csv}")

if __name__ == "__main__":
    extract_clean_vpmpcd()
    clean_drugs_generic_csv()
