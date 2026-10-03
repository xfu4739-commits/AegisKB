import hashlib
import re
from bisect import bisect_right
from dataclasses import dataclass
from typing import Any

from markdown_it import MarkdownIt
from markdown_it.token import Token

from rag_tools.schemas.document_parse import (
    MARKDOWN_SYNTAX_SCHEMA_VERSION,
    DocumentBlock,
    MarkdownSourceSpan,
    MarkdownSyntaxDocument,
    MarkdownSyntaxNode,
    json_metadata,
)


_OPEN_NODE_TYPES = {
    "heading_open": "HEADING",
    "paragraph_open": "PARAGRAPH",
    "ordered_list_open": "ORDERED_LIST",
    "bullet_list_open": "UNORDERED_LIST",
    "list_item_open": "LIST_ITEM",
    "blockquote_open": "BLOCKQUOTE",
    "table_open": "TABLE",
    "thead_open": "TABLE_HEAD",
    "tbody_open": "TABLE_BODY",
    "tr_open": "TABLE_ROW",
    "th_open": "TABLE_CELL",
    "td_open": "TABLE_CELL",
}

_CLOSE_NODE_TYPES = {
    token_type.replace("_open", "_close"): node_type
    for token_type, node_type in _OPEN_NODE_TYPES.items()
}

_LEAF_NODE_TYPES = {
    "fence": "CODE_BLOCK",
    "code_block": "CODE_BLOCK",
    "hr": "THEMATIC_BREAK",
    "html_block": "HTML_BLOCK",
}


@dataclass(frozen=True)  # 实例创建后不可修改,只读
class _SourceIndex:
    """源码位置索引：把 markdown-it 的粗位置（行 map）换算为节点可用的 MarkdownSourceSpan。

    坐标分层：
      token.map（0-based 行号）→ characterSpan（字符 offset）→ sourceSpan（UTF-8 字节 + 1-based 行列）

    build() 预计算 line_starts / byte_offsets，建树过程中只做查表换算，不重复扫描原文。
    """

    # 完整 Markdown 原文（与 MarkdownSyntaxDocument.sourceText 一致）
    source_text: str
    # 每行起始字符下标；line_starts[i] = 第 i 行（0-based）第一个字符在 source_text 中的 index
    line_starts: list[int]
    # 累计 UTF-8 字节数；byte_offsets[i] = source_text[:i] 编码后的字节长度（切片用 [s:e]）
    byte_offsets: list[int]

    @classmethod
    # 第一个参数是类本身 cls，不是实例 self。用来写「跟类相关、但不一定要已有实例」的逻辑，常见是工厂方法。
    def build(cls, source_text: str) -> "_SourceIndex":
        """
        一次性扫描原文，构建行表与字节表。
        行表：line_starts，记录每一行第一个字符source_text中的下表
        字节表：byte_offsets，记录source_text[:i] 编码后的字节长度
        """
        # 按 \n / \r\n / \r 切行，记录每行起始字符 offset
        line_starts = [0]
        for match in re.finditer(r"\r\n|\n|\r", source_text):
            line_starts.append(match.end())
        # 逐字符累加 UTF-8 长度（中文等与 len(str) 不同的场景）
        byte_offsets = [0]
        byte_total = 0
        for character in source_text:
            byte_total += len(character.encode("utf-8"))
            byte_offsets.append(byte_total)
        return cls(source_text, line_starts, byte_offsets)

    def character_span(self, line_map: list[int] | None) -> tuple[int, int] | None:
        """markdown-it token.map [start_line, end_line] → 半开区间字符 span (start, end)。"""
        if not line_map or len(line_map) != 2:
            return None
        start_line, end_line = line_map
        if start_line < 0 or end_line < start_line:
            raise ValueError(f"invalid markdown token line map: {line_map}")
        start = self.line_starts[start_line] if start_line < len(self.line_starts) else len(self.source_text)
        end = self.line_starts[end_line] if end_line < len(self.line_starts) else len(self.source_text)
        return start, end

    def source_span(self, character_span: tuple[int, int]) -> MarkdownSourceSpan:
        """字符 span → 对外权威的 MarkdownSourceSpan（字节范围 + 行列，行列为 1-based）。"""
        start, end = character_span
        start_line, start_column = self.line_column(start)
        end_line, end_column = self.line_column(end)
        return MarkdownSourceSpan(
            startByte=self.byte_offsets[start],
            endByte=self.byte_offsets[end],
            startLine=start_line,
            startColumn=start_column,
            endLine=end_line,
            endColumn=end_column,
        )

    def line_column(self, character_offset: int) -> tuple[int, int]:
        """字符 offset → (line, column)，均为 1-based，供人类可读定位。"""
        line_index = max(0, bisect_right(self.line_starts, character_offset) - 1)
        return line_index + 1, character_offset - self.line_starts[line_index] + 1

    def table_cell_span(self, row_span: tuple[int, int], column_index: int) -> tuple[int, int]:
        """表格行字符 span + 列号 → 单元格在原文中的字符 span。

        markdown-it 表格 token 通常只有整行 map，无列级位置；此处按 | 分列（支持 \\| 转义），
        并去掉行首行尾空白与首尾空列。
        """
        row_start, row_end = row_span
        row_text = self.source_text[row_start:row_end]
        row_text = row_text.removesuffix("\r\n").removesuffix("\n").removesuffix("\r")
        left_trimmed = row_text.lstrip()
        content_start = row_start + len(row_text) - len(left_trimmed)
        content = left_trimmed.rstrip()
        content_end = content_start + len(content)

        # 按未转义的 | 切分列
        segments = []
        segment_start = 0
        escaped = False
        for index, character in enumerate(content):
            if character == "|" and not escaped:
                segments.append((segment_start, index))
                segment_start = index + 1
            escaped = character == "\\"
        segments.append((segment_start, len(content)))

        # 去掉 Markdown 表格行首/行尾的空分隔段
        if segments and segments[0] == (0, 0):
            segments.pop(0)
        if segments and segments[-1] == (len(content), len(content)):
            segments.pop()
        if column_index >= len(segments):
            return content_end, content_end

        start, end = segments[column_index]
        cell_text = content[start:end]
        trimmed_left = cell_text.lstrip()
        start += len(cell_text) - len(trimmed_left)
        end = start + len(trimmed_left.rstrip())
        return content_start + start, content_start + end


def parse_markdown_syntax(source_text: str, source_origin: str) -> MarkdownSyntaxDocument:
    """将 Markdown 源码建树为 MarkdownSyntaxDocument（扁平 nodes，parentNodeId 串父子）。"""
    # 建树步骤 1：markdown-it 解析源码 → 块级 token 流（parser 配置见步骤 0）
    parser = _markdown_parser()
    tokens = parser.parse(source_text)
    # 建树步骤 2：建立字符 offset ↔ 行号/列号/UTF-8 字节的索引，供 sourceSpan 计算
    source_index = _SourceIndex.build(source_text)
    # 建树步骤 3：初始化建树状态 —— 节点草稿、id 索引、open/close 栈、表格行列计数器
    drafts: list[dict[str, Any]] = []  #按创建顺序存放 每个节点的草稿 dict
    drafts_by_id: dict[str, dict[str, Any]] = {} # 	nodeId → 草稿，方便栈顶、找父节点、找表格祖先
    stack: list[str] = []  # 当前 未闭合 的结构节点 id 栈
    row_count_by_table: dict[str, int] = {}   # 每个 TABLE 下已经见到几行 → 给 TABLE_ROW 的 rowIndex                   
    column_count_by_row: dict[str, int] = {}   # 每个 TABLE_ROW 下已经见到几列 → 给 TABLE_CELL 的 columnIndex

    # 建树步骤 4：创建节点草稿（尚未 validate），登记到 drafts / drafts_by_id
    def append_node(node_type: str,
                    parent_node_id: str | None,
                    character_span: tuple[int, int] | None,
                    **fields) -> dict[str, Any]:
        order = len(drafts)
        node_id = f"md-{order:06d}"
        draft = {
            "order": order,
            "nodeId": node_id,
            "parentNodeId": parent_node_id,
            "nodeType": node_type,
            "origin": source_origin,
            "characterSpan": character_span,
            "text": "",
            "level": None,
            "marker": None,
            "ordinal": None,
            "header": None,
            "alignment": None,
            "rowIndex": None,
            "columnIndex": None,
            "info": None,
        }
        draft.update(fields)
        drafts.append(draft)
        drafts_by_id[node_id] = draft
        return draft

    # 建树步骤 5：DOCUMENT 根节点入栈，作为后续所有块级节点的默认父节点
    root = append_node("DOCUMENT", None, (0, len(source_text)))
    stack.append(root["nodeId"])

    # 建树步骤 6：正向扫描 token 流，用栈维护 open/close 嵌套关系
    for token in tokens:
        # 建树步骤 6.1：结构 open —— 建节点、挂到栈顶父下、入栈（专有字段见步骤 1）
        if token.type in _OPEN_NODE_TYPES and token.nesting == 1:
            node_type = _OPEN_NODE_TYPES[token.type]
            parent_id = stack[-1]
            fields = _opening_fields(token, node_type, parent_id, drafts_by_id,
                                     row_count_by_table, column_count_by_row, source_index)
            character_span = fields.pop("_characterSpan", source_index.character_span(token.map))
            draft = append_node(node_type, parent_id, character_span, **fields)
            stack.append(draft["nodeId"])
            continue

        # 建树步骤 6.2：inline —— 给当前栈顶结构节点填充行内纯文本（文本提取见步骤 4）
        if token.type == "inline":
            if len(stack) <= 1:
                raise ValueError("markdown inline token has no structural parent")
            draft = drafts_by_id[stack[-1]]
            draft["text"] = _inline_text(token)
            if draft["characterSpan"] is None:
                draft["characterSpan"] = source_index.character_span(token.map)
            continue

        # 建树步骤 6.3：结构 close —— 校验类型匹配后出栈，闭合当前结构节点
        if token.type in _CLOSE_NODE_TYPES and token.nesting == -1:
            expected_type = _CLOSE_NODE_TYPES[token.type]
            if len(stack) <= 1 or drafts_by_id[stack[-1]]["nodeType"] != expected_type:
                raise ValueError(f"unbalanced markdown token: {token.type}")
            stack.pop()
            continue

        # 建树步骤 6.4：无 open/close 的叶子块（代码块、分隔线、html_block 等）直接挂到栈顶父下
        if token.type in _LEAF_NODE_TYPES:
            node_type = _LEAF_NODE_TYPES[token.type]
            append_node(
                node_type,
                stack[-1],
                source_index.character_span(token.map),
                text=token.content.rstrip("\n"),
                marker=token.markup or None,
                info=(token.info or "").strip() or None,
            )

    # 建树步骤 7：确认 token 流结束时栈仅剩根，否则说明 open/close 不平衡
    if stack != [root["nodeId"]]:
        raise ValueError("markdown token stream ended with unclosed structural nodes")

    # 建树步骤 8：后处理 —— 补全缺失 span（步骤 5）、容器拼接 text（步骤 6）
    _fill_missing_spans(drafts, drafts_by_id)
    _fill_container_text(drafts)

    # 建树步骤 9：characterSpan（字符 offset）→ sourceSpan（字节+行列），并 validate 为 MarkdownSyntaxNode
    nodes = []
    for draft in drafts:
        character_span = draft.pop("characterSpan")
        draft.pop("_rowIndex", None)
        if character_span is None:
            raise ValueError(f"markdown node {draft['nodeId']} has no source span")
        draft["sourceSpan"] = source_index.source_span(character_span)
        nodes.append(MarkdownSyntaxNode.model_validate(draft))

    # 建树步骤 10：附带原文指纹与 schema 版本，组装 MarkdownSyntaxDocument
    source_bytes = source_text.encode("utf-8")
    return MarkdownSyntaxDocument(
        schemaVersion=MARKDOWN_SYNTAX_SCHEMA_VERSION,
        sourceOrigin=source_origin,
        sourceText=source_text,
        sourceLengthBytes=len(source_bytes),
        sourceSha256=hashlib.sha256(source_bytes).hexdigest(),
        nodes=nodes,
    )


def markdown_syntax_to_blocks(syntax: MarkdownSyntaxDocument, parser_name: str) -> list[DocumentBlock]:
    root = syntax.nodes[0]
    top_level = [node for node in syntax.nodes if node.parent_node_id == root.node_id]
    children_by_parent: dict[str, list[MarkdownSyntaxNode]] = {}
    for node in syntax.nodes:
        if node.parent_node_id is not None:
            children_by_parent.setdefault(node.parent_node_id, []).append(node)

    blocks = []
    for node in top_level:
        raw_text = _node_source_text(syntax, node)
        block_type, text = _legacy_block_content(node, raw_text)
        if not text and block_type != "TABLE":
            continue
        table_rows = _table_rows(node, children_by_parent) if node.node_type == "TABLE" else []
        if block_type == "TABLE" and not text:
            text = "\n".join(" | ".join(row) for row in table_rows)
        table_html = _markdown_parser().render(raw_text) if block_type == "TABLE" else ""
        metadata = {
            "parser": parser_name,
            "syntaxSchemaVersion": syntax.schema_version,
            "syntaxNodeId": node.node_id,
            "syntaxNodeType": node.node_type,
            "sourceOrigin": node.origin,
            "sourceSpan": node.source_span.model_dump(by_alias=True),
        }
        if node.level is not None:
            metadata["headingLevel"] = node.level
        if node.marker:
            metadata["originalMarker"] = node.marker
        blocks.append(DocumentBlock(
            blockNo=len(blocks) + 1,
            blockType=block_type,
            text=text,
            tableHtml=table_html,
            tableRows=table_rows,
            metadataJson=json_metadata(metadata),
        ))
    return blocks


# 步骤 0：创建 markdown-it 解析器（CommonMark + 内联 HTML + GFM 表格）
def _markdown_parser() -> MarkdownIt:
    return MarkdownIt("commonmark", {"html": True}).enable("table")


# 步骤 1：根据开标签 token 提取节点专有字段（标题层级、列表序号、表格行列等）
# 在 parse_markdown_syntax 主循环里，遇到 *_open 且 nesting==1 时调用
def _opening_fields(token: Token,
                    node_type: str,
                    parent_id: str,
                    drafts_by_id: dict[str, dict[str, Any]],
                    row_count_by_table: dict[str, int],
                    column_count_by_row: dict[str, int],
                    source_index: _SourceIndex) -> dict[str, Any]:
    fields: dict[str, Any] = {}
    # 步骤 1.1：标题 —— 从 h1/h2... 取 level，从 #/## 取 marker
    if node_type == "HEADING":
        fields["level"] = int(token.tag[1:])
        fields["marker"] = token.markup
    # 步骤 1.2：有序列表 —— 记录起始序号（如 start="3"）
    elif node_type == "ORDERED_LIST":
        start = token.attrGet("start")
        if start is not None:
            fields["ordinal"] = int(start)
    # 步骤 1.3：列表项 —— 有序列表带序号+标记，无序列表只带 bullet
    elif node_type == "LIST_ITEM":
        parent_type = drafts_by_id[parent_id]["nodeType"]
        if parent_type == "ORDERED_LIST":
            fields["ordinal"] = int(token.info)
            fields["marker"] = f"{token.info}{token.markup}"
        else:
            fields["marker"] = token.markup
    # 步骤 1.4：表格行 —— 在同一 TABLE 下按出现顺序分配 rowIndex
    elif node_type == "TABLE_ROW":
        table_id = _ancestor_id(parent_id, "TABLE", drafts_by_id)
        row_index = row_count_by_table.get(table_id, 0)
        row_count_by_table[table_id] = row_index + 1
        fields["_rowIndex"] = row_index
    # 步骤 1.5：表格单元格 —— 算列号、表头标记、对齐、以及精确的字符 span
    elif node_type == "TABLE_CELL":
        row_id = _ancestor_id(parent_id, "TABLE_ROW", drafts_by_id)
        row = drafts_by_id[row_id]
        column_index = column_count_by_row.get(row_id, 0)
        column_count_by_row[row_id] = column_index + 1
        fields.update({
            # markdown-it 的 map 只到行级，单元格 span 需按 | 分列手算
            "_characterSpan": source_index.table_cell_span(row["characterSpan"], column_index),
            "header": token.type == "th_open",
            "alignment": _table_alignment(token.attrGet("style")),
            "rowIndex": row["_rowIndex"],
            "columnIndex": column_index,
        })
    return fields


# 步骤 2：沿 parent 链向上找指定类型的祖先节点 id（表格行/列计数用）
def _ancestor_id(node_id: str,
                 node_type: str,
                 drafts_by_id: dict[str, dict[str, Any]]) -> str:
    current_id: str | None = node_id
    while current_id is not None:
        current = drafts_by_id[current_id]
        if current["nodeType"] == node_type:
            return current_id
        current_id = current["parentNodeId"]
    raise ValueError(f"markdown {node_type} ancestor is missing")


# 步骤 3：从 th/td 的 style 属性解析 text-align，转成 LEFT/CENTER/RIGHT
def _table_alignment(style: str | None) -> str | None:
    if not style:
        return None
    match = re.search(r"text-align\s*:\s*(left|center|right)", style, re.IGNORECASE)
    return match.group(1).upper() if match else None


# 步骤 4：从 inline token 的子 token 拼出纯文本（段落/标题/单元格正文）
# 注意：image 只保留 alt 文本，不保留 URL 或二进制
def _inline_text(token: Token) -> str:
    parts = []
    for child in token.children or []:
        if child.type in {"text", "code_inline", "html_inline"}:
            parts.append(child.content)
        elif child.type in {"softbreak", "hardbreak"}:
            parts.append("\n")
        elif child.type == "image":
            parts.append(child.content)
    return "".join(parts)


# 步骤 5：补全没有 characterSpan 的节点（自底向上）
# 优先用子节点 span 的并集，否则继承父节点 span
def _fill_missing_spans(drafts: list[dict[str, Any]], drafts_by_id: dict[str, dict[str, Any]]) -> None:
    # 步骤 5.1：建 parent -> children 索引
    children_by_parent: dict[str, list[dict[str, Any]]] = {}
    for draft in drafts:
        parent_id = draft["parentNodeId"]
        if parent_id is not None:
            children_by_parent.setdefault(parent_id, []).append(draft)
    # 步骤 5.2：逆序遍历（子先于父处理完），补 span
    for draft in reversed(drafts):
        if draft["characterSpan"] is not None:
            continue
        children = [child for child in children_by_parent.get(draft["nodeId"], []) if child["characterSpan"]]
        if children:
            draft["characterSpan"] = (
                min(child["characterSpan"][0] for child in children),
                max(child["characterSpan"][1] for child in children),
            )
            continue
        parent_id = draft["parentNodeId"]
        if parent_id is not None:
            draft["characterSpan"] = drafts_by_id[parent_id]["characterSpan"]


# 步骤 6：给没有 text 的容器节点拼文本（自底向上）
# TABLE_ROW 用 " | " 连接单元格，其它容器用换行连接子节点
def _fill_container_text(drafts: list[dict[str, Any]]) -> None:
    children_by_parent: dict[str, list[dict[str, Any]]] = {}
    for draft in drafts:
        parent_id = draft["parentNodeId"]
        if parent_id is not None:
            children_by_parent.setdefault(parent_id, []).append(draft)
    for draft in reversed(drafts):
        if draft["text"] or draft["nodeType"] == "DOCUMENT":
            continue
        children = children_by_parent.get(draft["nodeId"], [])
        texts = [child["text"] for child in children if child["text"]]
        if draft["nodeType"] == "TABLE_ROW":
            draft["text"] = " | ".join(child["text"] for child in children if child["nodeType"] == "TABLE_CELL")
        elif texts:
            draft["text"] = "\n".join(texts)


# 步骤 7：语法树节点 -> RAG 用的 legacy block 类型 + 展示文本（markdown_syntax_to_blocks 里调用）
def _legacy_block_content(node: MarkdownSyntaxNode, source_text: str) -> tuple[str, str]:
    raw_text = source_text.strip()
    if node.node_type == "HEADING":
        return "TITLE", node.text.strip()
    if node.node_type in {"ORDERED_LIST", "UNORDERED_LIST"}:
        return "LIST", raw_text
    if node.node_type == "TABLE":
        return "TABLE", node.text.strip()
    if node.node_type == "CODE_BLOCK":
        return "CODE", raw_text
    if node.node_type == "BLOCKQUOTE":
        return "BLOCKQUOTE", raw_text
    if node.node_type == "THEMATIC_BREAK":
        return "THEMATIC_BREAK", raw_text
    if node.node_type == "HTML_BLOCK":
        return "HTML", raw_text
    return "TEXT", raw_text


# 步骤 8：按节点的 sourceSpan（UTF-8 字节范围）从原文切片
def _node_source_text(syntax: MarkdownSyntaxDocument, node: MarkdownSyntaxNode) -> str:
    source_bytes = syntax.source_text.encode("utf-8")
    return source_bytes[node.source_span.start_byte:node.source_span.end_byte].decode("utf-8")


# 步骤 9：把 TABLE 节点下的 TABLE_CELL 铺成二维字符串矩阵 tableRows
def _table_rows(table: MarkdownSyntaxNode,
                children_by_parent: dict[str, list[MarkdownSyntaxNode]]) -> list[list[str]]:
    # 步骤 9.1：BFS 收集 TABLE 下所有后代节点
    descendants = []
    pending = list(children_by_parent.get(table.node_id, []))
    while pending:
        node = pending.pop(0)
        descendants.append(node)
        pending[0:0] = children_by_parent.get(node.node_id, [])
    # 步骤 9.2：只取 TABLE_CELL，按 rowIndex/columnIndex 填入二维数组
    cells = [node for node in descendants if node.node_type == "TABLE_CELL"]
    if not cells:
        return []
    row_count = max(cell.row_index or 0 for cell in cells) + 1
    column_count = max(cell.column_index or 0 for cell in cells) + 1
    rows = [["" for _ in range(column_count)] for _ in range(row_count)]
    for cell in cells:
        rows[cell.row_index][cell.column_index] = cell.text
    return rows
