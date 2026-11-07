import os
import json

def generate_manual_rag_html():
    output_path = "docs/ai-service/manual-rag-import.html"
    
    html_content = """<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Hướng Dẫn Nạp CSDL Clinical RAG Local — CareFlow</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  
  <style>
    :root {
      --primary: #0277A8;
      --primary-dark: #00577E;
      --primary-light: #DDF3FC;
      --teal: #087F6A;
      --teal-light: #D9F3EC;
      --slate-900: #0F172A;
      --slate-800: #1E293B;
      --slate-700: #334155;
      --slate-600: #475569;
      --slate-500: #64748B;
      --slate-200: #E2E8F0;
      --slate-100: #F1F5F9;
      --slate-50: #F8FAFC;
      --amber-500: #F59E0B;
      --amber-50: #FFFBEB;
      --amber-200: #FDE68A;
      --emerald-600: #059669;
      --emerald-50: #ECFDF5;
      --rose-600: #E11D48;
      --rose-50: #FFF1F2;
      --purple-600: #7C3AED;
      --purple-50: #FAF5FF;
      --radius-sm: 6px;
      --radius-md: 10px;
      --radius-lg: 14px;
      --shadow-sm: 0 1px 3px rgba(0,0,0,0.06);
      --shadow-md: 0 4px 12px rgba(0,0,0,0.06);
      --shadow-lg: 0 10px 25px -5px rgba(2,119,168,0.1), 0 8px 10px -6px rgba(0,0,0,0.04);
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }

    body {
      font-family: 'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: #F8FAFC;
      color: var(--slate-800);
      line-height: 1.65;
      font-size: 15px;
      -webkit-font-smoothing: antialiased;
    }

    /* Top Navigation Bar */
    .navbar {
      position: sticky;
      top: 0;
      z-index: 100;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid var(--slate-200);
      padding: 12px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      box-shadow: var(--shadow-sm);
    }

    .nav-brand {
      display: flex;
      align-items: center;
      gap: 12px;
      text-decoration: none;
      color: var(--slate-900);
    }

    .nav-logo-badge {
      background: linear-gradient(135deg, var(--primary) 0%, var(--teal) 100%);
      color: white;
      font-weight: 800;
      font-size: 13px;
      padding: 6px 10px;
      border-radius: var(--radius-sm);
      letter-spacing: 0.5px;
    }

    .nav-title {
      font-weight: 700;
      font-size: 15px;
      color: var(--slate-900);
    }

    .nav-actions {
      display: flex;
      gap: 10px;
      align-items: center;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      font-size: 13px;
      font-weight: 600;
      border-radius: var(--radius-sm);
      border: 1px solid transparent;
      cursor: pointer;
      transition: all 0.2s ease;
      text-decoration: none;
      font-family: inherit;
    }

    .btn-primary {
      background: var(--primary);
      color: white;
    }
    .btn-primary:hover {
      background: var(--primary-dark);
      transform: translateY(-1px);
    }

    .btn-outline {
      background: white;
      color: var(--slate-700);
      border-color: var(--slate-200);
    }
    .btn-outline:hover {
      background: var(--slate-100);
      color: var(--slate-900);
    }

    /* Layout */
    .container {
      max-width: 1100px;
      margin: 32px auto 60px;
      padding: 0 24px;
      display: grid;
      grid-template-columns: 260px 1fr;
      gap: 36px;
      align-items: start;
    }

    /* Sidebar / Sticky TOC */
    .sidebar {
      position: sticky;
      top: 80px;
      background: white;
      border: 1px solid var(--slate-200);
      border-radius: var(--radius-md);
      padding: 20px;
      box-shadow: var(--shadow-sm);
    }

    .toc-title {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--slate-500);
      margin-bottom: 14px;
    }

    .toc-nav {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .toc-link {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 7px 10px;
      font-size: 13px;
      color: var(--slate-600);
      text-decoration: none;
      border-radius: var(--radius-sm);
      transition: all 0.15s ease;
    }

    .toc-link:hover {
      background: var(--slate-100);
      color: var(--primary);
    }

    .toc-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: var(--slate-100);
      font-size: 11px;
      font-weight: 700;
      color: var(--slate-600);
    }

    .toc-link:hover .toc-badge {
      background: var(--primary-light);
      color: var(--primary);
    }

    /* Main Content */
    .main-content {
      background: white;
      border: 1px solid var(--slate-200);
      border-radius: var(--radius-lg);
      padding: 44px;
      box-shadow: var(--shadow-md);
    }

    /* Hero Banner */
    .hero {
      border-bottom: 1px solid var(--slate-200);
      padding-bottom: 24px;
      margin-bottom: 32px;
    }

    .hero-badge-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;
    }

    .tag {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      padding: 4px 10px;
      border-radius: 20px;
    }

    .tag-blue {
      background: var(--primary-light);
      color: var(--primary-dark);
    }

    .tag-teal {
      background: var(--teal-light);
      color: var(--teal);
    }

    .hero-title {
      font-size: 26px;
      font-weight: 800;
      color: var(--slate-900);
      line-height: 1.3;
      margin-bottom: 10px;
    }

    .hero-desc {
      font-size: 15px;
      color: var(--slate-600);
    }

    /* Quick Start Banner */
    .quick-start-box {
      background: linear-gradient(135deg, #0277A8 0%, #00577E 100%);
      color: white;
      border-radius: var(--radius-md);
      padding: 24px 28px;
      margin-bottom: 36px;
      box-shadow: var(--shadow-lg);
    }

    .quick-start-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 14px;
    }

    .quick-start-title {
      font-size: 17px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .quick-start-sub {
      font-size: 13px;
      color: rgba(255, 255, 255, 0.85);
      margin-bottom: 16px;
    }

    /* Section Styles */
    .guide-section {
      margin-bottom: 44px;
      scroll-margin-top: 90px;
    }

    .section-header {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 16px;
      border-bottom: 2px solid var(--slate-100);
      padding-bottom: 10px;
    }

    .step-number {
      background: var(--primary);
      color: white;
      width: 32px;
      height: 32px;
      border-radius: var(--radius-sm);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 14px;
      flex-shrink: 0;
    }

    .section-title {
      font-size: 19px;
      font-weight: 700;
      color: var(--slate-900);
    }

    p {
      color: var(--slate-700);
      margin-bottom: 12px;
    }

    /* Callout Alerts */
    .alert {
      border-radius: var(--radius-sm);
      padding: 14px 18px;
      margin: 16px 0;
      display: flex;
      gap: 14px;
      font-size: 13.5px;
      line-height: 1.55;
    }

    .alert-icon {
      font-size: 18px;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .alert-important {
      background: var(--purple-50);
      border-left: 4px solid var(--purple-600);
      color: #4C1D95;
    }

    .alert-tip {
      background: var(--emerald-50);
      border-left: 4px solid var(--emerald-600);
      color: #064E3B;
    }

    .alert-warning {
      background: var(--amber-50);
      border-left: 4px solid var(--amber-500);
      color: #78350F;
    }

    /* Code Blocks with Copy Button */
    .code-container {
      position: relative;
      background: #0B132B;
      border-radius: var(--radius-md);
      overflow: hidden;
      margin: 16px 0 20px;
      box-shadow: 0 4px 14px rgba(0,0,0,0.12);
    }

    .code-header {
      background: #1C2541;
      padding: 8px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(255,255,255,0.08);
    }

    .code-lang {
      font-family: 'JetBrains Mono', monospace;
      font-size: 11px;
      text-transform: uppercase;
      font-weight: 600;
      color: #8BD2EE;
      letter-spacing: 0.5px;
    }

    .btn-copy {
      background: rgba(255, 255, 255, 0.1);
      color: #E2E8F0;
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 4px;
      padding: 4px 10px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
      font-family: inherit;
    }

    .btn-copy:hover {
      background: var(--primary);
      color: white;
      border-color: var(--primary);
    }

    .btn-copy.copied {
      background: var(--emerald-600);
      border-color: var(--emerald-600);
      color: white;
    }

    pre {
      padding: 16px;
      overflow-x: auto;
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      line-height: 1.6;
      color: #E2E8F0;
    }

    code {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
    }

    p code, li code {
      background: var(--slate-100);
      color: #D946EF;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 12.5px;
      font-weight: 500;
      border: 1px solid var(--slate-200);
    }

    /* Option Cards */
    .option-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
      margin: 18px 0;
    }

    .option-card {
      border: 1px solid var(--slate-200);
      border-radius: var(--radius-md);
      padding: 20px;
      background: var(--slate-50);
      transition: all 0.2s ease;
    }

    .option-card.recommended {
      border-color: var(--primary);
      background: white;
      box-shadow: 0 4px 12px rgba(2, 119, 168, 0.08);
      position: relative;
    }

    .option-badge {
      display: inline-block;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 3px 8px;
      border-radius: 12px;
      margin-bottom: 8px;
    }

    .badge-rec {
      background: var(--emerald-50);
      color: var(--emerald-600);
      border: 1px solid rgba(5, 150, 105, 0.2);
    }

    .badge-alt {
      background: var(--slate-200);
      color: var(--slate-700);
    }

    .option-card h4 {
      font-size: 16px;
      font-weight: 700;
      color: var(--slate-900);
      margin-bottom: 8px;
    }

    .option-card ul {
      padding-left: 18px;
      font-size: 13px;
      color: var(--slate-600);
      margin-top: 8px;
    }

    .option-card li {
      margin-bottom: 4px;
    }

    /* Tables */
    .table-container {
      overflow-x: auto;
      border: 1px solid var(--slate-200);
      border-radius: var(--radius-md);
      margin: 18px 0;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13.5px;
    }

    th {
      background: var(--slate-50);
      color: var(--slate-700);
      font-weight: 700;
      padding: 12px 16px;
      border-bottom: 1px solid var(--slate-200);
    }

    td {
      padding: 12px 16px;
      border-bottom: 1px solid var(--slate-200);
      color: var(--slate-700);
    }

    tr:last-child td {
      border-bottom: none;
    }

    tr:hover td {
      background: var(--slate-50);
    }

    /* Print Overrides */
    @media print {
      .navbar, .sidebar, .btn-copy, .quick-start-box {
        display: none !important;
      }
      .container {
        display: block;
        max-width: 100%;
        margin: 0;
        padding: 0;
      }
      .main-content {
        border: none;
        box-shadow: none;
        padding: 0;
      }
      .code-container {
        border: 1px solid #ccc;
        box-shadow: none;
      }
      pre {
        color: #000;
        background: #f4f4f4;
      }
    }

    @media (max-width: 860px) {
      .container {
        grid-template-columns: 1fr;
      }
      .sidebar {
        display: none;
      }
      .option-grid {
        grid-template-columns: 1fr;
      }
      .main-content {
        padding: 24px;
      }
    }
  </style>
</head>
<body>

  <!-- Top Navigation Bar -->
  <header class="navbar">
    <a href="#" class="nav-brand">
      <span class="nav-logo-badge">CAREFLOW AI</span>
      <span class="nav-title">Hướng Dẫn Thiết Lập & Nạp CSDL Clinical RAG</span>
    </a>
    <div class="nav-actions">
      <button class="btn btn-outline" onclick="window.print()">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 6 2 18 2 18 9"></polyline>
          <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path>
          <rect x="6" y="14" width="12" height="8"></rect>
        </svg>
        In / Xuất PDF
      </button>
      <a href="#quick-start" class="btn btn-primary">
        🚀 Lệnh Nạp Nhanh (3s)
      </a>
    </div>
  </header>

  <!-- Container -->
  <div class="container">
    
    <!-- Sticky Table of Contents -->
    <aside class="sidebar">
      <div class="toc-title">Mục lục hướng dẫn</div>
      <nav class="toc-nav">
        <a href="#git-rules" class="toc-link">
          <span class="toc-badge">1</span>
          Quy định Git (.md & .csv)
        </a>
        <a href="#prerequisites" class="toc-link">
          <span class="toc-badge">2</span>
          Yêu cầu môi trường
        </a>
        <a href="#step-1" class="toc-link">
          <span class="toc-badge">3</span>
          Bước 1: Docker pgvector
        </a>
        <a href="#step-2" class="toc-link">
          <span class="toc-badge">4</span>
          Bước 2: Nạp dữ liệu Vector
        </a>
        <a href="#step-3" class="toc-link">
          <span class="toc-badge">5</span>
          Bước 3: Xác thực dữ liệu
        </a>
        <a href="#step-4" class="toc-link">
          <span class="toc-badge">6</span>
          Bước 4: Test & Chạy Service
        </a>
        <a href="#troubleshooting" class="toc-link">
          <span class="toc-badge">7</span>
          Xử lý sự cố thường gặp
        </a>
      </nav>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      
      <!-- Hero -->
      <div class="hero">
        <div class="hero-badge-row">
          <span class="tag tag-blue">Phân hệ AI Lâm Sàng</span>
          <span class="tag tag-teal">pgvector 0.8.7</span>
          <span class="tag tag-blue">1.095 Chunks Y Tế</span>
        </div>
        <h1 class="hero-title">Thiết Lập & Nạp CSDL Clinical RAG Cục Bộ</h1>
        <p class="hero-desc">Tài liệu kỹ thuật hướng dẫn chi tiết từng bước cho thành viên nhóm CareFlow thiết lập PostgreSQL Vector, nạp 100% phác đồ Bộ Y Tế & Dược thư Quốc gia để chạy và kiểm thử tính năng RAG.</p>
      </div>

      <!-- Quick Start Card -->
      <div class="quick-start-box" id="quick-start">
        <div class="quick-start-header">
          <div class="quick-start-title">
            <span>⚡ Khởi Động Nhanh Trong 10 Giây</span>
          </div>
          <span class="tag tag-teal" style="background: rgba(255,255,255,0.2); color: white;">Cách Tối Ưu Nhất</span>
        </div>
        <div class="quick-start-sub">Mở terminal tại thư mục gốc dự án và copy 2 lệnh dưới đây:</div>
        
        <div class="code-container" style="margin: 0; background: #071527;">
          <div class="code-header" style="background: #0D233F;">
            <span class="code-lang">BASH — LỆNH 1: BẬT CONTAINER DOCKER PGVECTOR</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker start careflow-ai-postgres 2>/dev/null || docker run -d --name careflow-ai-postgres -p 5433:5432 -e POSTGRES_USER=careflow -e POSTGRES_PASSWORD=careflow -e POSTGRES_DB=careflow_ai --restart unless-stopped pgvector/pgvector:pg16</code></pre>
        </div>

        <div class="code-container" style="margin-top: 12px; margin-bottom: 0; background: #071527;">
          <div class="code-header" style="background: #0D233F;">
            <span class="code-lang">BASH — LỆNH 2: NẠP FILE DUMP (1.095 CHUNKS & VECTOR 768 CHIỀU)</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker exec -i careflow-ai-postgres psql -U careflow -d careflow_ai &lt; scripts/careflow_ai_schema_and_data.sql</code></pre>
        </div>
      </div>

      <!-- Section 1 -->
      <section class="guide-section" id="git-rules">
        <div class="section-header">
          <span class="step-number">1</span>
          <h2 class="section-title">Quy Định Git: File .md và .csv Có Được Push Không?</h2>
        </div>
        <div class="alert alert-important">
          <span class="alert-icon">💡</span>
          <div>
            <strong>KẾT LUẬN: HOÀN TOÀN CÓ THỂ VÀ BẮT BUỘC NÊN PUSH!</strong><br>
            Toàn bộ tri thức y khoa đã được chuẩn hóa định dạng văn bản nhẹ để toàn bộ nhóm kéo về là nạp được ngay.
          </div>
        </div>

        <table style="margin-top: 16px;">
          <thead>
            <tr>
              <th style="width: 50%;">Tệp ĐƯỢC PHÉP & CẦN THIẾT push lên Git</th>
              <th style="width: 50%;">Tệp BỊ CHẶN TUYỆT ĐỐI bởi .gitignore</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                • Phác đồ Markdown: <code>docs/rag-docs/treatment-protocol/markdown/*.md</code><br>
                • Danh mục DDI: <code>docs/rag-docs/ddi/*.csv</code> (701 cặp tương tác)<br>
                • Thuốc generic & ICD-10: <code>docs/rag-docs/drug-formulary/*.csv</code><br>
                • SQL Dump: <code>scripts/careflow_ai_schema_and_data.sql</code>
              </td>
              <td>
                • File nhị phân nặng: <code>docs/rag-docs/**/*.pdf</code>, <code>*.xlsx</code><br>
                • Cache vector tạm: <code>scripts/.rag_embeddings_cache*.json</code><br>
                • File bí mật / API key: <code>.env</code>, <code>careflow-ai-service/.env</code> (chứa <code>GEMINI_API_KEY</code>)
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Section 2 -->
      <section class="guide-section" id="prerequisites">
        <div class="section-header">
          <span class="step-number">2</span>
          <h2 class="section-title">Yêu Cầu Môi Trường (Prerequisites)</h2>
        </div>
        <p>Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã cài đặt các công cụ sau:</p>
        <ul style="padding-left: 20px; color: var(--slate-700); margin-bottom: 14px;">
          <li><strong>Docker Desktop / Docker Engine:</strong> Đang chạy để tạo container PostgreSQL.</li>
          <li><strong>Java 21 & Maven 3.9+:</strong> Dùng để biên dịch và chạy microservice AI.</li>
          <li><strong>PostgreSQL Client (<code>psql</code>) hoặc DBeaver:</strong> Tùy chọn để xem bảng trực quan.</li>
          <li><strong>Python 3.10+ (Tùy chọn):</strong> Chỉ cần nếu bạn muốn tự chạy lại script Ingestion từ file nguồn Markdown/CSV.</li>
        </ul>
      </section>

      <!-- Section 3 -->
      <section class="guide-section" id="step-1">
        <div class="section-header">
          <span class="step-number">3</span>
          <h2 class="section-title">Bước 1: Khởi Tạo CSDL Vector pgvector (Docker Port 5433)</h2>
        </div>
        <p>CareFlow AI Service sử dụng CSDL <strong>PostgreSQL 16 tích hợp extension <code>pgvector</code></strong>, chạy riêng biệt trên cổng <strong><code>5433</code></strong> để không xung đột với CSDL Core 5432.</p>
        
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker run -d \
  --name careflow-ai-postgres \
  -p 5433:5432 \
  -e POSTGRES_USER=careflow \
  -e POSTGRES_PASSWORD=careflow \
  -e POSTGRES_DB=careflow_ai \
  --restart unless-stopped \
  pgvector/pgvector:pg16</code></pre>
        </div>

        <p>Kiểm tra container đang chạy:</p>
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker ps --filter "name=careflow-ai-postgres"</code></pre>
        </div>
      </section>

      <!-- Section 4 -->
      <section class="guide-section" id="step-2">
        <div class="section-header">
          <span class="step-number">4</span>
          <h2 class="section-title">Bước 2: Nạp Dữ Liệu Vector (Chọn 1 Trong 2 Cách)</h2>
        </div>

        <div class="option-grid">
          <!-- Option A -->
          <div class="option-card recommended">
            <span class="option-badge badge-rec">Khuyên Dùng (Khuyến nghị)</span>
            <h4>Cách A: Restore từ file SQL Dump</h4>
            <p style="font-size: 13.5px;">Nạp ngay lập tức snapshot có sẵn 1.095 chunks và vector nhúng 768 chiều.</p>
            <ul>
              <li>⚡ Tốc độ cực nhanh: <strong>3 – 5 giây</strong></li>
              <li>🆓 <strong>Không cần API Key Gemini</strong>, không lo hết Quota</li>
              <li>🎯 100% chính xác với môi trường chuẩn</li>
            </ul>
          </div>

          <!-- Option B -->
          <div class="option-card">
            <span class="option-badge badge-alt">Tùy Chọn Mở Rộng</span>
            <h4>Cách B: Ingestion qua Script Python</h4>
            <p style="font-size: 13.5px;">Tự bóc tách lại từ file Markdown phác đồ và gọi Gemini Embedding API.</p>
            <ul>
              <li>⚙️ Dùng khi bạn chỉnh sửa hoặc bổ sung phác đồ mới</li>
              <li>🔑 Yêu cầu <code>GEMINI_API_KEY</code></li>
              <li>⏳ Mất khoảng 1 – 2 phút xử lý</li>
            </ul>
          </div>
        </div>

        <h4 style="margin: 20px 0 8px; color: var(--slate-900);">Thực hiện Cách A (Khuyên dùng):</h4>
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH — CHẠY TỪ THƯ MỤC GỐC CAREFLOW</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker exec -i careflow-ai-postgres psql -U careflow -d careflow_ai &lt; scripts/careflow_ai_schema_and_data.sql</code></pre>
        </div>

        <div class="alert alert-tip">
          <span class="alert-icon">✨</span>
          <div>
            <strong>Lợi ích:</strong> File <code>scripts/careflow_ai_schema_and_data.sql</code> đã bao gồm bảng cha <code>clinical_guideline_documents</code>, bảng con <code>clinical_guideline_chunks</code>, chỉ mục HNSW Cosine distance và GIN full-text BM25.
          </div>
        </div>

        <details style="margin-top: 14px; background: var(--slate-50); border: 1px solid var(--slate-200); border-radius: var(--radius-sm); padding: 12px 16px;">
          <summary style="font-weight: 600; font-size: 13.5px; cursor: pointer; color: var(--primary);">👉 Bấm vào đây nếu muốn xem chi tiết Cách B (Chạy Ingestion Python)</summary>
          <div style="margin-top: 12px; font-size: 13.5px;">
            <p>1. Cài đặt thư viện Python:</p>
            <div class="code-container">
              <div class="code-header"><span class="code-lang">BASH</span><button class="btn-copy" onclick="copyCode(this)">Sao chép</button></div>
              <pre><code>pip install requests psycopg2-binary pgvector google-genai</code></pre>
            </div>
            <p>2. Cấu hình API Key trong file <code>careflow-ai-service/.env</code>:</p>
            <div class="code-container">
              <div class="code-header"><span class="code-lang">ENV</span><button class="btn-copy" onclick="copyCode(this)">Sao chép</button></div>
              <pre><code>GEMINI_API_KEY=AIzaSy... (API Key của bạn)</code></pre>
            </div>
            <p>3. Chạy script Ingestion:</p>
            <div class="code-container">
              <div class="code-header"><span class="code-lang">BASH</span><button class="btn-copy" onclick="copyCode(this)">Sao chép</button></div>
              <pre><code>python3 scripts/ingest_clinical_rag.py</code></pre>
            </div>
          </div>
        </details>
      </section>

      <!-- Section 5 -->
      <section class="guide-section" id="step-3">
        <div class="section-header">
          <span class="step-number">5</span>
          <h2 class="section-title">Bước 3: Xác Thực Dữ Liệu Đã Nạp Thành Công</h2>
        </div>
        <p>Kiểm tra số lượng bản ghi tài liệu cha, số chunks và số vector nhúng:</p>
        
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker exec -it careflow-ai-postgres psql -U careflow -d careflow_ai -c "
SELECT 
    (SELECT COUNT(*) FROM clinical_guideline_documents) AS total_documents,
    (SELECT COUNT(*) FROM clinical_guideline_chunks) AS total_chunks,
    (SELECT COUNT(*) FROM clinical_guideline_chunks WHERE embedding IS NOT NULL) AS chunks_with_vector;
"</code></pre>
        </div>

        <p><strong>Kết quả kỳ vọng chuẩn xác:</strong></p>
        <div class="code-container" style="background: #061A23;">
          <div class="code-header" style="background: #0C2B3A;">
            <span class="code-lang">KẾT QUẢ MONG ĐỢI</span>
          </div>
          <pre><code> total_documents | total_chunks | chunks_with_vector 
-----------------+--------------+--------------------
               4 |         1095 |               1095</code></pre>
        </div>

        <p style="margin-top: 14px;">Thử nghiệm một câu truy vấn tìm kiếm kiểm tra trực tiếp:</p>
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>docker exec -it careflow-ai-postgres psql -U careflow -d careflow_ai -c "
SELECT document_id, section_title 
FROM clinical_guideline_chunks 
WHERE section_title ILIKE '%đái tháo đường%' 
LIMIT 3;
"</code></pre>
        </div>
      </section>

      <!-- Section 6 -->
      <section class="guide-section" id="step-4">
        <div class="section-header">
          <span class="step-number">6</span>
          <h2 class="section-title">Bước 4: Khởi Chạy & Kiểm Thử Trên Service</h2>
        </div>
        
        <p><strong>1. Chạy bộ kiểm thử tự động (Unit & Integration Tests):</strong></p>
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>mvn -pl careflow-ai-service test</code></pre>
        </div>
        <p style="font-size: 13.5px; color: var(--emerald-600); font-weight: 600;">✓ Xác nhận 13/13 tests RAG & Hybrid Search PASS 100%.</p>

        <p style="margin-top: 16px;"><strong>2. Khởi chạy toàn bộ hệ sinh thái dịch vụ AI:</strong></p>
        <div class="code-container">
          <div class="code-header">
            <span class="code-lang">BASH</span>
            <button class="btn-copy" onclick="copyCode(this)">Sao chép</button>
          </div>
          <pre><code>./start-local.sh ai</code></pre>
        </div>

        <p><strong>3. Trải nghiệm trên giao diện Bác sĩ:</strong></p>
        <ul style="padding-left: 20px; font-size: 14px; color: var(--slate-700);">
          <li>Truy cập: <a href="http://localhost:3000" target="_blank" style="color: var(--primary); font-weight: 600;">http://localhost:3000</a> (hoặc 5173).</li>
          <li>Đăng nhập tài khoản Bác sĩ mẫu: <code>doctor@careflow.com</code> / <code>Password123@</code>.</li>
          <li>Mở một ca khám bệnh bất kỳ ➔ Nhấp vào tab <strong>"Trợ lý Lâm sàng (Phác đồ BYT)"</strong> để xem các gợi ý phác đồ và cảnh báo tương tác thuốc DDI từ Dược thư Quốc gia.</li>
        </ul>
      </section>

      <!-- Section 7 -->
      <section class="guide-section" id="troubleshooting">
        <div class="section-header">
          <span class="step-number">7</span>
          <h2 class="section-title">Khắc Phục Sự Cố Thường Gặp (Troubleshooting)</h2>
        </div>

        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th style="width: 32%;">Lỗi gặp phải</th>
                <th style="width: 28%;">Nguyên nhân</th>
                <th style="width: 40%;">Cách khắc phục</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>Bind for 0.0.0.0:5433 failed: port is already allocated</code></td>
                <td>Cổng 5433 đã bị container hoặc tiến trình khác chiếm dụng.</td>
                <td>Kiểm tra: <code>ss -tuln | grep 5433</code> hoặc dừng container cũ: <code>docker stop &lt;container_id&gt;</code>.</td>
              </tr>
              <tr>
                <td><code>psql: error: connection to server ... failed</code></td>
                <td>Container <code>careflow-ai-postgres</code> chưa được bật.</td>
                <td>Chạy: <code>docker start careflow-ai-postgres</code>.</td>
              </tr>
              <tr>
                <td><code>Extension "vector" does not exist</code></td>
                <td>Dùng sai image PostgreSQL thường thay vì pgvector.</td>
                <td>Tạo lại container với đúng image: <code>pgvector/pgvector:pg16</code>.</td>
              </tr>
              <tr>
                <td><code>404 Not Found on /api/ai/...</code></td>
                <td>Gọi sai prefix đường dẫn API.</td>
                <td>Đảm bảo prefix endpoint luôn bắt đầu bằng <code>/api/ai/...</code> (ví dụ: <code>/api/ai/clinical-suggestions</code>).</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Footer -->
      <div style="border-top: 1px solid var(--slate-200); padding-top: 20px; font-size: 13px; color: var(--slate-500); display: flex; justify-content: space-between;">
        <span>CareFlow — Clinical RAG & AI Ecosystem</span>
        <span>Cập nhật: 05/11/2026</span>
      </div>

    </main>
  </div>

  <script>
    function copyCode(btn) {
      const pre = btn.closest('.code-container').querySelector('pre code');
      if (!pre) return;
      
      const text = pre.innerText;
      navigator.clipboard.writeText(text).then(() => {
        const originalText = btn.innerText;
        btn.innerText = '✓ Đã chép!';
        btn.classList.add('copied');
        setTimeout(() => {
          btn.innerText = originalText;
          btn.classList.remove('copied');
        }, 2000);
      }).catch(err => {
        console.error('Không thể sao chép:', err);
      });
    }
  </script>
</body>
</html>
"""

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_content)

    print(f"Generated standalone HTML successfully: {output_path}")

if __name__ == "__main__":
    generate_manual_rag_html()
