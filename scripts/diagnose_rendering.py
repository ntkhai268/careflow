import re

with open('docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Check all Mermaid blocks
blocks = re.findall(r'```mermaid\s*\n(.*?)```', text, re.DOTALL)
print(f"Total Mermaid diagrams: {len(blocks)}")
for idx, b in enumerate(blocks):
    first_line = b.strip().splitlines()[0] if b.strip() else "EMPTY"
    print(f"Diagram {idx+1}: {first_line}")

# 2. Check the Table syntax around Priority Matrix
lines = text.splitlines()
for idx, line in enumerate(lines[:80]):
    if '|' in line:
        print(f"Line {idx+1}: {line[:80]}")
