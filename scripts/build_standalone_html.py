import os
import json

def build_standalone_html():
    md_file = "docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md"
    html_file = "docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.html"
    
    with open(md_file, "r", encoding="utf-8") as f:
        md_text = f.read()

    # Pre-render callout alerts for cleaner display
    # Replace > [!NOTE] etc. with HTML equivalents
    import re
    
    # We can embed the markdown as a JSON string so there's zero chance of syntax/escaping error
    json_md = json.dumps(md_text)

    html_content = f"""<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>CareFlow - Kế Hoạch Triển Khai Clinical RAG & AI Lâm Sàng</title>
  
  <!-- GitHub Markdown CSS -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.1/github-markdown.min.css">
  
  <!-- Highlight.js for Code Highlighting -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
  <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>

  <!-- Marked.js for Markdown Parsing -->
  <script src="https://cdn.jsdelivr.net/npm/marked@12.0.0/marked.min.js"></script>

  <!-- Mermaid.js for Rendering Diagrams -->
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"></script>

  <style>
    :root {{
      --color-primary: #2563EB;
      --color-primary-hover: #1D4ED8;
    }}
    
    body {{
      background-color: #F8FAFC;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      margin: 0;
      padding: 0;
      color: #0F172A;
    }}

    /* Floating Print Action Bar */
    .print-bar {{
      position: sticky;
      top: 0;
      z-index: 1000;
      background: rgba(255, 255, 255, 0.98);
      backdrop-filter: blur(8px);
      border-bottom: 1px solid #E2E8F0;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
      padding: 12px 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }}

    .print-bar .title-info {{
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 14px;
      color: #475569;
    }}

    .print-bar .btn-print {{
      background-color: var(--color-primary);
      color: white;
      border: none;
      border-radius: 6px;
      padding: 10px 20px;
      font-weight: 600;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: background-color 0.2s;
    }}

    .print-bar .btn-print:hover {{
      background-color: var(--color-primary-hover);
    }}

    .markdown-body {{
      box-sizing: border-box;
      min-width: 200px;
      max-width: 960px;
      margin: 30px auto;
      padding: 45px;
      background: white;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }}

    /* Mermaid diagrams container */
    .mermaid {{
      background: #F8FAFC !important;
      border: 1px solid #CBD5E1;
      border-radius: 8px;
      padding: 16px;
      margin: 20px 0;
      text-align: center;
    }}

    /* GitHub Callouts */
    .callout {{
      padding: 12px 16px;
      margin: 16px 0;
      border-left: 4px solid #CBD5E1;
      border-radius: 4px;
      background-color: #F8FAFC;
    }}
    .callout-note {{
      border-left-color: #3B82F6;
      background-color: #EFF6FF;
    }}
    .callout-important {{
      border-left-color: #8B5CF6;
      background-color: #F5F3FF;
    }}
    .callout-warning {{
      border-left-color: #F59E0B;
      background-color: #FFFBEB;
    }}

    /* Print Specific Styling */
    @media print {{
      body {{
        background: white;
      }}
      .no-print {{
        display: none !important;
      }}
      .markdown-body {{
        border: none;
        box-shadow: none;
        max-width: 100%;
        margin: 0;
        padding: 0;
      }}
      @page {{
        size: A4;
        margin: 15mm 12mm 15mm 12mm;
      }}
      .mermaid, table, pre, blockquote, .callout {{
        page-break-inside: avoid !important;
        break-inside: avoid !important;
      }}
      h1, h2, h3, h4 {{
        page-break-after: avoid !important;
        break-after: avoid !important;
      }}
      * {{
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }}
    }}
  </style>
</head>
<body>

  <!-- Floating Print Header -->
  <div class="print-bar no-print">
    <div class="title-info">
      <strong style="color: #0F172A;">📄 CareFlow Clinical RAG & AI Plan</strong>
      <span>• Bảo toàn 100% sơ đồ Mermaid & cấu trúc A4</span>
    </div>
    <div style="display: flex; gap: 10px;">
      <button class="btn-print" onclick="window.print()">
        <svg width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
          <path d="M2.5 8a.5.5 0 1 0 0-1 .5.5 0 0 0 0 1z"/>
          <path d="M5 1a2 2 0 0 0-2 2v2H2a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h1v1a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-1h1a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-1V3a2 2 0 0 0-2-2H5zM4 3a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2H4V3zm1 5a2 2 0 0 0-2 2v1H2a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1v-1a2 2 0 0 0-2-2H5zm7 2v3a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1z"/>
        </svg>
        Xuất PDF / In tài liệu (Ctrl + P)
      </button>
    </div>
  </div>

  <!-- Markdown Container -->
  <article class="markdown-body" id="content">
    <div style="text-align: center; padding: 40px; color: #64748B;">
      <h3>⏳ Đang kết xuất tài liệu...</h3>
    </div>
  </article>

  <script>
    // Embedded Raw Markdown - 100% Offline, ZERO FETCH, ZERO CORS!
    const RAW_MARKDOWN = {json_md};

    // Configure Mermaid
    mermaid.initialize({{
      startOnLoad: false,
      theme: 'neutral',
      themeVariables: {{
        fontSize: '12px',
        primaryColor: '#F8FAFC',
        primaryBorderColor: '#94A3B8',
        primaryTextColor: '#0F172A',
        lineColor: '#64748B',
        clusterBkg: '#F1F5F9',
        clusterBorder: '#CBD5E1'
      }},
      securityLevel: 'loose'
    }});

    const renderer = new marked.Renderer();
    const originalCodeRenderer = renderer.code.bind(renderer);
    renderer.code = function(code, language) {{
      if (typeof code === 'object') {{
        language = code.lang;
        code = code.text;
      }}
      if (language === 'mermaid') {{
        return '<div class="mermaid">' + code + '</div>';
      }}
      return originalCodeRenderer(code, language);
    }};

    marked.setOptions({{
      renderer: renderer,
      highlight: function(code, lang) {{
        const language = highlight.getLanguage(lang) ? lang : 'plaintext';
        return hljs.highlight(code, {{ language }}).value;
      }},
      gfm: true,
      breaks: false
    }});

    function renderPage() {{
      try {{
        let md = RAW_MARKDOWN;
        md = md
          .replace(/> \\[\\!NOTE\\]\\s*\\n((?:> .*\\n?)+)/g, '<div class="callout callout-note"><strong>ℹ️ LƯU Ý:</strong><br/>$1</div>')
          .replace(/> \\[\\!IMPORTANT\\]\\s*\\n((?:> .*\\n?)+)/g, '<div class="callout callout-important"><strong>⚠️ QUAN TRỌNG:</strong><br/>$1</div>')
          .replace(/> \\[\\!WARNING\\]\\s*\\n((?:> .*\\n?)+)/g, '<div class="callout callout-warning"><strong>🚨 CẢNH BÁO:</strong><br/>$1</div>')
          .replace(/^> /gm, '');

        const container = document.getElementById('content');
        container.innerHTML = marked.parse(md);

        mermaid.run({{
          nodes: document.querySelectorAll('.mermaid')
        }}).then(() => {{
          document.body.setAttribute('data-rendered', 'true');
        }});
      }} catch (e) {{
        console.error('Lỗi render:', e);
      }}
    }}

    window.addEventListener('DOMContentLoaded', renderPage);
  </script>
</body>
</html>
"""

    with open(html_file, "w", encoding="utf-8") as f:
        f.write(html_content)

    print(f"Generated standalone HTML successfully: {html_file}")

if __name__ == "__main__":
    build_standalone_html()
