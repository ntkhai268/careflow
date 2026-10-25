import os
from markitdown import MarkItDown

def main():
    md = MarkItDown()
    input_file = "docs/rag-docs/ddi/Quyết-định-5948-QĐ-BYT.pdf"
    output_dir = "docs/rag-docs/ddi/markdown"
    output_file = os.path.join(output_dir, "tuong_tac_thuoc_chong_chi_dinh_5948.md")

    os.makedirs(output_dir, exist_ok=True)

    print(f"🚀 Bắt đầu chuyển đổi DDI PDF sang Markdown bằng MarkItDown...")
    print(f"   Nguồn: {input_file}")
    print(f"   Đích:  {output_file}")

    if not os.path.exists(input_file):
        print(f"❌ Không tìm thấy file: {input_file}")
        return

    try:
        result = md.convert(input_file)
        content = result.text_content
        
        # Sửa lỗi font CID thường gặp trong văn bản hành chính BYT
        content = content.replace("(cid:1233)", "ọ")

        with open(output_file, "w", encoding="utf-8") as f:
            f.write(content)

        size_kb = os.path.getsize(output_file) / 1024
        print(f"✅ Hoàn tất thành công: {output_file} ({size_kb:.1f} KB)")
    except Exception as e:
        print(f"❌ Lỗi khi convert bằng MarkItDown: {e}")

if __name__ == "__main__":
    main()
