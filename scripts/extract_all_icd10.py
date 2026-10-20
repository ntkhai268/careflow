import pdfplumber
import csv
import os
import time

def resume_extract_icd10():
    pdf_path = "docs/rag-docs/icd10/06-byt-kem.pdf"
    output_csv = "docs/rag-docs/icd10/icd10_dictionary.csv"
    
    # Đọc các mã đã có
    seen_codes = set()
    records = []
    if os.path.exists(output_csv):
        with open(output_csv, 'r', encoding='utf-8-sig') as f:
            reader = csv.DictReader(f)
            for r in reader:
                code = r.get('icd10_code') or r.get('\ufefficd10_code')
                if code:
                    seen_codes.add(code)
                    records.append(r)
    print(f"📦 Đã có sẵn {len(records)} mã bệnh từ trước.")

    start_time = time.time()

    def save_csv():
        with open(output_csv, 'w', encoding='utf-8-sig', newline='') as f:
            fieldnames = ['icd10_code', 'disease_name_vi', 'disease_name_en', 'chapter', 'block']
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for r in records:
                c = r.get('icd10_code') or r.get('\ufefficd10_code')
                writer.writerow({
                    'icd10_code': c,
                    'disease_name_vi': r.get('disease_name_vi', ''),
                    'disease_name_en': r.get('disease_name_en', ''),
                    'chapter': r.get('chapter', ''),
                    'block': r.get('block', '')
                })

    with pdfplumber.open(pdf_path) as pdf:
        total_pages = len(pdf.pages)
        # Bắt đầu từ trang 1000 để quét tiếp 271 trang cuối
        start_page = 1000
        print(f"📊 Bắt đầu quét tiếp từ trang {start_page + 1} đến {total_pages}...")

        current_chapter = "Bệnh hệ cơ xương khớp và mô liên kết"
        current_block = "Bệnh lý cột sống"

        for idx in range(start_page, total_pages):
            page = pdf.pages[idx]
            try:
                tables = page.extract_tables()
            except Exception as e:
                continue

            for table in tables:
                for row in table:
                    if not row or len(row) < 22:
                        continue

                    if row[0] and ('STT' in row[0] or 'Phụ lục' in row[0]):
                        continue
                    if row[1] and 'STT' in row[1]:
                        continue

                    if len(row) > 4 and row[4] and row[4].strip():
                        current_chapter = row[4].strip().replace('\n', ' ')
                    if len(row) > 7 and row[7] and row[7].strip():
                        current_block = row[7].strip().replace('\n', ' ')

                    code = row[17].strip() if len(row) > 17 and row[17] else ""
                    name_en = row[19].strip().replace('\n', ' ') if len(row) > 19 and row[19] else ""
                    name_vi = row[21].strip().replace('\n', ' ') if len(row) > 21 and row[21] else ""

                    name_vi = name_vi.replace("(cid:1233)", "ọ")
                    current_chapter = current_chapter.replace("(cid:1233)", "ọ")
                    current_block = current_block.replace("(cid:1233)", "ọ")

                    if code and name_vi and code not in ('MÃ BỆNH', '18', 'MÃ'):
                        if code not in seen_codes:
                            seen_codes.add(code)
                            records.append({
                                'icd10_code': code,
                                'disease_name_vi': name_vi,
                                'disease_name_en': name_en,
                                'chapter': current_chapter,
                                'block': current_block
                            })

            if (idx + 1) % 100 == 0 or (idx + 1) == total_pages:
                elapsed = time.time() - start_time
                save_csv()
                print(f"   ⏳ Đã quét đến trang {idx + 1}/{total_pages} ({elapsed:.1f}s). Tổng số mã hiện tại: {len(records)}...")

    save_csv()
    total_time = time.time() - start_time
    print(f"🎉 Hoàn tất 100%! Đã lưu toàn bộ {len(records)} mã bệnh ICD-10 vào: {output_csv} (Thời gian: {total_time:.1f}s)")

if __name__ == "__main__":
    resume_extract_icd10()
