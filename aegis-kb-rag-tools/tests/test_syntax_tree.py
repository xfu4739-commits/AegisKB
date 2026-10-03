from rag_tools.markdown_syntax import _SourceIndex

text = "# 标题\n正文"
idx = _SourceIndex.build(text)
print(idx.line_starts)
print(idx.byte_offsets)
print(len(text), idx.byte_offsets[-1])  # 字符数 vs UTF-8 字节数