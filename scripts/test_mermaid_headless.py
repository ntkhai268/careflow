import re
import json
import subprocess
import time

with open('docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md', 'r', encoding='utf-8') as f:
    text = f.read()

blocks = re.findall(r'```mermaid\s*\n(.*?)```', text, re.DOTALL)

test_html = """<!DOCTYPE html>
<html>
<head>
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"></script>
</head>
<body>
<div id="logs"></div>
<script>
mermaid.initialize({ startOnLoad: false });
const diagrams = """ + json.dumps(blocks) + """;

async function testAll() {
  const logDiv = document.getElementById('logs');
  for (let i = 0; i < diagrams.length; i++) {
    const d = diagrams[i];
    try {
      const { svg } = await mermaid.render('mermaid-' + i, d);
      logDiv.innerHTML += '<p>Diagram ' + (i+1) + ': SUCCESS (' + svg.length + ' chars)</p>';
    } catch (err) {
      logDiv.innerHTML += '<p style="color:red">Diagram ' + (i+1) + ': ERROR - ' + err.message + '</p>';
    }
  }
  document.body.setAttribute('data-done', 'true');
}
window.onload = testAll;
</script>
</body>
</html>"""

with open('/tmp/test_mermaid.html', 'w', encoding='utf-8') as f:
    f.write(test_html)

# Run in headless chrome and dump DOM
cmd = [
    "google-chrome", "--headless", "--disable-gpu", "--no-sandbox",
    "--virtual-time-budget=10000", "--dump-dom", "file:///tmp/test_mermaid.html"
]
result = subprocess.run(cmd, capture_output=True, text=True)

# Parse output
for line in result.stdout.splitlines():
    if "Diagram" in line:
        print(line)
