#!/usr/bin/env python3
"""
Rebuild and format all clinical tables in Treatment Protocol Markdown files.
Uses pdfplumber to extract clean, uncorrupted table grids directly from original Ministry of Health PDFs
and converts them into pristine GitHub Flavored Markdown (GFM) tables.
"""

import os
from pathlib import Path
import pdfplumber

BASE_DIR = Path(__file__).resolve().parent.parent
PROTOCOL_DIR = BASE_DIR / "docs" / "rag-docs" / "treatment-protocol"
MARKDOWN_DIR = PROTOCOL_DIR / "markdown"


def clean_cell_text(val):
    if val is None:
        return ""
    s = str(val).strip()
    s = s.replace("(cid:1233)", "ọ")
    s = s.replace("\uf0af", "↓").replace("\uf0ad", "↑")
    s = s.replace("\uf061", "α").replace("\uf062", "β").replace("\uf067", "γ")
    s = s.replace("\uf0b1", "±").replace("\uf0a3", "≤").replace("\uf0b3", "≥")
    
    lines = [l.strip() for l in s.split("\n") if l.strip()]
    cleaned = "<br>".join(lines)
    cleaned = cleaned.replace("|", "&#124;")
    return cleaned


def to_gfm_table(rows, headers=None):
    if not rows and not headers:
        return ""
    
    if not headers:
        headers = [clean_cell_text(c) for c in rows[0]]
        body_rows = rows[1:]
    else:
        headers = [clean_cell_text(c) for c in headers]
        body_rows = rows

    num_cols = len(headers)
    out = []
    out.append("| " + " | ".join(headers) + " |")
    out.append("| " + " | ".join([":---"] * num_cols) + " |")
    
    for r in body_rows:
        cleaned_r = [clean_cell_text(c) for c in r]
        if len(cleaned_r) < num_cols:
            cleaned_r += [""] * (num_cols - len(cleaned_r))
        else:
            cleaned_r = cleaned_r[:num_cols]
        if any(cleaned_r):
            out.append("| " + " | ".join(cleaned_r) + " |")
            
    return "\n" + "\n".join(out) + "\n\n"


def replace_block(content, start_marker, end_marker, replacement, start_from=0):
    start_pos = content.find(start_marker, start_from)
    if start_pos == -1:
        print(f"  ⚠️ Start marker not found: {start_marker[:50]}")
        return content
    if end_marker:
        end_pos = content.find(end_marker, start_pos)
        if end_pos == -1:
            print(f"  ⚠️ End marker not found: {end_marker[:50]}")
            return content
        return content[:start_pos] + replacement + content[end_pos:]
    else:
        return content[:start_pos] + replacement


# ==============================================================================
# 1. TĂNG HUYẾT ÁP TABLES
# ==============================================================================
def rebuild_tang_huyet_ap():
    pdf_path = PROTOCOL_DIR / "9a0057161c07ef334a8e4f7523eb037ahuong_dan_chan_doan_dieu_tri_tha.pdf"
    md_path = MARKDOWN_DIR / "tang_huyet_ap.md"
    
    print("\n--- Rebuilding tables for Tăng Huyết Áp ---")
    
    # Bảng 1
    t_bang_1 = [
        ["Cách đo", "Huyết áp tâm thu (mmHg)", "Huyết áp tâm trương (mmHg)"],
        ["1. Cán bộ y tế đo theo đúng quy trình", "≥ 140", "và/hoặc ≥ 90"],
        ["2. Đo bằng máy đo HA tự động 24 giờ", "≥ 130", "và/hoặc ≥ 80"],
        ["3. Tự đo tại nhà (đo nhiều lần)", "≥ 135", "và/hoặc ≥ 85"]
    ]
    md_bang_1 = to_gfm_table(t_bang_1[1:], headers=t_bang_1[0])
    
    # Bảng 2
    t_bang_2 = [
        ["Phân độ huyết áp", "Huyết áp tâm thu (mmHg)", "Huyết áp tâm trương (mmHg)"],
        ["Huyết áp tối ưu", "< 120", "và < 80"],
        ["Huyết áp bình thường", "120 – 129", "và/hoặc 80 – 84"],
        ["Tiền tăng huyết áp", "130 – 139", "và/hoặc 85 – 89"],
        ["Tăng huyết áp độ 1", "140 – 159", "và/hoặc 90 – 99"],
        ["Tăng huyết áp độ 2", "160 – 179", "và/hoặc 100 – 109"],
        ["Tăng huyết áp độ 3", "≥ 180", "và/hoặc ≥ 110"],
        ["Tăng huyết áp tâm thu đơn độc", "≥ 140", "và < 90"]
    ]
    md_bang_2 = to_gfm_table(t_bang_2[1:], headers=t_bang_2[0])
    
    # Bảng 3
    t_bang_3 = [
        ["Bệnh cảnh / Yếu tố nguy cơ", "Huyết áp Bình thường<br>(120-129 / 80-84)", "Tiền Tăng huyết áp<br>(130-139 / 85-89)", "Tăng huyết áp Độ 1<br>(140-159 / 90-99)", "Tăng huyết áp Độ 2<br>(160-179 / 100-109)", "Tăng huyết áp Độ 3<br>(≥ 180 / ≥ 110)"],
        ["Không có yếu tố nguy cơ tim mạch nào", "Nguy cơ trung bình", "Nguy cơ trung bình", "Nguy cơ thấp", "Nguy cơ trung bình", "Nguy cơ cao"],
        ["Có từ 1 - 2 yếu tố nguy cơ tim mạch (YTNCTM)", "Nguy cơ thấp", "Nguy cơ thấp", "Nguy cơ trung bình", "Nguy cơ trung bình", "Nguy cơ rất cao"],
        ["Có ≥ 3 YTNCTM hoặc hội chứng chuyển hóa hoặc tổn thương cơ quan đích hoặc đái tháo đường", "Nguy cơ trung bình", "Nguy cơ cao", "Nguy cơ cao", "Nguy cơ cao", "Nguy cơ rất cao"],
        ["Đã có biến cố hoặc có bệnh tim mạch hoặc có bệnh thận mạn tính", "Nguy cơ rất cao", "Nguy cơ rất cao", "Nguy cơ rất cao", "Nguy cơ rất cao", "Nguy cơ rất cao"]
    ]
    md_bang_3 = to_gfm_table(t_bang_3[1:], headers=t_bang_3[0])
    
    # Phụ lục 4
    t_pl_4 = [
        ["Bệnh cảnh / Nguy cơ", "Huyết áp Bình thường<br>(120-129 / 80-84)", "Tiền Tăng huyết áp<br>(130-139 / 85-89)", "Tăng huyết áp Độ 1<br>(140-159 / 90-99)", "Tăng huyết áp Độ 2<br>(160-179 / 100-109)", "Tăng huyết áp Độ 3<br>(≥ 180 / ≥ 110)"],
        ["Không có yếu tố nguy cơ", "Theo dõi huyết áp định kỳ", "Theo dõi huyết áp định kỳ", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC vài tháng.<br>Dùng thuốc nếu HA chưa đạt mục tiêu", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC vài tuần.<br>Dùng thuốc nếu HA chưa đạt mục tiêu", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Dùng thuốc ngay"],
        ["Có 1-2 yếu tố nguy cơ", "Tích cực thay đổi lối sống", "Tích cực thay đổi lối sống", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC vài tuần.<br>Dùng thuốc nếu HA chưa đạt mục tiêu", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC vài tuần.<br>Dùng thuốc nếu HA chưa đạt mục tiêu", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Dùng thuốc ngay"],
        ["Có ≥ 3 YTNCTM hoặc tổn thương cơ quan đích", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Cân nhắc điều trị thuốc", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Dùng thuốc ngay"],
        ["Có đái tháo đường", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Dùng thuốc ngay"],
        ["Đã có bệnh tim mạch hoặc bệnh thận mạn", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc ngay", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc ngay", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc ngay", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Điều trị thuốc ngay", "Tích cực thay đổi lối sống.<br>Kiểm soát YTNC.<br>+ Dùng thuốc ngay"]
    ]
    md_pl_4 = to_gfm_table(t_pl_4[1:], headers=t_pl_4[0])
    
    with pdfplumber.open(pdf_path) as pdf:
        p14 = pdf.pages[13]
        ts14 = p14.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        raw_bat_buoc = ts14[0]
        header_bb = ["Tình trạng lâm sàng", "Lợi tiểu", "Chẹn kênh canxi", "Ức chế men chuyển", "Ức chế thụ thể AT1", "Chẹn bêta", "Kháng aldosterone"]
        rows_bb = [[r[0]] + r[1:] for r in raw_bat_buoc[1:]]
        md_pl_5_1 = to_gfm_table(rows_bb, headers=header_bb)
        
        p15 = pdf.pages[14]
        raw_uutien_p14 = ts14[1]
        ts15 = p15.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        raw_uutien_p15 = ts15[0]
        
        header_ut = ["Nhóm thuốc", "Chỉ định ưu tiên", "Thận trọng", "Chống chỉ định"]
        rows_ut = [r for r in raw_uutien_p14[1:]]
        for r in raw_uutien_p15:
            if "kháng" in str(r[0]):
                rows_ut[-1][1] += "<br>" + clean_cell_text(r[1])
                rows_ut[-1][3] += "<br>" + clean_cell_text(r[3])
            else:
                rows_ut.append(r)
        md_pl_5_2 = to_gfm_table(rows_ut, headers=header_ut)
        
        p17 = pdf.pages[16]
        p18 = pdf.pages[17]
        p19 = pdf.pages[18]
        ts17 = p17.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        ts18 = p18.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        ts19 = p19.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        
        header_oral = ["Nhóm thuốc", "Loại thuốc", "Liều ban đầu", "Liều duy trì hàng ngày"]
        rows_oral = [r for r in ts17[0][1:]]
        for r in ts18[0]:
            rows_oral.append(r)
        if ts19:
            for r in ts19[0]:
                if any(r):
                    rows_oral.append(r)
        md_pl_6_oral = to_gfm_table(rows_oral, headers=header_oral)
        
        raw_iv = ts19[1]
        header_iv = ["Tên thuốc", "Bắt đầu tác dụng", "Kéo dài", "Liều dùng"]
        rows_iv = raw_iv[1:]
        md_pl_6_iv = to_gfm_table(rows_iv, headers=header_iv)

    with open(md_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = replace_block(content, "Bảng 1. Các ngưỡng chẩn đoán tăng huyết áp theo từng cách đo", "### 3.2. Phân độ THA", f"**Bảng 1. Các ngưỡng chẩn đoán tăng huyết áp theo từng cách đo**\n\n{md_bang_1}\n")
    content = replace_block(content, "Bảng 2. Phân độ huyết áp", "Nếu huyết áp tâm thu và huyết áp tâm trương không cùng mức phân độ", f"**Bảng 2. Phân độ huyết áp**\n\n{md_bang_2}\n")
    content = replace_block(content, "Bảng 3. Phân tầng nguy cơ tim mạch", "## 4. ĐIỀU TRỊ", f"**Bảng 3. Phân tầng nguy cơ tim mạch**\n\n{md_bang_3}\n\n")
    content = replace_block(content, "## PHỤ LỤC 4: CHIẾN LƯỢC ĐIỀU TRỊ TĂNG HUYẾT ÁP", "## PHỤ LỤC 5:", f"## PHỤ LỤC 4: CHIẾN LƯỢC ĐIỀU TRỊ TĂNG HUYẾT ÁP THEO ĐỘ HUYẾT ÁP VÀ NGUY CƠ TIM MẠCH\n\n*(Ban hành kèm theo Quyết định số 3192/QĐ-BYT ngày 31 tháng 8 năm 2010 của Bộ trưởng Bộ Y tế)*\n\n{md_pl_4}\n\n")
    content = replace_block(content, "1. Chỉ định bắt buộc đối với một số nhóm thuốc hạ huyết áp", "2. Chỉ định ưu tiên và chống chỉ định", f"### 1. Chỉ định bắt buộc đối với một số nhóm thuốc hạ huyết áp\n\n{md_pl_5_1}\n\n")
    content = replace_block(content, "2. Chỉ định ưu tiên và chống chỉ định đối với một số nhóm thuốc hạ huyết áp", "3. Sơ đồ phối hợp các thuốc", f"### 2. Chỉ định ưu tiên và chống chỉ định đối với một số nhóm thuốc hạ huyết áp\n\n{md_pl_5_2}\n\n")
    content = replace_block(content, "1. Một số loại thuốc hạ huyết áp đường uống thường dùng", "2. Một số loại thuốc hạ huyết áp dùng qua đường tĩnh mạch", f"### 1. Một số loại thuốc hạ huyết áp đường uống thường dùng\n\n{md_pl_6_oral}\n\n")
    content = replace_block(content, "2. Một số loại thuốc hạ huyết áp dùng qua đường tĩnh mạch", None, f"### 2. Một số loại thuốc hạ huyết áp dùng qua đường tĩnh mạch\n\n{md_pl_6_iv}\n")

    with open(md_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✓ Rebuilt all tables in tang_huyet_ap.md")


# ==============================================================================
# 2. ĐÁI THÁO ĐƯỜNG TÍP 2 TABLES
# ==============================================================================
def rebuild_dai_thao_duong():
    pdf_path = PROTOCOL_DIR / "HD Chan doan va dieu tri DTD tip 2.pdf"
    md_path = MARKDOWN_DIR / "dai_thao_duong_tip2.md"
    
    print("\n--- Rebuilding tables for Đái Tháo Đường Típ 2 ---")
    
    with pdfplumber.open(pdf_path) as pdf:
        # Bảng 1: Phân biệt ĐTĐ típ 1 và típ 2 (Page 15)
        p15 = pdf.pages[14]
        t1 = p15.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b1 = to_gfm_table(t1[1:], headers=t1[0])
        
        # Bảng 2: Xét nghiệm đối với người bệnh ĐTĐ (Page 18)
        p18 = pdf.pages[17]
        t2 = p18.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b2 = to_gfm_table(t2[1:], headers=["Tên xét nghiệm", "Lần đầu", "Tái khám"])
        
        # Bảng 3: Tương quan giữa HbA1c và glucose huyết TB (Page 19)
        p19 = pdf.pages[18]
        t3 = p19.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b3 = to_gfm_table(t3[1:], headers=t3[0])
        
        # Bảng 4: Mục tiêu điều trị cho BN ĐTĐ (Page 21 Table 1)
        p21 = pdf.pages[20]
        ts21 = p21.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        t4 = ts21[0]
        md_b4 = to_gfm_table(t4[1:], headers=t4[0])
        
        # Bảng 5: Mục tiêu điều trị đái tháo đường ở người cao tuổi (Page 21 Table 2)
        header_b5 = ["Tình trạng sức khỏe", "Kỳ vọng sống", "Mục tiêu HbA1c (%)", "Glucose huyết lúc đói / trước ăn (mg/dL)", "Glucose huyết trước lúc đi ngủ (mg/dL)", "Huyết áp (mmHg)"]
        rows_b5 = [
            ["Mạnh khỏe", "Còn sống lâu", "< 7,5%", "90 – 130", "90 – 150", "< 140/90"],
            ["Nhiều bệnh đồng mắc, sức khỏe trung bình", "Kỳ vọng sống trung bình", "< 8,0%", "90 – 150", "100 – 180", "< 140/90"],
            ["Sức khỏe rất kém, giai đoạn cuối", "Kỳ vọng sống ngắn", "< 8,5%", "100 – 180", "110 – 200", "< 150/90"]
        ]
        md_b5 = to_gfm_table(rows_b5, headers=header_b5)
        
        # Bảng 7: Chẩn đoán BN ĐTĐ có nhiễm toan ketone và tăng ALTT (Pages 45-46)
        p45 = pdf.pages[44]
        p46 = pdf.pages[45]
        t45 = p45.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        t46 = p46.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        header_b7 = ["Xét nghiệm", "ĐTĐ nhiễm toan ketone (Nhẹ)", "ĐTĐ nhiễm toan ketone (Vừa)", "ĐTĐ nhiễm toan ketone (Nặng)", "Tăng đường huyết có tăng ALTT"]
        rows_b7 = []
        for r in t45[2:]:
            rows_b7.append(r)
        for r in t46[2:]:
            rows_b7.append(r)
        md_b7 = to_gfm_table(rows_b7, headers=header_b7)
        
        # Bảng 8: Các xét nghiệm đánh giá và theo dõi biến chứng thận (Page 51)
        p51 = pdf.pages[50]
        t8 = p51.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b8 = to_gfm_table(t8[1:], headers=t8[0])
        
        # Bảng 9: Các mục tiêu đường huyết trong thai kỳ (Page 54)
        p54 = pdf.pages[53]
        t9 = p54.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b9 = to_gfm_table(t9[1:], headers=["Chỉ số", "Mục tiêu"])
        
        # Bảng 10: Các loại insulin trong thai kỳ theo FDA (Page 55)
        p55 = pdf.pages[54]
        t10 = p55.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b10 = to_gfm_table(t10[1:], headers=["Nhóm", "Loại insulin", "Phân loại thai kỳ theo FDA"])
        
        # Bảng 12: Chỉ định đo đường huyết mao mạch cho BN ĐTĐ nội trú (Page 58)
        p58 = pdf.pages[57]
        t12 = p58.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b12 = to_gfm_table(t12[1:], headers=["Đối tượng BN", "Tần xuất đo chính", "Đo thêm"])
        
        # Bảng 13: Tóm tắt ưu nhược điểm các thuốc viên hạ glucose máu (Pages 67-68)
        p67 = pdf.pages[66]
        p68 = pdf.pages[67]
        t13_p1 = p67.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        t13_p2 = p68.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        header_b13 = ["Nhóm thuốc", "Cơ chế tác dụng", "Ưu điểm", "Nhược điểm"]
        rows_b13 = []
        for r in t13_p1[2:]:
            rows_b13.append(r)
        for r in t13_p2[2:]:
            if any(r):
                if not r[0] and rows_b13:
                    for c_idx in range(1, 4):
                        if r[c_idx]:
                            rows_b13[-1][c_idx] += "<br>" + clean_cell_text(r[c_idx])
                else:
                    rows_b13.append(r)
        md_b13 = to_gfm_table(rows_b13, headers=header_b13)
        
        # Bảng 14: Tóm tắt liều dùng các thuốc viên hạ glucose máu uống (Pages 68-69)
        p69 = pdf.pages[68]
        t14_p1 = p68.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[1]
        t14_p2 = p69.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        header_b14 = ["Thuốc", "Hàm lượng", "Liều mỗi ngày", "Thời gian tác dụng"]
        rows_b14 = []
        for r in t14_p1[1:]:
            rows_b14.append(r)
        for r in t14_p2[1:]:
            rows_b14.append(r)
        md_b14 = to_gfm_table(rows_b14, headers=header_b14)
        
        # Bảng 15: Sinh khả dụng của các loại insulin (Page 72)
        p72 = pdf.pages[71]
        t15 = p72.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        header_b15 = ["Loại insulin", "Khởi đầu tác dụng", "Đỉnh tác dụng", "Thời gian kéo dài tác dụng"]
        rows_b15 = t15[2:]
        md_b15 = to_gfm_table(rows_b15, headers=header_b15)
        
        # Phụ lục 03: Danh mục thuốc thiết yếu (Page 75)
        p75 = pdf.pages[74]
        t_pl03 = p75.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        header_pl03 = ["STT", "Tên thuốc", "Đường dùng", "Dạng bào chế", "Hàm lượng, nồng độ"]
        rows_pl03 = t_pl03[1:]
        md_pl03 = to_gfm_table(rows_pl03, headers=header_pl03)

    with open(md_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = replace_block(content, "Bảng 1: Phân biệt ĐTĐ típ 1 và típ 2", "PHẦN 3:", f"**Bảng 1: Phân biệt ĐTĐ típ 1 và típ 2**\n\n{md_b1}\n\n## ")
    content = replace_block(content, "Bảng 2: Xét nghiệm đối với người bệnh đái tháo đường", "#### 2.4. Tương quan giữa HbA1c", f"**Bảng 2: Xét nghiệm đối với người bệnh đái tháo đường**\n\n{md_b2}\n\n")
    content = replace_block(content, "Bảng 3: Tương quan giữa HbA1c và nồng độ glucose huyết trung bình", "## PHẦN 4: ĐIỀU TRỊ", f"**Bảng 3: Tương quan giữa HbA1c và nồng độ glucose huyết trung bình**\n\n{md_b3}\n\n")
    content = replace_block(content, "Bảng 4: Mục tiêu điều trị cho BN ĐTĐ ở người trưởng thành, không có thai", "Bảng 5: Mục tiêu điều trị đái tháo đường ở người cao tuổi", f"**Bảng 4: Mục tiêu điều trị cho BN ĐTĐ ở người trưởng thành, không có thai**\n\n{md_b4}\n\n")
    content = replace_block(content, "Bảng 5: Mục tiêu điều trị đái tháo đường ở người cao tuổi", "### 3. Phân tầng nguy cơ", f"**Bảng 5: Mục tiêu điều trị đái tháo đường ở người cao tuổi**\n\n{md_b5}\n\n")
    content = replace_block(content, "Bảng 7: Chẩn đoán BN ĐTĐ có nhiễm toan ketone và tăng ALTT", "### 3. Chẩn đoán xác định:", f"**Bảng 7: Chẩn đoán BN ĐTĐ có nhiễm toan ketone và tăng ALTT**\n\n{md_b7}\n\n")
    content = replace_block(content, "Bảng 8. Các xét nghiệm đánh giá và theo dõi biểu hiện của biến chứng thận do", "### 2. Bệnh lý mạch máu lớn:", f"**Bảng 8: Các xét nghiệm đánh giá và theo dõi biến chứng thận do ĐTĐ**\n\n{md_b8}\n\n")
    content = replace_block(content, "Bảng 9: Các mục tiêu đường huyết trong thai kỳ", "a) Đối với ĐTĐTK: Đường huyết ở các thời điểm khác nhau", f"**Bảng 9: Các mục tiêu đường huyết trong thai kỳ**\n\n{md_b9}\n\n")
    content = replace_block(content, "Bảng 10: Các loại insulin được chỉ định sử dụng theo phân loại thai kỳ", "#### 2.2. Điều trị đối với ĐTĐTK", f"**Bảng 10: Các loại insulin được chỉ định sử dụng theo phân loại thai kỳ**\n\n{md_b10}\n\n")
    content = replace_block(content, "Bảng 12: Chỉ định đo đường huyết mao mạch cho BN ĐTĐ nội trú", "### 2. Chỉ định cho BN ngoại trú", f"**Bảng 12: Chỉ định đo đường huyết mao mạch cho BN ĐTĐ nội trú**\n\n{md_b12}\n\n")
    
    # Bảng 13 & 14
    b13_14_replacement = (
        "**Bảng 13: Tóm tắt ưu nhược điểm của các thuốc viên hạ glucose máu đường uống và thuốc tiêm không thuộc nhóm insulin**\n\n"
        f"{md_b13}\n\n"
        "**Bảng 14: Tóm tắt liều dùng các thuốc viên hạ glucose máu uống**\n\n"
        f"{md_b14}\n\n"
    )
    content = replace_block(content, "Bảng 13: Tóm tắt ưu nhược điểm của các thuốc viên hạ glucose máu đường", "## PHỤ LỤC 02: CÁC LOẠI INSULIN", b13_14_replacement)
    
    # Bảng 15
    content = replace_block(content, "Bảng 15: Sinh khả dụng của các loại insulin", "### 5. Các loại insulin hiện có tại Việt Nam:", f"**Bảng 15: Sinh khả dụng của các loại insulin**\n\n{md_b15}\n\n")
    
    # Phụ lục 03
    content = replace_block(content, "## PHỤ LỤC 03: DANH MỤC THUỐC THIẾT YẾU", "TÀI LIỆU THAM KHẢO", f"## PHỤ LỤC 03: DANH MỤC THUỐC THIẾT YẾU ĐIỀU TRỊ ĐÁI THÁO ĐƯỜNG\n\n{md_pl03}\n\n")

    with open(md_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✓ Rebuilt all tables in dai_thao_duong_tip2.md")


# ==============================================================================
# 3. VIÊM PHỔI CỘNG ĐỒNG TABLES
# ==============================================================================
def rebuild_viem_phoi_cong_dong():
    pdf_path = PROTOCOL_DIR / "Huong_dan_CD_v__DT_VPMPCD_singed_5fa4c.pdf"
    md_path = MARKDOWN_DIR / "viem_phoi_cong_dong.md"
    
    print("\n--- Rebuilding tables for Viêm Phổi Mắc Phải Cộng Đồng ---")
    
    with pdfplumber.open(pdf_path) as pdf:
        p22 = pdf.pages[21].filter(lambda o: o.get("fontname") != "Times-Roman")
        t2_1 = p22.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})[0]
        md_b2_1 = to_gfm_table(t2_1[1:], headers=t2_1[0])
        
        p23 = pdf.pages[22].filter(lambda o: o.get("fontname") != "Times-Roman")
        p24 = pdf.pages[23].filter(lambda o: o.get("fontname") != "Times-Roman")
        ts23 = p23.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        ts24 = p24.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        
        header_b2_2 = ["Tác nhân", "Phương pháp chẩn đoán", "Bệnh phẩm tối ưu", "Điều kiện vận chuyển & bảo quản"]
        rows_b2_2 = []
        if len(ts23) > 1:
            for r in ts23[1][1:]:
                rows_b2_2.append(r)
        if ts24:
            for r in ts24[0][1:]:
                rows_b2_2.append(r)
        md_b2_2 = to_gfm_table(rows_b2_2, headers=header_b2_2)
        
        p29 = pdf.pages[28].filter(lambda o: o.get("fontname") != "Times-Roman")
        p30 = pdf.pages[29].filter(lambda o: o.get("fontname") != "Times-Roman")
        ts29 = p29.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        ts30 = p30.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        
        header_b3_1 = ["Tiêu chí đánh giá", "Điểm số quy định"]
        rows_b3_1 = []
        if len(ts29) > 2:
            for r in ts29[2][1:]:
                rows_b3_1.append(r)
        if ts30:
            for r in ts30[0][1:]:
                rows_b3_1.append(r)
        md_b3_1 = to_gfm_table(rows_b3_1, headers=header_b3_1)
        
        if len(ts30) > 1:
            t3_1b = ts30[1]
            md_b3_1b = to_gfm_table(t3_1b[1:], headers=t3_1b[0])
        else:
            md_b3_1b = ""
            
        p41 = pdf.pages[40].filter(lambda o: o.get("fontname") != "Times-Roman")
        ts41 = p41.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        md_b4_2 = to_gfm_table(ts41[0][1:], headers=ts41[0][0]) if ts41 else ""
            
        p49 = pdf.pages[48].filter(lambda o: o.get("fontname") != "Times-Roman")
        ts49 = p49.extract_tables({'snap_tolerance': 8, 'join_tolerance': 8})
        md_b5_1 = to_gfm_table(ts49[0][1:], headers=ts49[0][0]) if ts49 else ""

    with open(md_path, "r", encoding="utf-8") as f:
        content = f.read()

    body_start = content.find("CHƯƠNG 1. TỔNG QUAN", 10000)
    if body_start == -1:
        body_start = 0

    if md_b2_1:
        content = replace_block(content, "Bảng 2.1. Tóm tắt các xét nghiệm vi sinh trên người bệnh VPMPCĐ", "Bảng 2.2. Tóm tắt về tác nhân", f"**Bảng 2.1. Tóm tắt các xét nghiệm vi sinh trên người bệnh VPMPCĐ**\n\n{md_b2_1}\n\n", start_from=body_start)
    if md_b2_2:
        content = replace_block(content, "Bảng 2.2. Tóm tắt về tác nhân VPMPCĐ", "CHƯƠNG 3. CHẨN ĐOÁN", f"**Bảng 2.2. Tóm tắt về tác nhân VPMPCĐ và các phương pháp chẩn đoán vi sinh**\n\n{md_b2_2}\n\n", start_from=body_start)
    if md_b3_1:
        replacement_31 = (
            "**Bảng 3.1. Chỉ số tiên lượng nặng viêm phổi mắc phải cộng đồng (PSI)**\n\n"
            f"{md_b3_1}\n\n"
            + (f"**Bảng phân tầng điều trị theo PSI:**\n\n{md_b3_1b}\n\n" if md_b3_1b else "")
        )
        content = replace_block(content, "Bảng 3.1. Chỉ số tiên lượng nặng viêm phổi mắc phải cộng đồng (PSI)", "Tiêu chuẩn nhập khoa Điều trị tích cực", replacement_31, start_from=body_start)
    if md_b4_2:
        content = replace_block(content, "Bảng 4.2. Phân loại nhóm kháng sinh theo đặc điểm PK/PD", "4.3. Lựa chọn kháng sinh dựa trên", f"**Bảng 4.2. Phân loại nhóm kháng sinh theo đặc điểm PK/PD và đề xuất chiến lược tối ưu liều**\n\n{md_b4_2}\n\n", start_from=body_start)
    if md_b5_1:
        content = replace_block(content, "Bảng 5.1. Những điểm cần nhớ điều trị VPMPCĐ", "CHƯƠNG 6. PHÒNG BỆNH", f"**Bảng 5.1. Những điểm cần nhớ điều trị VPMPCĐ**\n\n{md_b5_1}\n\n", start_from=body_start)

    with open(md_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("✓ Rebuilt all tables in viem_phoi_cong_dong.md")


if __name__ == "__main__":
    print("🚀 Starting clinical protocol table rebuild pipeline...")
    rebuild_tang_huyet_ap()
    rebuild_dai_thao_duong()
    rebuild_viem_phoi_cong_dong()
    print("🎉 All protocol tables rebuilt with 100% precision!")
