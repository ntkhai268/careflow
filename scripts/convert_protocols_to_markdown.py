import os
from markitdown import MarkItDown

def main():
    md = MarkItDown()
    input_dir = "docs/rag-docs/treatment-protocol"
    output_dir = "docs/rag-docs/treatment-protocol/markdown"
    os.makedirs(output_dir, exist_ok=True)

    mappings = [
        ("Huong_dan_CD_v__DT_VPMPCD_singed_5fa4c.pdf", "viem_phoi_cong_dong.md", "Viêm phổi mắc phải cộng đồng"),
        ("HD Chan doan va dieu tri DTD tip 2.pdf", "dai_thao_duong_tip2.md", "Đái tháo đường týp 2"),
        ("9a0057161c07ef334a8e4f7523eb037ahuong_dan_chan_doan_dieu_tri_tha.pdf", "tang_huyet_ap.md", "Tăng huyết áp")
    ]

    print("🚀 Bắt đầu chuyển đổi phác đồ điều trị sang Markdown bằng MarkItDown...")
    for src_file, dest_file, title in mappings:
        src_path = os.path.join(input_dir, src_file)
        dest_path = os.path.join(output_dir, dest_file)
        if not os.path.exists(src_path):
            print(f"⚠️ Không tìm thấy: {src_path}")
            continue

        print(f"⏳ Đang xử lý: {title} ({src_file})...")
        try:
            result = md.convert(src_path)
            with open(dest_path, "w", encoding="utf-8") as f:
                f.write(result.text_content)
            size_kb = os.path.getsize(dest_path) / 1024
            print(f"✅ Hoàn tất: {dest_path} ({size_kb:.1f} KB)")
        except Exception as e:
            print(f"❌ Lỗi khi convert {src_file}: {e}")

    print("🎉 Hoàn tất toàn bộ!")

if __name__ == "__main__":
    main()
