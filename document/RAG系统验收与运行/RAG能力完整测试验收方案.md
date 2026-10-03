# RAG 能力完整测试验收方案

> 本文是当前课程版的可复跑验收方案，不维护阶段状态。当前状态只看 `document/RAG系统设计与重构/改造计划与进度.md`。
>
> 当前初始化权威以仓库现有 SQL `sql/表数据/Mysql/知识路由初始化(需要文档例子演示中文档上传解析完).sql` 为准：使用三个固定知识库名称和脚本内固定 ID 段。历史 run-code 方案只属于旧执行记录，不与当前流程并列。

## 1. 文档定位

本文是当前保留的可复跑验收方案，用来验证项目改造后 O2、O3、O4、O6、O7、O8 的核心能力。它不是历史记录，也不是改造计划；阶段顺序和实时状态以 `document/RAG系统设计与重构/改造计划与进度.md` 为准。

当前测试分两种：

| 测试类型 | 什么时候用 | 是否重新上传 15 份文档 |
| :--- | :--- | :--- |
| 运行时复测 | 只改 O8 路由融合、O3 rerank、O4 citation、final evidence、answer plan、O9 trace | 不需要 |
| 完整重建验收 | 改 O2 解析、切块参数、Parent/Child、表格解析、GraphRAG/RAPTOR 构建、KB 构建期参数 | 需要 |

2026-07-10 复核后的默认策略：

- 运行时阶段用新 exchange 做最小复测，不复用旧 exchange 证明通过。
- 解析、结构、table、GraphRAG 或 RAPTOR 构建阶段先重建代表文档；最终验收必须在隔离知识库完整重建 15 份文档。
- 最终重建产物验收与 48 个唯一 Q 编号的对话验收分开执行。
- Markdown table 的真实构建、table channel 召回和 row/cell 回源属于本轮核心闭环；复杂跨页/无框线表格、复杂 community report 和高级全局总结仍属于后续质量增强。

### 1.1 执行主体和人工交接

AI 可以新增并运行单元测试、集成测试、characterization fixture、编译、静态门禁和测试隔离数据上的自动化验证，也可以准备操作清单并记录操作前 document/task/exchange 水位。

以下步骤必须由用户通过实际产品入口或明确的人工命令执行：

- 创建或选择验收知识库，上传或重新上传 15 份验收文档。
- 触发真实解析、索引、重建、重试以及 GraphRAG/RAPTOR 等派生产物构建。
- 执行会写入真实验收库的知识范围和主题初始化脚本。
- 按规定顺序和会话分组执行真实聊天，包括阶段复测与最终 48 个 case。

需要人工操作时，AI 必须先给出具体 KB/文档/对话清单、操作前水位、预期成功状态和后续只读验收项，在改造目录 `改造计划与进度.md` 把阶段标为 `HUMAN_ACTION_REQUIRED` 并更新 `当前阶段与下一步.md`，然后停止等待。未经用户在当前任务中单独明确授权，AI 不得使用浏览器自动化、HTTP 接口、脚本或数据库写入代替这些操作，不得自行制造 document/task/exchange。

用户完成后可以回复“已完成”，并尽量提供 document/task/exchange ID 或操作时间范围。AI 优先按用户提供的 ID 验收；否则只能根据事前水位和时间只读定位新记录。后续验收只读查询数据库、O9 stage snapshot、检索结果、索引产物和日志，不得混用旧 cohort 或修改数据制造通过。

## 2. 保留的测试知识库和样例

完整重建验收时由用户创建或选择 3 个测试知识库：

初始化脚本按下表的固定名称匹配知识库，不使用 run code 后缀。

| 知识库 | 用途 |
| :--- | :--- |
| 解析回归知识库 | O2 OCR、layout、artifact、bbox、表格、页面定位 |
| 运营制度知识库 | O3/O4/O7/O8、表格强测、多文档路由、RAPTOR |
| GraphRAG图谱评测知识库 | O6 canonical、别名、关系、community、多社区排序 |

由用户上传 15 份必传样例：

| 知识库 | 文件 | 主要验证 |
| :--- | :--- | :--- |
| 解析回归知识库 | `需要的例子演示/O2-provider-artifact验收样例.pdf` | 普通 PDF、layout、表格、FIGURE、artifact、bbox |
| 解析回归知识库 | `需要的例子演示/O2-扫描OCR验收样例-图片型PDF.pdf` | 图片型 PDF OCR、PAGE_IMAGE、TABLE_IMAGE、bbox overlay |
| 解析回归知识库 | `需要的例子演示/O2-扫描OCR验收样例-文字截图.png` | 图片 OCR、图片表格、关键短语 |
| 运营制度知识库 | `需要的例子演示/星联智服全渠道客服平台上线与运营管理手册.md` | 单文档长文、章节结构、追问、负样例、RAPTOR、O8 主基线 |
| 运营制度知识库 | `需要的例子演示/生产环境发布与回滚操作规范.md` | 发布回滚、多文档路由、暂停发布原则 |
| 运营制度知识库 | `需要的例子演示/核心业务系统故障应急响应预案.md` | NovaRAG 降级、故障等级、应急处置 |
| 运营制度知识库 | `需要的例子演示/客户数据分级与访问控制管理制度.md` | 数据等级、审批、表格 |
| 运营制度知识库 | `需要的例子演示/差旅与费用报销管理办法.md` | 金额阈值、报销审批、表格 |
| 运营制度知识库 | `需要的例子演示/澄星智能新员工入职培训手册.md` | 培训日程、30/60/90 |
| GraphRAG图谱评测知识库 | `需要的例子演示/O6跨文档图谱-审计证据规范A.md` | AuditTrail 证据规范 |
| GraphRAG图谱评测知识库 | `需要的例子演示/O6跨文档图谱-审计系统别名说明B.md` | AuditTrail 别名和负边界 |
| GraphRAG图谱评测知识库 | `需要的例子演示/O6多社区排序-生产发布回滚规范A.md` | ReleaseControl community |
| GraphRAG图谱评测知识库 | `需要的例子演示/O6多社区排序-生产发布回滚别名B.md` | CAB / SRE 别名 |
| GraphRAG图谱评测知识库 | `需要的例子演示/O6多社区排序-客户数据访问控制规范A.md` | DataAccessGuard community |
| GraphRAG图谱评测知识库 | `需要的例子演示/O6多社区排序-客户数据访问控制别名B.md` | 数据治理负责人 / 信息安全部别名 |

用户完成上传并触发解析、策略和索引后，AI 只读验收必须满足：

```text
parse_status = 3
strategy_status = 3
index_status = 3
last_index_task_id 不为空
```

如果只是运行时复测，不重新上传；直接使用当前已完成解析和索引的这 15 份文档。

## 3. 知识范围和主题初始化

完整重建验收时，知识库和文档上传完成且 AI 只读确认构建产物通过后，由用户执行初始化脚本；AI 只负责准备或校验命令。

脚本固定使用 `aegis_kb` 库、上述 3 个固定知识库名和 ID 段起点 `9000000000000000000`，不需要设置 `O2_O8_RUN_CODE`、`O2_O8_BASE_ID` 或 MySQL 会话变量，直接执行：

```bash
mysql -uroot -proot aegis_kb \
  < 'sql/表数据/Mysql/知识路由初始化(需要文档例子演示中文档上传解析完).sql'
```

脚本目标：

- 在 3 个 KB 内创建测试 KnowledgeScope。
- 创建测试 KnowledgeTopic。
- 建立 TopicDocumentRelation。
- 生成或更新 DocumentProfile。

脚本不应该：

- 创建知识库。
- 修改文档所属知识库。
- 恢复旧知识域/分类/标签字段。
- 修改 chunk、向量、ES、KG、RAPTOR 数据。

门禁 SQL：

```sql
SELECT d.original_file_name,
       d.knowledge_base_name,
       d.parse_status,
       d.strategy_status,
       d.index_status,
       d.last_index_task_id
FROM aegis_kb_document d
WHERE d.status = 1
  AND d.original_file_name IN (
    'O2-provider-artifact验收样例.pdf',
    'O2-扫描OCR验收样例-图片型PDF.pdf',
    'O2-扫描OCR验收样例-文字截图.png',
    '星联智服全渠道客服平台上线与运营管理手册.md',
    '生产环境发布与回滚操作规范.md',
    '核心业务系统故障应急响应预案.md',
    '客户数据分级与访问控制管理制度.md',
    '差旅与费用报销管理办法.md',
    '澄星智能新员工入职培训手册.md',
    'O6跨文档图谱-审计证据规范A.md',
    'O6跨文档图谱-审计系统别名说明B.md',
    'O6多社区排序-生产发布回滚规范A.md',
    'O6多社区排序-生产发布回滚别名B.md',
    'O6多社区排序-客户数据访问控制规范A.md',
    'O6多社区排序-客户数据访问控制别名B.md'
  )
ORDER BY d.knowledge_base_name, d.original_file_name;
```

期望：15 行都完成解析、策略和索引，并落到预期 KB。

## 4. O2 文档侧验收

只有改 O2 解析、切块参数或构建期配置时才必须重新跑。

检查 A-O2 三份样例：

| 能力 | 通过标准 |
| :--- | :--- |
| Document Mind artifact | 有 `ALIYUN_DOCMIND_JSON`、`LAYOUT_JSON`、`JSON`、`MARKDOWN` |
| OCR | OCR 样例能识别关键短语 |
| block/table bbox | block/table bbox 入库，管理端 overlay 不明显错位 |
| PAGE_IMAGE/TABLE_IMAGE | PDF/图片型样例有页面图片和表格图片 |
| table/row/cell | 基础样例表格能拆成结构化 table/row/column/cell |
| cell bbox | 没有可信 cell 坐标时保持为空，不伪造 |
| RAG 产物 | ParentBlock、ChildChunk、Table、KG、RAPTOR 构建状态可见 |

辅助问答：

| 编号 | 问题 | 预期 |
| :--- | :--- | :--- |
| Q-O2-01 | O2 扫描 OCR 样例里有没有识别到“蓝桥订单 7391”“RAG-O2-20260630”和“支付回调延迟”？ | 应能回答识别到了这些关键短语 |
| Q-O2-02 | O2 固定样例中的表格能否被识别成结构化表格？有几行几列？ | 应能定位到基础表格结构 |
| Q-O2-03 | 这份 PDF 样例是否包含图示或图片区域？ | O9 文档侧至少应能看到 FIGURE/IMAGE block 或明确说明样例限制 |

## 5. O8 星联智服单文档主基线

执行方式：

- 模式：当前文档问答。
- KB：运营制度知识库。
- 文档：`星联智服全渠道客服平台上线与运营管理手册.md`。
- 对话：新建一轮，按顺序连续追问。

| 编号 | 问题 | 期望 |
| :--- | :--- | :--- |
| Q-O8-01 | 检索命中率突然下降的可能原因都有哪些？ | `14.1.2` 四条可能原因 |
| Q-O8-02 | 还有造成这个问题的现象都有什么？ | `14.1.1` 三条现象，不能复用原因/检查项 |
| Q-O8-03 | 还有这个问题的处理步骤都有什么？ | `14.1.3` 四步处理步骤 |
| Q-O8-04 | 如果不是检索命中率突然下降，而是回答口径不完整，处理步骤是什么？ | 文档没有明确处理步骤，不能编造 |
| Q-O8-05 | 如果是人工转接率异常升高这个问题，检查顺序应该是什么？ | `14.3.1` 四步检查顺序 |
| Q-O8-06 | 那这个问题里的第一步和第三步分别是什么？ | 继承 Q-O8-05，回答第 1 和第 3 步 |
| Q-O8-07 | 如果不是人工转接率异常升高，而是知识引用错误率突然升高，检查顺序应该是什么？ | 文档没有明确给出 |
| Q-O8-08 | 文档里有没有明确给出“知识引用错误率突然升高”的检查顺序？ | 明确回答没有 |
| Q-O8-09 | 上线观察与值班规则中观察时长都有哪些？ | `13.1` 普通版本不少于 2 小时，高风险版本不少于 1 个自然日 |
| Q-O8-10 | 刚才说的“观察时长”属于哪个一级章节和哪个小节？同一一级章节里的下一小节是什么？ | `十三、上线观察与值班规则`、`13.1`、`13.2 值班安排` |
| Q-O8-11 | 机器人策略设计都包含哪些章节？那其中和知识召回直接相关的是哪几个？ | 至少列出 `10.1/10.2/10.3`；相关判断要有正文 evidence |
| Q-O8-12 | 灰度期必看指标中，“首次命中率”“人工转接率”“无证据回复率”的推荐阈值分别是什么？ | 命中 `11.2` 表格 |
| Q-O8-13 | RACI 责任矩阵里，“父子块策略确认”和“回答质量验收”分别是谁最终负责？ | 命中 `4.2` RACI 表 |
| Q-O8-14 | Parent-Child 推荐配置中，父块流水线和子块流水线分别推荐什么步骤？ | 命中 `9.2` 推荐配置 |
| Q-O8-15 | 请概括这份手册里“上线后运营监控与故障处置”的核心机制。 | 复杂总结，学习版只记录不阻断 |
| Q-O8-16 | 检索命中率突然下降、父子块策略配置错误、finalTopK 下降之间是什么关系？ | 应能基于 `9.x/14.1/17.2` 组织关系，不能与证据相反 |

当前 P0 关注：

- Q-O8-10 结构导航。
- Q-O8-11 父章节子节点枚举。
- Q-O8-02 证据角色。
- Q-O8-16 answer plan。

## 6. O8 多文档路由和融合

执行方式：

- 模式：自动知识问答。
- KB：只选择运营制度知识库。
- 文档：不手选，让 AUTO_DOCUMENT 在 KB 内路由。
- 对话：新建一轮。

| 编号 | 问题 | 期望文档和章节 |
| :--- | :--- | :--- |
| Q-ROUTE-01 | 生产发布默认灰度节奏分几个阶段？每个阶段的流量比例和观察时长是什么？ | 生产发布规范 `7.1` |
| Q-ROUTE-02 | 强制回滚条件有哪些？其中和 NovaRAG 召回成功率相关的条件是什么？ | 生产发布规范 `八、强制回滚条件` |
| Q-ROUTE-03 | NovaRAG 检索服务降级时，按什么顺序处理？ | 核心应急预案 `7.1` |
| Q-ROUTE-04 | 如果 NovaRAG 连续 15 分钟无法返回检索结果，故障等级怎么判断？ | 核心应急预案 `3.1` |
| Q-ROUTE-05 | 生产发布后如果召回成功率下降，应该看发布回滚规范还是故障应急预案？为什么？ | 生产发布规范 + 核心应急预案 |
| Q-ROUTE-06 | 生产发布、故障应急、上线观察这三类规则有什么边界？ | 三份文档可引用，必须标明来源边界 |
| Q-ROUTE-07 | 哪些情况下默认动作是暂停发布，而不是“先试一下再说”？ | 生产发布规范 `5.3` |

当前 P0 关注：

- Q-ROUTE-03、Q-ROUTE-04、Q-ROUTE-07。
- 如果失败，必须用 O9 判断正确证据卡在 route、channel recall、fusion、rerank 还是 final budget。

## 7. O3 rerank 观测

O3 不单独造问答入口，复用第 5/6/9/10 节问题作为候选排序观察对象。实际测试时建议新建 O3 专用对话，减少历史上下文影响。

重点看：

- 正确 evidence 是否进入 channel recall。
- 是否进入 hybrid candidate。
- 是否进入 rerank candidate。
- rerank 前后排名。
- 是否进入 final evidence。
- rerank 失败时是否记录 `rerankStatus/rerankError`，且没有恢复旧 HTTP rerank 或词法 rerank。

建议观察问题：

| 编号 | 问题 | 观察点 |
| :--- | :--- | :--- |
| Q-O3-01 | 哪些情况下默认动作是暂停发布，而不是“先试一下再说”？ | 语义不重合题，正确证据是否进入 rerank/final |
| Q-O3-02 | NovaRAG 检索服务降级时，按什么顺序处理？ | 正确文档进入候选后是否被 rerank 保留 |
| Q-O3-03 | 北京出差酒店住宿上限是多少？如果是其他城市是多少？ | 北京属于一线城市，原文标准为 500 元；观察表格正文和 table metadata 是否被保留 |
| Q-O3-04 | 生产发布控制台 ReleaseControl 和变更评审委员会、值班 SRE 分别是什么关系？ | GraphRAG 候选是否进入 rerank/final |

## 8. 显式引用绑定与来源完整性

所有问答都要检查 citation，不单独造大批问题。

通过标准：

- final references 必须能回 source chunk、table row/cell、KG quote source 或 RAPTOR source chunk。
- `context_only=1` 的 wrapper/summary 不能单独支撑事实答案。
- `SELECTED_STRUCTURE_ANCHOR_BODY` / `SELECTED_STRUCTURE_DESCENDANT_BODY` 必须落到 raw TEXT/LIST/TABLE chunk。
- 负样例不应产生虚假强引用。

重点复核：

- Q-O8-01 至 Q-O8-08 的章节引用。
- Q-O8-12/Q-O8-13/Q-O8-14 的表格/配置引用。
- O6 GraphRAG 关系题的 KG quote source。
- O7 RAPTOR 总结题的 source chunk 下钻。

## 9. O6 GraphRAG

执行方式：

- 模式：自动知识问答。
- KB：只选择 GraphRAG图谱评测知识库。
- 对话：新建一轮。

| 编号 | 问题 | 预期 |
| :--- | :--- | :--- |
| Q-O6-01 | 审计系统有哪些权限相关要求？ | AuditTrail 权限记录、审批、回收、临时权限延长等 |
| Q-O6-02 | 审计系统本身是否审批权限或直接回收权限？ | 负边界：不审批、不直接回收 |
| Q-O6-03 | 如果出现异常权限扩散，AuditTrail 要保留哪些信息，谁负责复核？ | 五类信息和信息安全部复核 |
| Q-O6-04 | 生产发布控制台 ReleaseControl 和变更评审委员会、值班 SRE 分别是什么关系？ | CAB 批准高风险发布；SRE 执行回滚演练 |
| Q-O6-05 | DataAccessGuard 和数据治理负责人、信息安全部分别是什么关系？ | DataAccessGuard 校验申请；数据治理负责人确认 L4 范围；信息安全部批准/复核 |
| Q-O6-06 | ReleaseControl 是否负责 L4 高敏感客户数据访问范围确认？ | 负边界：不是 |
| Q-O6-07 | DataAccessGuard 是否负责回滚演练和发布窗口管控？ | 负边界：不是 |

当前学习版判断：

- AuditTrail canonical 和负边界必须稳定。
- ReleaseControl / DataAccessGuard 复杂关系可作为 O6 后续维护项，不作为 O8 P0 阻断。

## 10. O7 RAPTOR

执行方式：

- 模式：自动知识问答。
- KB：运营制度知识库。
- 对话：新建一轮。

| 编号 | 问题 | 当前判断 |
| :--- | :--- | :--- |
| Q-O7-01 | 请总结星联智服平台从灰度上线到生产发布再到质量复盘的完整治理流程。 | 复杂全局总结，学习版记录不阻断 |
| Q-O7-02 | 这两份规范中，和上线风险控制、灰度验证、回滚评估相关的要求有哪些？请跨文档总结。 | 复杂跨文档总结，记录引用覆盖 |
| Q-O7-03 | 请比较星联智服手册中的上线观察规则和生产发布规范中的灰度观测要求。 | 复杂比较，记录是否能引用两份文档 |
| Q-O7-04 | 如果只看单份星联智服手册，上线后运营监控与故障处置的核心机制是什么？ | 单文档总结，记录是否过度保守 |

通过基础：

- RAPTOR channel 能参与召回。
- RAPTOR selected evidence 必须下钻到 source chunk。
- summary-only 不能作为最终 citation。

## 11. 表格强测

执行方式：

- 优先当前文档问答。
- KB：运营制度知识库。
- 按问题选择目标文档。

| 编号 | 文档 | 问题 | 预期 |
| :--- | :--- | :--- | :--- |
| Q-TABLE-01 | 星联智服手册 | 灰度期必看指标中三个指标的推荐阈值分别是什么？ | 命中 `11.2` 表格 |
| Q-TABLE-02 | 星联智服手册 | RACI 责任矩阵里“父子块策略确认”和“回答质量验收”分别是谁最终负责？ | 命中 `4.2` RACI |
| Q-TABLE-03 | 客户数据制度 | L3 和 L4 数据的日志保存期限分别是多少？ | L3 365 天，L4 2 年 |
| Q-TABLE-04 | 差旅报销 | 北京出差酒店住宿上限是多少？如果是其他城市是多少？ | 北京属于一线城市，500 元/晚；直接问题中的其他城市 350 元/晚；原表另有新一线城市 450 元/晚，可作为更完整回答的观察项，但问题未明确询问该分类，遗漏它本身不构成阻断 |
| Q-TABLE-05 | 差旅报销 | 10000 元以上报销需要哪些审批？ | 直属主管、财务 BP、分管副总裁 |
| Q-TABLE-06 | 入职培训 | 入职当天 09:30-10:30 的培训模块、形式和地点是什么？ | 企业文化与组织介绍、线下、B 座 8 层星河教室 |
| Q-TABLE-07 | 入职培训 | 第 30 天、第 60 天、第 90 天分别关注什么？ | 三个节点关注重点 |

当前确定性通过口径：

- 构建验收必须证明 Markdown table 形成稳定的 table/row/column/cell 产物和 source identity；产物缺失、scope 错误、错误 row/cell 被确定性 filter 消费或 identity/O9 不守恒才是 `BLOCKING_FAIL`。
- 单个问法的 table channel 为 0、模型没有选择 table operation、表头是否计入行数、答案没有覆盖未明确询问的分类，属于 确定性工程链路同样不能保证的检索/理解/生成质量；只要普通合法 Source 可见且没有确定性权威违规，记 `OBSERVATION_ONLY`。
- citation 的硬要求是合法 `[n]` 只能绑定同轮 rendered/reused Source，且 binding/FINALIZE/archive identity 守恒。citation 对答案语义的覆盖程度由人工记录为质量观察，不增加基于相似度或语义猜测的自动补绑层，也不据此单独重开 table/citation 阶段。

## 12. O9 观测记录模板

每个失败样例都按下面模板记录：

```text
问题编号：
exchange ID：
用例判定：PASS / BLOCKING_FAIL / OBSERVATION_ONLY
模式：
知识库：
文档/自动知识问答：
答案结论：
预期证据：
RetrievalPlan / request scope：
route 是否命中文档：
recalled / accepted：
fusion window / rerank input：
budget-selected source：
Prompt rendered source / context：
citation candidate / used：
source snapshot 是否 generation-visible：
channel execution 与 retrieval result 是否守恒：
初步归因：
归属接力阶段：
```

异常归因优先级：

1. 数据门禁不通过。
2. O2 解析/切块/索引产物缺失。
3. route 未包含正确文档。
4. channel recall 未召回正确证据。
5. fusion/rerank/final budget 裁掉正确证据。
6. final evidence 正确但 answer plan/模型回答错误：`OBSERVATION_ONLY`，不分配代码 owner。
7. 合法 token 绑定到未渲染/越界/Context Source，或 binding/FINALIZE/archive identity 不守恒：`BLOCKING_FAIL`，归 explicit citation binding 权威；合法绑定但语义覆盖不足仍是 `OBSERVATION_ONLY`。

同一业务阶段存在多个子步骤时，O9 必须使用唯一 stage code 或显式 parent/substage；不能把 schema 不同的多条记录都记成不可区分的 `ROUTE`。

## 13. 当前不再作为 P0 的内容

- 复杂跨文档治理流程总结。
- community report 自然语言质量。
- GraphRAG 多跳复杂推理。
- RAPTOR 长文全局高质量摘要。
- cell 级 bbox 高亮。
- 复杂 PDF/OCR 表格。

这些内容记录问题即可，等 O8 P0/P1 稳定后再按真实失败样例维护。

三分类边界在所有简单题和复杂题中一致：

- `PASS`：问题事实可用，且 scope、Plan/request、证据可见性、citation authority 与 O9 守恒没有确定性违规。
- `BLOCKING_FAIL`：只能用于当前项目可由源码和运行数据稳定定位的 producer/transition/consumer 错误，例如 hard scope 越界、suggestion 越权成为 hard constraint、合法普通候选被错误增强清空、唯一 ranking/final-policy 违反明确排序/窗口契约、错误构建产物被权威链路消费、非法 token 被实际绑定、Context/未渲染 Source 进入 final snapshot，或 binding/FINALIZE/archive identity 不守恒。
- `OBSERVATION_ONLY`：LLM query understanding、multi-turn refinement、reranker 模型和答案生成造成的答反、漏项、措辞、表格分类/行数口径、citation 语义覆盖不足或复杂关系综合。只要上述确定性边界合法，这些现象即使与期望答案不一致，也不得据此重开阶段、增加本地语义 adapter 或修改生产代码。

模型理解、reranker 与生成质量无法由确定性工程链路逐轮保证；验收不能把这类波动冒充为 scope、evidence、citation 或 O9 的确定性失败。观察项可以记录用户可见影响，但不计入 `BLOCKING_FAIL`。

## 14. 最终执行约束

- 真实文档上传、解析/索引/重建、初始化脚本和 48 个聊天 case 均由用户执行；AI 必须先记录水位并等待，不得通过浏览器、HTTP 接口、脚本或数据库写入代跑。
- 用户完成后，AI 只读查询数据库、O9 stage snapshot、检索结果、索引产物和日志；没有用户提供的 ID 时，只能使用事前水位和操作时间定位新记录。
- 真实对话必须来自用户使用的实际产品入口；脚本直接制造 exchange、复用旧 exchange 或混入水位之前的记录均不能作为通过证据。
- 本文共有 48 个唯一 Q 编号；最终验收必须逐个记录新 exchange，不能用总轮数替代用例覆盖。
- 分组数量固定为 O2=3、O8=16、ROUTE=7、O3=4、O6=7、O7=4、TABLE=7。
- 48 个 case ID 对应 44 个唯一问题文本；O3-01/02/03/04 分别复用 ROUTE-07、ROUTE-03、TABLE-04、O6-04，但仍须在 O3 专用对话产生独立 exchange。
- Q-O2-01/Q-O2-02/Q-O2-03 必须补跑，不能继续只验收构建产物。
- 每轮运行前记录 `SELECT MAX(id) FROM aegis_kb_chat_exchange`，完成证据只统计水位之后的新 exchange。
- 旧反例 exchange 只用于理解旧问题：Q-ROUTE-03 `2313953969052923334`、Q-TABLE-05 `2313953969052924516`、O8-10 `2313953969052922745`、O8-11 `2313953969052922796`。
- 完整终验只做验收，不现场添加问题文本、文档名、部门、金额或关系类型补丁；失败后回到接力计划中的责任阶段，在新会话修复。
- 只有 `BLOCKING_FAIL` 才产生修复 owner；`OBSERVATION_ONLY` 不要求重启、重建、补跑或生产代码修改。
