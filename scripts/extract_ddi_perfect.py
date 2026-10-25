import os
import csv
import re
import pdfplumber

def clean_text(text):
    if not text:
        return ""
    # Sửa lỗi mã hoá font CID trong PDF BYT
    t = text.replace("(cid:1233)", "ọ")
    # Chuẩn hoá khoảng trắng
    t = re.sub(r'[ \t]+', ' ', t)
    return t.strip()

def process_table_rows(tables, table_type, start_stt=1):
    records = []
    current_record = None

    for table in tables:
        for row in table:
            if not row or len(row) < 6:
                continue

            cell_0 = clean_text(row[0])
            # Bỏ qua dòng tiêu đề
            if "STT" in cell_0:
                continue

            c0 = cell_0
            c1 = clean_text(row[1])
            c2 = clean_text(row[2])
            c3 = clean_text(row[3])
            c4 = clean_text(row[4])
            c5 = clean_text(row[5])

            # Kiểm tra xem đây là dòng mới (có STT là số) hay dòng nối tiếp từ trang trước
            if c0.isdigit():
                if current_record:
                    records.append(current_record)
                current_record = {
                    "table_type": table_type,
                    "stt": int(c0),
                    "drug_1": c1.replace("\n", " "),
                    "drug_2": c2.replace("\n", " "),
                    "mechanism": c3.replace("\n", " "),
                    "consequence": c4.replace("\n", " "),
                    "management": c5.replace("\n", " ")
                }
            elif current_record:
                # Nối tiếp dữ liệu vào record hiện tại
                if c1: current_record["drug_1"] += " " + c1.replace("\n", " ")
                if c2: current_record["drug_2"] += " " + c2.replace("\n", " ")
                if c3: current_record["mechanism"] += " " + c3.replace("\n", " ")
                if c4: current_record["consequence"] += " " + c4.replace("\n", " ")
                if c5: current_record["management"] += " " + c5.replace("\n", " ")

    if current_record:
        records.append(current_record)

    # Làm sạch khoảng trắng dư thừa và sửa lỗi wrap chữ trong tên thuốc (do ngắt dòng PDF)
    drug_splits = {
        "Acenocoumar ol": "Acenocoumarol",
        "Candesarta n": "Candesartan",
        "Carbamazepi n": "Carbamazepin",
        "Ciprofloxaci n": "Ciprofloxacin",
        "Clarithromyci n": "Clarithromycin",
        "Clormadino n": "Clormadinon",
        "Clorpromaz in": "Clorpromazin",
        "Dexlansoprazo l": "Dexlansoprazol",
        "Erythromyc in": "Erythromycin",
        "Erythromyci n": "Erythromycin",
        "Escitalopra m": "Escitalopram",
        "Glibenclami d": "Glibenclamid",
        "Levofloxaci n": "Levofloxacin",
        "Methylphenid at": "Methylphenidat",
        "Metoclopram id": "Metoclopramid",
        "Moxifloxaci n": "Moxifloxacin",
        "Norethindro n": "Norethindron",
        "Ondansetro n": "Ondansetron",
        "Oxcarbamazep in": "Oxcarbamazepin",
        "Phenobarbita l": "Phenobarbital",
        "Pseudoephedri n": "Pseudoephedrin",
        "Roxithromyc in": "Roxithromycin",
        "Sparfloxaci n": "Sparfloxacin",
        "Sumatripta n": "Sumatriptan",
        "Trandolapri l": "Trandolapril"
    }

    for r in records:
        for k in ["drug_1", "drug_2", "mechanism", "consequence", "management"]:
            val = re.sub(r'\s+', ' ', r[k]).strip()
            for broken, fixed in drug_splits.items():
                val = val.replace(broken, fixed)
            r[k] = val

    return records

def extract_intro_text(pdf):
    intro_lines = []
    # Lấy văn bản từ trang 1 đến trang 10
    for page_num in range(1, 11):
        text = pdf.pages[page_num - 1].extract_text()
        if text:
            cleaned = clean_text(text)
            intro_lines.append(f"<!-- Page {page_num} -->\n" + cleaned)
    return "\n\n".join(intro_lines)

def main():
    pdf_path = "docs/rag-docs/ddi/Quyết-định-5948-QĐ-BYT.pdf"
    output_dir = "docs/rag-docs/ddi"
    md_output_dir = "docs/rag-docs/ddi/markdown"
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(md_output_dir, exist_ok=True)

    csv_path = os.path.join(output_dir, "ddi_contraindicated.csv")
    md_path = os.path.join(md_output_dir, "tuong_tac_thuoc_chong_chi_dinh_5948.md")

    print(f"🚀 Bắt đầu trích xuất chuẩn xác DDI từ: {pdf_path}")

    with pdfplumber.open(pdf_path) as pdf:
        total_pages = len(pdf.pages)
        print(f"📄 Tổng số trang PDF: {total_pages}")

        # 1. Thu thập văn bản phần đầu (Trang 1 -> 10)
        print("⏳ Đang đọc phần văn bản quyết định và hướng dẫn (Trang 1 - 10)...")
        intro_text = extract_intro_text(pdf)

        # 2. Thu thập Bảng 3.1: Từng hoạt chất (Trang 11 -> 155)
        print("⏳ Đang trích xuất BẢNG 3.1 (Tương tác theo từng hoạt chất - Trang 11 đến 155)...")
        tables_3_1 = []
        for p in range(11, 156):
            tbls = pdf.pages[p - 1].extract_tables()
            tables_3_1.extend(tbls)
        records_3_1 = process_table_rows(tables_3_1, "Bảng 3.1 - Theo từng hoạt chất")
        print(f"✅ Bảng 3.1: Trích xuất thành công {len(records_3_1)} cặp tương tác thuốc.")

        # 3. Thu thập Bảng 3.2: Theo nhóm đặc tính dược lý (Trang 156 -> 176)
        print("⏳ Đang trích xuất BẢNG 3.2 (Tương tác theo nhóm đặc tính dược lý - Trang 156 đến 176)...")
        tables_3_2 = []
        for p in range(156, total_pages + 1):
            tbls = pdf.pages[p - 1].extract_tables()
            tables_3_2.extend(tbls)
        records_3_2 = process_table_rows(tables_3_2, "Bảng 3.2 - Theo nhóm đặc tính dược lý")
        print(f"✅ Bảng 3.2: Trích xuất thành công {len(records_3_2)} nhóm tương tác thuốc.")

    all_records = records_3_1 + records_3_2
    print(f"🎉 Tổng cộng: {len(all_records)} tương tác chống chỉ định.")

    # 4. Lưu ra CSV
    print(f"💾 Đang lưu vào CSV: {csv_path}...")
    with open(csv_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["table_type", "stt", "drug_1", "drug_2", "mechanism", "consequence", "management"])
        writer.writeheader()
        writer.writerows(all_records)
    print(f"✅ Đã ghi {len(all_records)} dòng vào {csv_path}")

    # 5. Lưu ra Markdown chuẩn định dạng GFM (GitHub Flavored Markdown)
    print(f"💾 Đang tạo file Markdown chuẩn: {md_path}...")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("# DANH MỤC TƯƠNG TÁC THUỐC CHỐNG CHỈ ĐỊNH TRONG THỰC HÀNH LÂM SÀNG\n\n")
        f.write("> **Căn cứ pháp lý**: Quyết định số 5948/QĐ-BYT của Bộ trưởng Bộ Y tế\n\n")
        f.write("---\n\n")
        f.write("## PHẦN I: VĂN BẢN QUYẾT ĐỊNH & HƯỚNG DẪN SỬ DỤNG\n\n")
        f.write(intro_text)
        f.write("\n\n---\n\n")

        # Bảng 3.1
        f.write("## PHẦN II: BẢNG 3.1. DANH MỤC TƯƠNG TÁC THUỐC THEO TỪNG HOẠT CHẤT\n\n")
        f.write("| STT | Hoạt chất 1 | Hoạt chất 2 | Cơ chế | Hậu quả | Xử trí |\n")
        f.write("| :---: | :--- | :--- | :--- | :--- | :--- |\n")
        for r in records_3_1:
            # Escape pipes if any
            m = r['mechanism'].replace('|', '\\|')
            c = r['consequence'].replace('|', '\\|')
            x = r['management'].replace('|', '\\|')
            d1 = r['drug_1'].replace('|', '\\|')
            d2 = r['drug_2'].replace('|', '\\|')
            f.write(f"| {r['stt']} | **{d1}** | **{d2}** | {m} | {c} | {x} |\n")

        f.write("\n\n---\n\n")

        # Bảng 3.2
        f.write("## PHẦN III: BẢNG 3.2. DANH MỤC TƯƠNG TÁC THUỐC THEO NHÓM ĐẶC TÍNH DƯỢC LÝ\n\n")
        f.write("| STT | Tên thuốc / Nhóm thuốc 1 | Tên thuốc / Nhóm thuốc 2 | Cơ chế | Hậu quả | Xử trí |\n")
        f.write("| :---: | :--- | :--- | :--- | :--- | :--- |\n")
        for r in records_3_2:
            m = r['mechanism'].replace('|', '\\|')
            c = r['consequence'].replace('|', '\\|')
            x = r['management'].replace('|', '\\|')
            d1 = r['drug_1'].replace('|', '\\|')
            d2 = r['drug_2'].replace('|', '\\|')
            f.write(f"| {r['stt']} | **{d1}** | **{d2}** | {m} | {c} | {x} |\n")

    size_kb = os.path.getsize(md_path) / 1024
    print(f"🎉 Hoàn tất toàn bộ! File Markdown chuẩn: {md_path} ({size_kb:.1f} KB)")

if __name__ == "__main__":
    main()
