import pdfplumber
import csv
import os
import sys

def extract_icd10(pdf_path, output_csv, max_pages=None):
    print(f"🚀 Bắt đầu trích xuất bảng ICD-10 từ: {pdf_path}")
    print(f"   Đích đến: {output_csv}")

    records = []
    seen_codes = set()

    with pdfplumber.open(pdf_path) as pdf:
        total = len(pdf.pages)
        pages_to_process = min(total, max_pages) if max_pages else total
        print(f"📊 Tổng số trang cần xử lý: {pages_to_process}/{total} trang")

        current_chapter = ""
        current_block = ""

        for idx in range(pages_to_process):
            page = pdf.pages[idx]
            try:
                tables = page.extract_tables()
            except Exception as e:
                print(f"⚠️ Trang {idx+1} lỗi: {e}")
                continue

            for table in tables:
                for row in table:
                    if not row or len(row) < 22:
                        continue
                    
                    # Bỏ qua dòng tiêu đề
                    if row[0] and ('STT' in row[0] or 'Phụ lục' in row[0]):
                        continue
                    if row[1] and 'STT' in row[1]:
                        continue
                    
                    # Cập nhật Chương và Khối nếu có
                    if len(row) > 4 and row[4] and row[4].strip():
                        current_chapter = row[4].strip().replace('\n', ' ')
                    if len(row) > 7 and row[7] and row[7].strip():
                        current_block = row[7].strip().replace('\n', ' ')

                    code = row[17].strip() if len(row) > 17 and row[17] else ""
                    name_en = row[19].strip().replace('\n', ' ') if len(row) > 19 and row[19] else ""
                    name_vi = row[21].strip().replace('\n', ' ') if len(row) > 21 and row[21] else ""

                    if code and name_vi and code not in ('MÃ BỆNH', '18'):
                        if code not in seen_codes:
                            seen_codes.add(code)
                            records.append({
                                'icd10_code': code,
                                'disease_name_vi': name_vi,
                                'disease_name_en': name_en,
                                'chapter': current_chapter,
                                'block': current_block
                            })

            if (idx + 1) % 50 == 0 or (idx + 1) == pages_to_process:
                print(f"   ⏳ Đã quét xong {idx + 1}/{pages_to_process} trang. Thu thập được: {len(records)} mã bệnh...")

    # Ghi ra file CSV
    os.makedirs(os.path.dirname(output_csv), exist_ok=True)
    with open(output_csv, 'w', encoding='utf-8-sig', newline='') as f:
        fieldnames = ['icd10_code', 'disease_name_vi', 'disease_name_en', 'chapter', 'block']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(records)

    print(f"🎉 Hoàn tất! Đã lưu {len(records)} mã bệnh vào: {output_csv}")

if __name__ == "__main__":
    pdf_file = "docs/rag-docs/icd10/06-byt-kem.pdf"
    csv_file = "docs/rag-docs/icd10/icd10_dictionary.csv"
    
    # Cho phép truyền tham số số trang muốn quét (mặc định quét 100 trang để nhanh cho MVP, hoặc tất cả)
    pages = int(sys.argv[1]) if len(sys.argv) > 1 else 100
    extract_icd10(pdf_file, csv_file, max_pages=pages)
