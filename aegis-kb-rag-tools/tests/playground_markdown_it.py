from markdown_it import MarkdownIt

'''
from markdown_it import MarkdownIt

def test_desiable_rule(md):
    # 示例：禁用链接和图片，只保留结构 + 表格
    md.disable(["link", "image", "autolink", "html_block", "html_inline"])

md = (
    MarkdownIt("commonmark")
    .enable("table")
    .use(test_desiable_rule)
)
# {'maxNesting': 20, 'html': True, 'linkify': False, 'typographer': False, 'quotes': '“”‘’', 'xhtmlOut': True, 'breaks': False, 'langPrefix': 'language-', 'highlight': None}
print(md.options)
print(md.get_all_rules())   # 查看当前所有注册的，包括禁用的
print(md.get_active_rules()) # 查看当前生效的
tokens = md.parse("# title\n\n| a | b |\n|---|---|\n| 1 | 2 |")

print(type(tokens))

"""token流解析
0  type='heading_open'   tag='h1'  nesting=1  level=0  content=''  map=[0, 1]
1  type='inline'         tag=''    nesting=0  level=1  content='title'  map=[0, 1]
2  type='heading_close' tag='h1'  nesting=-1 level=0  content=''  map=None
3  type='table_open'     ...
...

type: 令牌类型
tag: 对应HTML的标签名
nesting: 开/关/自闭：1=打开，-1=关闭，0=内容或自闭合
level：嵌套深度（树层级），配合 open/close 看结构
content：文本内容；结构 open/close 通常为空
map：在源码中的行范围 [start_line, end_line]（0-based），用于定位 span [0,1] 表示第0行
"""
''' 

def test_desiable_rule(md):
    # 示例：禁用链接和图片，只保留结构 + 表格
    md.disable(["link", "image", "autolink", "html_block", "html_inline"])

md = (
    MarkdownIt("commonmark")
    .enable("table")
    .use(test_desiable_rule)
)

tokens = md.parse("""
Header
""")

# 打印每一个token
for token in tokens:
    print(token)
