# RAG 系统设计与重构执行记录规则

## 1. 记录职责

`stages/Sxx-*.md` 记录进入阶段前准备怎么做，`records/Sxx.md` 记录实际执行时发生了什么。Record 是审计证据，不拥有阶段状态，也不能替代《改造计划与进度》中的状态表。

计划和记录不要求完全一致。以下内容必须如实保存：

- 阶段文件与当前源码不一致；
- 初始假设被代码或数据推翻；
- characterization 证明测试固化旧错误；
- 首次实现可以编译但产生双权威；
- 自动测试通过但真实产品验收失败；
- 数据持久化失败导致 O9 假绿色；
- 后续阶段发现前置 owner 结论失效并触发重开；
- 模型质量问题最终被降为 `OBSERVATION_ONLY`。

## 2. 每次会话模板

```markdown
### YYYY-MM-DD / Sxx / 会话说明

- 状态变化：
- 开始/结束 HEAD：
- 预存工作区：
- document/task/exchange 水位：

#### 开工事实

- 已确认事实：
- 文档差异：
- 证据不足项：
- 固化旧错误的测试：
- 新增 fixture：
- 实际修改边界：

#### 执行结果

- 首个红灯：
- 实际根因：
- 候选方案：
- 最终决策：
- 实际修改：
- 自动验证：
- 人工操作及 watermark：
- 用户提供的 ID：
- AI 只读验收：
- O9 对账：
- 偏差和风险：
- 是否重开前置 owner：
- 退出判断：
- 下一阶段准入：
- 下一会话提示词：
```

## 3. 记录要求

- 关键结论标注 `CONFIRMED`、`CODE_CONFIRMED`、`DATA_CONFIRMED`、`STRONG_INFERENCE` 或 `HYPOTHESIS`。
- 记录实际命令、退出码和测试数量，不只写“测试通过”。
- 保存开始和结束 HEAD，但不要求每阶段 commit。
- 保存用户预存脏文件，不能把它们写成本阶段修改。
- 真实操作前记录水位、运行版本、目标 KB/document/task、问题分组和预期数量。
- 用户操作后优先使用用户提供的 ID；否则只能按事前水位和时间只读定位。
- 旧 ID 只能作为历史证据，不能进入生产规则、fixture 或新 cohort 通过条件。
- `OBSERVATION_ONLY` 必须写明确定性链路为何合法；`BLOCKING_FAIL` 必须写明 producer、transition、consumer 和责任 owner。
- Record 可以重复阶段状态作为历史事件，但当前实时状态仍只看 改造计划与进度。

## 4. 课程发布整理说明

本目录的新版本保留项目 HEAD、测试数量、watermark、document/task/exchange ID、失败、偏差、人工操作和重开证据。为课程发布只移除了与本项目执行无关的本机研究环境噪音，并把设计依据统一改写为本项目源码、数据、不变量和目标红灯；没有把失败过程美化成一次成功。

## 5. 已删除的本机脚本

`scripts/` 下的本机启动和 SQL 自测脚本已经删除，不再是当前启动、验收或新记录的入口：

- `scripts/start-local.ps1`
- `scripts/start-local.cmd`
- `scripts/start-local.secrets.ps1.example`
- `scripts/dev-java.cmd`
- `scripts/dev-rag.cmd`
- `scripts/dev-vue.cmd`
- `scripts/rag-route-init-sql-test.sh`
- `scripts/system-config-init-sql-test.sh`

`scripts/start-local.secrets.ps1` 被 `.gitignore` 排除，只存在于删除前的本机，不能从 Git 恢复。

新的 record、阶段交接和验证说明直接记录实际执行的命令，不再把上述脚本写成必跑步骤，也不为了让旧命令重新可执行而加回这些文件。已经写入 `records/Sxx.md` 的历史命令保持原样，作为当时证据。

当前本机启动（详见根目录 `README.md`）：

1. 中间件：在仓库根目录执行 `docker compose up -d`，再用 `docker compose ps` 确认。
2. 密钥：复制 `.env.example` 为 `.env` 并填写 `ALI_BAI_LIAN_API_KEY` 等。
3. 一键：`.\start.cmd`（或 `scripts\start-all.ps1`）打开 Java `9083`、rag-tools `18080`、Vue `5173` 三个窗口；也可分别执行 `scripts\start-java.ps1`、`start-rag.ps1`、`start-vue.ps1`。
4. rag-tools 首次需在 `aegis-kb-rag-tools` 创建 `.venv` 并 `pip install -e .`；前端首次需在 `vue/` 执行 `npm install`。
