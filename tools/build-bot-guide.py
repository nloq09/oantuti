"""Render the repository's protocol Markdown subset into a static, readable guide."""
import html
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / "docs" / "BOT-PROTOCOL.md"
target = root / "client" / "bot-protocol.html"


def inline(text):
    text = html.escape(text)
    text = re.sub(r"\[([^]]+)\]\(([^)]+)\)", lambda m: '<a href="' +
                  ("/bot-guide.html" if m[2] == "BOT-UPLOAD.md" else m[2]) + '">' + m[1] + "</a>", text)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    return re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)


blocks, paragraph, toc = [], [], []
code = None
list_type = None
section = 0


def flush():
    global list_type
    if paragraph:
        blocks.append("<p>" + inline(" ".join(paragraph)) + "</p>")
        paragraph.clear()
    if list_type:
        blocks.append(f"</{list_type}>")
        list_type = None


for line in source.read_text(encoding="utf-8").splitlines():
    if line.startswith("```"):
        if code is None:
            flush()
            code = []
        else:
            blocks.append("<pre><code>" + html.escape("\n".join(code)) + "</code></pre>")
            code = None
        continue
    if code is not None:
        code.append(line)
        continue
    if not line.strip():
        flush()
        continue
    heading = re.match(r"^(#{1,3}) (.*)$", line)
    if heading:
        flush()
        level, title = len(heading[1]), heading[2]
        section += 1
        blocks.append(f'<h{level} id="section-{section}">{inline(title)}</h{level}>')
        if level == 2:
            toc.append(f'<li><a href="#section-{section}">{inline(title)}</a></li>')
        continue
    item = re.match(r"^(?:- |\d+\. )(.*)$", line)
    if item:
        kind = "ul" if line.startswith("- ") else "ol"
        if list_type != kind:
            flush()
            blocks.append(f"<{kind}>")
            list_type = kind
        blocks.append("<li>" + inline(item[1]) + "</li>")
        continue
    if list_type:
        flush()
    paragraph.append(line)
flush()
assert code is None, "Unclosed code fence"
target.write_text('''<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Giao thức bot chi tiết · RPS Battle</title><link rel="stylesheet" href="/css/global.css">
<link rel="stylesheet" href="/css/bot-guide.css"></head><body>
<header><a class="brand" href="/">◈ RPS BATTLE</a><a href="/bot-guide.html">Bot mẫu và tải lên →</a></header>
<main class="bot-guide"><p class="eyebrow">DỮ LIỆU VÀ CHIẾN THUẬT</p>
<nav class="panel" aria-label="Mục lục"><strong>Nội dung hướng dẫn</strong><ul>''' + "".join(toc) +
                  '</ul></nav><article class="panel">' + "\n".join(blocks) + '</article></main></body></html>\n', encoding="utf-8")
print("Generated client/bot-protocol.html from docs/BOT-PROTOCOL.md")
