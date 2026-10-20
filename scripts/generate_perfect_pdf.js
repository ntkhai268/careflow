const fs = require('fs');
const path = require('path');
const playwrightPath = path.resolve(__dirname, '../frontend/doctor-web/node_modules/playwright');
const { chromium } = require(playwrightPath);

async function generatePdf() {
  console.log('🚀 Đang khởi động Playwright để xuất PDF hoàn hảo...');

  const mdPath = path.resolve('docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md');
  const htmlPath = path.resolve('docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.html');
  const pdfPath = path.resolve('docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.pdf');

  let rawMd = fs.readFileSync(mdPath, 'utf8');

  // Xử lý các Alert GitHub ([!NOTE], [!IMPORTANT], etc.) CHUẨN XÁC, KHÔNG PHÁ VỠ TABLE
  // Đảm bảo luôn có 2 dòng trống (\n\n) trước và sau mỗi callout để marked.js nhận diện table ngay sau đó!
  rawMd = rawMd.replace(/> \[!NOTE\]\s*\n((?:> .*\n?)+)/g, (match, p1) => {
    const text = p1.replace(/^> /gm, '');
    return `\n\n<div class="callout callout-note"><strong>ℹ️ LƯU Ý:</strong><br/>${text}</div>\n\n`;
  });

  rawMd = rawMd.replace(/> \[!IMPORTANT\]\s*\n((?:> .*\n?)+)/g, (match, p1) => {
    const text = p1.replace(/^> /gm, '');
    return `\n\n<div class="callout callout-important"><strong>⚠️ QUAN TRỌNG:</strong><br/>${text}</div>\n\n`;
  });

  rawMd = rawMd.replace(/> \[!WARNING\]\s*\n((?:> .*\n?)+)/g, (match, p1) => {
    const text = p1.replace(/^> /gm, '');
    return `\n\n<div class="callout callout-warning"><strong>🚨 CẢNH BÁO:</strong><br/>${text}</div>\n\n`;
  });

  rawMd = rawMd.replace(/> \[!TIP\]\s*\n((?:> .*\n?)+)/g, (match, p1) => {
    const text = p1.replace(/^> /gm, '');
    return `\n\n<div class="callout callout-tip"><strong>💡 GỢI Ý:</strong><br/>${text}</div>\n\n`;
  });

  // Tạo file HTML hoàn chỉnh với CSS in ấn A4 chuẩn mực
  const htmlTemplate = `<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>CareFlow - Kế Hoạch Triển Khai Clinical RAG & AI Lâm Sàng</title>
  
  <!-- High-contrast highlight.js -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css">
  <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/marked@12.0.0/marked.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"></script>

  <style>
    :root {
      --bg-page: #F1F5F9;
      --bg-card: #FFFFFF;
      --text-main: #0F172A;
      --text-muted: #334155;
      --text-heading: #020617;
      --border-color: #CBD5E1;
      --border-table: #94A3B8;
      --code-bg: #1E293B;
      --code-text: #F8FAFC;
      --inline-code-bg: #E2E8F0;
      --inline-code-text: #0F172A;
      --callout-bg: #F8FAFC;
      --table-header-bg: #E2E8F0;
      --table-stripe: #F8FAFC;
      --font-scale: 15px;
    }

    [data-theme="dark"] {
      --bg-page: #090D16;
      --bg-card: #0F172A;
      --text-main: #F1F5F9;
      --text-muted: #CBD5E1;
      --text-heading: #FFFFFF;
      --border-color: #334155;
      --border-table: #475569;
      --code-bg: #020617;
      --code-text: #38BDF8;
      --inline-code-bg: #1E293B;
      --inline-code-text: #38BDF8;
      --callout-bg: #1E293B;
      --table-header-bg: #1E293B;
      --table-stripe: #131D31;
    }

    * {
      box-sizing: border-box;
    }

    body {
      background-color: var(--bg-page);
      color: var(--text-main);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      font-size: var(--font-scale);
      line-height: 1.7;
      margin: 0;
      padding: 0;
      transition: background-color 0.2s, color 0.2s;
    }

    .toolbar {
      position: sticky;
      top: 0;
      z-index: 1000;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(8px);
      border-bottom: 2px solid var(--border-color);
      padding: 12px 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      box-shadow: 0 2px 4px rgba(0,0,0,0.04);
    }
    [data-theme="dark"] .toolbar {
      background: rgba(15, 23, 42, 0.95);
      border-bottom-color: #334155;
    }

    .toolbar-title {
      font-weight: 700;
      font-size: 16px;
      color: var(--text-heading);
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .toolbar-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      font-size: 13px;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid var(--border-color);
      background: var(--bg-card);
      color: var(--text-main);
      cursor: pointer;
      user-select: none;
      transition: all 0.15s;
    }
    .btn:hover {
      background: #E2E8F0;
    }
    [data-theme="dark"] .btn:hover {
      background: #1E293B;
    }
    .btn-primary {
      background: #1D4ED8 !important;
      color: #FFFFFF !important;
      border-color: #1D4ED8 !important;
    }
    .btn-primary:hover {
      background: #1E40AF !important;
    }

    .document-container {
      max-width: 980px;
      margin: 24px auto;
      padding: 48px;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }

    /* Headings */
    h1, h2, h3, h4, h5, h6 {
      color: var(--text-heading) !important;
      font-weight: 800 !important;
      line-height: 1.35;
      margin-top: 1.8em;
      margin-bottom: 0.6em;
    }
    h1 {
      font-size: 2.1em;
      border-bottom: 3px solid var(--border-color);
      padding-bottom: 0.3em;
      margin-top: 0.5em;
    }
    h2 {
      font-size: 1.6em;
      border-bottom: 2px solid var(--border-color);
      padding-bottom: 0.25em;
    }
    h3 { font-size: 1.3em; }
    h4 { font-size: 1.1em; }

    p, li, dl, dd {
      color: var(--text-main) !important;
      font-size: var(--font-scale);
      line-height: 1.75;
    }

    strong, b {
      color: var(--text-heading) !important;
      font-weight: 700;
    }

    a {
      color: #2563EB !important;
      text-decoration: underline;
      font-weight: 500;
    }
    [data-theme="dark"] a {
      color: #60A5FA !important;
    }

    /* Table Fixes: Tương phản cao, viền rõ */
    table {
      width: 100% !important;
      border-collapse: collapse !important;
      margin: 24px 0 !important;
      font-size: 13.5px !important;
      border: 2px solid var(--border-table) !important;
      page-break-inside: avoid !important;
    }
    th, td {
      border: 1px solid var(--border-table) !important;
      padding: 10px 14px !important;
      text-align: left !important;
      line-height: 1.6 !important;
    }
    th {
      background-color: var(--table-header-bg) !important;
      color: var(--text-heading) !important;
      font-weight: 800 !important;
    }
    td {
      color: var(--text-main) !important;
      background-color: var(--bg-card) !important;
    }
    tr:nth-child(even) td {
      background-color: var(--table-stripe) !important;
    }

    /* Code Blocks */
    pre {
      background-color: var(--code-bg) !important;
      color: var(--code-text) !important;
      border: 1px solid #475569 !important;
      border-radius: 8px !important;
      padding: 16px 20px !important;
      overflow-x: auto !important;
      white-space: pre-wrap !important;
      word-break: break-word !important;
      page-break-inside: avoid !important;
      margin: 20px 0 !important;
    }
    pre code {
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Courier, monospace !important;
      font-size: 12.5px !important;
      line-height: 1.5 !important;
      color: var(--code-text) !important;
      background: transparent !important;
      padding: 0 !important;
    }
    code {
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Courier, monospace !important;
      font-size: 0.9em !important;
      background-color: var(--inline-code-bg) !important;
      color: var(--inline-code-text) !important;
      padding: 2px 6px !important;
      border-radius: 4px !important;
      font-weight: 600 !important;
    }

    /* Callouts */
    .callout {
      padding: 14px 18px;
      margin: 20px 0;
      border-left: 6px solid #64748B;
      border-radius: 6px;
      background-color: var(--callout-bg);
      color: var(--text-main) !important;
      page-break-inside: avoid !important;
    }
    .callout strong {
      color: var(--text-heading) !important;
    }
    .callout-note {
      border-left-color: #2563EB;
      background-color: #EFF6FF;
    }
    .callout-important {
      border-left-color: #7C3AED;
      background-color: #F5F3FF;
    }
    .callout-warning {
      border-left-color: #D97706;
      background-color: #FFFBEB;
    }
    .callout-tip {
      border-left-color: #059669;
      background-color: #ECFDF5;
    }
    [data-theme="dark"] .callout-note { background-color: #1E293B; border-left-color: #3B82F6; }
    [data-theme="dark"] .callout-important { background-color: #1E293B; border-left-color: #8B5CF6; }
    [data-theme="dark"] .callout-warning { background-color: #1E293B; border-left-color: #F59E0B; }
    [data-theme="dark"] .callout-tip { background-color: #1E293B; border-left-color: #10B981; }

    /* Mermaid */
    .mermaid {
      background: #FFFFFF !important;
      border: 2px solid var(--border-color);
      border-radius: 8px;
      padding: 20px;
      margin: 24px 0;
      text-align: center;
      page-break-inside: avoid !important;
    }
    [data-theme="dark"] .mermaid {
      background: #1E293B !important;
      border-color: #475569;
    }
    .mermaid svg {
      max-width: 100% !important;
      height: auto !important;
      display: inline-block;
    }

    /* Print Specific */
    @media print {
      body {
        background: white !important;
        color: #000000 !important;
      }
      .no-print {
        display: none !important;
      }
      .document-container {
        border: none !important;
        box-shadow: none !important;
        max-width: 100% !important;
        margin: 0 !important;
        padding: 0 !important;
      }
      h1, h2, h3, h4, p, li, td, th {
        color: #000000 !important;
      }
      pre {
        background-color: #F8FAFC !important;
        color: #000000 !important;
        border: 1px solid #94A3B8 !important;
      }
      pre code {
        color: #000000 !important;
      }
      @page {
        size: A4;
        margin: 15mm 12mm 15mm 12mm;
      }
      * {
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
    }
  </style>
</head>
<body data-theme="light">

  <div class="toolbar no-print">
    <div class="toolbar-title">
      <span>📄 CareFlow Clinical RAG & AI Plan</span>
      <span style="font-weight: 400; font-size: 13px; color: var(--text-muted);">• Tài liệu kỹ thuật phân hệ AI</span>
    </div>
    <div class="toolbar-actions">
      <button class="btn" onclick="toggleTheme()" id="themeBtn" title="Chuyển chế độ Sáng / Tối">
        🌓 Đổi Giao Diện (Sáng/Tối)
      </button>
      <button class="btn" onclick="changeFontSize(-1)" title="Giảm cỡ chữ">A-</button>
      <button class="btn" onclick="changeFontSize(1)" title="Tăng cỡ chữ">A+</button>
      <button class="btn btn-primary" onclick="window.print()" title="In ra giấy hoặc lưu PDF">
        🖨️ In / Xuất PDF (Ctrl + P)
      </button>
    </div>
  </div>

  <article class="document-container" id="content">
    <div style="text-align: center; padding: 40px; color: #64748B;">
      <h3>⏳ Đang tải nội dung tài liệu...</h3>
    </div>
  </article>

  <script>
    const RAW_MD = ${JSON.stringify(rawMd)};

    mermaid.initialize({
      startOnLoad: false,
      theme: 'neutral',
      themeVariables: {
        fontSize: '12px',
        primaryColor: '#F8FAFC',
        primaryBorderColor: '#94A3B8',
        primaryTextColor: '#0F172A',
        lineColor: '#64748B',
        clusterBkg: '#F1F5F9',
        clusterBorder: '#CBD5E1'
      },
      securityLevel: 'loose'
    });

    const renderer = new marked.Renderer();
    const originalCodeRenderer = renderer.code.bind(renderer);
    renderer.code = function(code, language) {
      if (typeof code === 'object') {
        language = code.lang;
        code = code.text;
      }
      if (language === 'mermaid') {
        return '<div class="mermaid">' + code + '</div>';
      }
      return originalCodeRenderer(code, language);
    };

    marked.setOptions({
      renderer: renderer,
      gfm: true,
      breaks: false
    });

    let currentTheme = 'light';
    let currentScale = 15;

    function toggleTheme() {
      currentTheme = currentTheme === 'light' ? 'dark' : 'light';
      document.body.setAttribute('data-theme', currentTheme);
      const btn = document.getElementById('themeBtn');
      if (btn) {
        btn.textContent = currentTheme === 'light' ? '🌓 Đổi Giao Diện (Tối)' : '☀️ Đổi Giao Diện (Sáng)';
      }
    }

    function changeFontSize(delta) {
      currentScale = Math.max(12, Math.min(22, currentScale + delta));
      document.documentElement.style.setProperty('--font-scale', currentScale + 'px');
    }

    async function init() {
      const container = document.getElementById('content');
      container.innerHTML = marked.parse(RAW_MD);

      // Kích hoạt Mermaid và render tất cả các block
      await mermaid.run({
        nodes: document.querySelectorAll('.mermaid')
      });

      // Highlight code blocks
      document.querySelectorAll('pre code').forEach((el) => {
        hljs.highlightElement(el);
      });

      // Đánh dấu hoàn tất render
      document.body.setAttribute('data-rendered', 'true');
      console.log('✅ Mermaid, code highlight and DOM rendering complete!');
    }

    window.addEventListener('DOMContentLoaded', init);
  </script>
</body>
</html>`;

  fs.writeFileSync(htmlPath, htmlTemplate, 'utf8');
  console.log('✅ Đã cập nhật file HTML độc lập:', htmlPath);

  // Khởi động trình duyệt Chromium qua Playwright
  const browser = await chromium.launch({
    executablePath: '/usr/bin/google-chrome',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const context = await browser.newContext({
    colorScheme: 'light'
  });

  const page = await context.newPage();
  await page.emulateMedia({ colorScheme: 'light', media: 'screen' });
  
  // Mở file HTML
  console.log('⏳ Đang mở trang HTML và chờ kết xuất toàn bộ sơ đồ Mermaid...');
  await page.goto(`file://${htmlPath}`, { waitUntil: 'networkidle' });

  // Đợi cho đến khi attribute data-rendered="true" xuất hiện
  await page.waitForSelector('body[data-rendered="true"]', { timeout: 30000 });

  // Đợi thêm 1.5 giây để tất cả SVG layouts ổn định kích thước
  await page.waitForTimeout(1500);

  // Kiểm tra số lượng SVG Mermaid đã render
  const svgCount = await page.evaluate(() => document.querySelectorAll('.mermaid svg').length);
  console.log(`📊 Số lượng sơ đồ Mermaid render thành công: ${svgCount} sơ đồ`);

  // Chuyển sang media print với light mode tuyệt đối trước khi in
  await page.emulateMedia({ colorScheme: 'light', media: 'print' });

  // Xuất ra PDF chuẩn A4
  console.log('📄 Đang kết xuất file PDF chuẩn A4...');
  await page.pdf({
    path: pdfPath,
    format: 'A4',
    printBackground: true,
    margin: {
      top: '15mm',
      bottom: '15mm',
      left: '12mm',
      right: '12mm'
    }
  });

  await browser.close();
  const stats = fs.statSync(pdfPath);
  console.log(`🎉 HOÀN TẤT XUẤT FILE PDF!`);
  console.log(`   Đường dẫn: ${pdfPath}`);
  console.log(`   Dung lượng: ${(stats.size / (1024 * 1024)).toFixed(2)} MB`);
}

generatePdf().catch(err => {
  console.error('❌ Lỗi khi xuất PDF:', err);
  process.exit(1);
});
