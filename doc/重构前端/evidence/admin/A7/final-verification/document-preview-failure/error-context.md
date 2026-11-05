# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admin-document.spec.js >> artifact preview is safe, releases image URLs and authenticated JSON downloads remain files
- Location: e2e\admin-document.spec.js:42:1

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 1
Received: 0

Call Log:
- Timeout 5000ms exceeded while waiting on the predicate
```

# Page snapshot

```yaml
- generic [ref=e4]:
  - generic [ref=e5]:
    - link "跳到主要内容" [ref=e6] [cursor=pointer]:
      - /url: "#admin-next-main"
    - complementary [ref=e7]:
      - generic [ref=e8]:
        - link "AegisKB 管理后台" [ref=e9] [cursor=pointer]:
          - /url: /admin
          - generic [ref=e10]: AegisKB
        - navigation "后台主导航" [ref=e11]:
          - generic [ref=e12]:
            - heading "工作空间" [level=2] [ref=e13]
            - link "运营总览" [ref=e14] [cursor=pointer]:
              - /url: /admin/dashboard
              - img [ref=e15]
              - generic [ref=e17]: 运营总览
            - link "知识运行全景" [ref=e18] [cursor=pointer]:
              - /url: /admin/quality-overview
              - img [ref=e19]
              - generic [ref=e21]: 知识运行全景
          - generic [ref=e22]:
            - heading "知识管理" [level=2] [ref=e23]
            - link "知识库管理" [ref=e24] [cursor=pointer]:
              - /url: /admin/knowledge-bases
              - img [ref=e25]
              - generic [ref=e27]: 知识库管理
            - link "文档管理" [ref=e28] [cursor=pointer]:
              - /url: /admin/documents
              - img [ref=e29]
              - generic [ref=e31]: 文档管理
            - link "知识路由" [ref=e32] [cursor=pointer]:
              - /url: /admin/knowledge-route
              - img [ref=e33]
              - generic [ref=e35]: 知识路由
            - link "路由追踪" [ref=e36] [cursor=pointer]:
              - /url: /admin/knowledge-route/traces
              - img [ref=e37]
              - generic [ref=e39]: 路由追踪
          - generic [ref=e40]:
            - heading "运行与设置" [level=2] [ref=e41]
            - link "对话观测" [ref=e42] [cursor=pointer]:
              - /url: /admin/observability
              - img [ref=e43]
              - generic [ref=e45]: 对话观测
            - link "系统配置" [ref=e46] [cursor=pointer]:
              - /url: /admin/settings/configuration
              - img [ref=e47]
              - generic [ref=e50]: 系统配置
            - link "账户" [ref=e51] [cursor=pointer]:
              - /url: /admin/account
              - img [ref=e52]
              - generic [ref=e54]: 账户
        - generic [ref=e55]:
          - link "返回对话" [ref=e56] [cursor=pointer]:
            - /url: /chat
            - img [ref=e57]
            - generic [ref=e59]: 返回对话
          - link "旧版后台" [ref=e60] [cursor=pointer]:
            - /url: http://localhost:5173/admin/login?redirect=/admin/dashboard
            - img [ref=e61]
            - generic [ref=e63]: 旧版后台
    - generic [ref=e64]:
      - banner [ref=e65]:
        - generic [ref=e66]:
          - button "收起后台导航" [ref=e67] [cursor=pointer]:
            - img [ref=e68]
          - navigation [ref=e70]:
            - list [ref=e71]:
              - listitem [ref=e72]:
                - link "管理后台" [ref=e74] [cursor=pointer]:
                  - /url: /admin
                - text: /
              - listitem [ref=e75]:
                - generic [ref=e76]: 文档详情
        - generic [ref=e77]:
          - generic [ref=e78]: F
          - generic [ref=e79]: fixture-admin
          - button "退出登录" [ref=e80] [cursor=pointer]:
            - img [ref=e81]
      - main [ref=e83]:
        - region "团队知识使用指南" [ref=e84]:
          - generic [ref=e85]:
            - generic [ref=e86]:
              - link "← 文档列表" [ref=e87] [cursor=pointer]:
                - /url: /admin/documents
              - heading "团队知识使用指南" [level=1] [ref=e88]
              - paragraph [ref=e89]: 文档 ID 9007199254740993123
            - button "刷新文档" [ref=e90] [cursor=pointer]:
              - generic [ref=e91]: 刷新文档
          - generic "文档状态" [ref=e92]:
            - generic [ref=e93]: 解析：解析成功
            - generic [ref=e94]: 策略：已确认
            - generic [ref=e95]: 索引：索引完成
          - navigation "文档工作台" [ref=e96]:
            - button "文档概览" [ref=e97] [cursor=pointer]:
              - generic [ref=e98]: 文档概览
            - button "策略方案" [ref=e99] [cursor=pointer]:
              - generic [ref=e100]: 策略方案
            - button "任务与日志" [ref=e101] [cursor=pointer]:
              - generic [ref=e102]: 任务与日志
            - button "解析产物" [ref=e103] [cursor=pointer]:
              - generic [ref=e104]: 解析产物
            - button "分块" [ref=e105] [cursor=pointer]:
              - generic [ref=e106]: 分块
            - button "解析工作台" [ref=e107] [cursor=pointer]:
              - generic [ref=e108]: 解析工作台
            - button "产物与关系" [ref=e109] [cursor=pointer]:
              - generic [ref=e110]: 产物与关系
          - generic [ref=e111]:
            - generic [ref=e112]:
              - heading "解析产物" [level=2] [ref=e113]
              - button "刷新产物" [ref=e114] [cursor=pointer]:
                - generic [ref=e115]: 刷新产物
            - generic [ref=e116]:
              - generic [ref=e117]: 搜索产物文件名
              - textbox "搜索产物文件名" [ref=e118]
            - list [ref=e119]:
              - listitem [ref=e120]:
                - generic [ref=e121]:
                  - strong [ref=e122]: parsed.md
                  - generic [ref=e123]: 标准化正文 · PDF 1.0 · 1024 B
                - generic [ref=e124]:
                  - button "预览" [ref=e125] [cursor=pointer]:
                    - generic [ref=e126]: 预览
                  - button "下载" [ref=e127] [cursor=pointer]:
                    - generic [ref=e128]: 下载
              - listitem [ref=e129]:
                - generic [ref=e130]:
                  - strong [ref=e131]: page.png
                  - generic [ref=e132]: 页面原图 · PDF 1.0 · 1024 B
                - generic [ref=e133]:
                  - button "预览" [ref=e134] [cursor=pointer]:
                    - generic [ref=e135]: 预览
                  - button "下载" [ref=e136] [cursor=pointer]:
                    - generic [ref=e137]: 下载
  - dialog "page.png" [ref=e139]:
    - document:
      - generic [ref=e140]:
        - button "Close" [ref=e141] [cursor=pointer]:
          - img "close" [ref=e143]:
            - img [ref=e144]
        - generic [ref=e147]: page.png
        - generic [ref=e148]:
          - generic [ref=e149]:
            - generic [ref=e150]: 搜索产物内容
            - textbox "搜索产物内容" [ref=e151]:
              - /placeholder: 搜索内容
            - button "复制内容" [ref=e152] [cursor=pointer]:
              - generic [ref=e153]: 复制内容
          - paragraph [ref=e154]: 接口返回 0 字符
          - img "page.png" [ref=e155]
        - generic [ref=e157]:
          - button "返回产物列表" [ref=e158] [cursor=pointer]:
            - generic [ref=e159]: 返回产物列表
          - generic [ref=e160]: 本列表 2 / 2
          - generic [ref=e161]:
            - button "上一个产物" [ref=e162] [cursor=pointer]:
              - generic [ref=e163]: 上一个产物
            - button "下一个产物" [disabled] [ref=e164]:
              - generic [ref=e165]: 下一个产物
```

# Test source

```ts
  1  | import { test,expect } from '@playwright/test'
  2  | import AxeBuilder from '@axe-core/playwright'
  3  | import { resolve } from 'node:path'
  4  | import { readFile } from 'node:fs/promises'
  5  | import { mockDocument } from './fixtures/admin-document.js'
  6  | import { seedAdmin } from './fixtures/admin-auth.js'
  7  | import { docId,parseId,indexId,planId,log } from './fixtures/document-data.js'
  8  | const output=resolve(process.env.AEGIS_DOCUMENT_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A4'),errors=new WeakMap(),states=new WeakMap()
  9  | test.beforeEach(async({page})=>{errors.set(page,[]);page.on('pageerror',e=>errors.get(page).push(e.message));await page.clock.setFixedTime(new Date('2026-11-04T09:30:00+08:00'))})
  10 | test.afterEach(({page})=>{expect(errors.get(page)).toEqual([]);expect(states.get(page)?.catalog.auth.blocked||[]).toEqual([])})
  11 | async function setup(page,context){const state=await mockDocument(context);states.set(page,state);await seedAdmin(page);return state}
  12 | const path=section=>`/admin/documents/${docId}${section?`?section=${section}`:''}`
  13 | async function tab(page,name){await page.getByRole('navigation',{name:'文档工作台'}).getByRole('button',{name,exact:true}).click()}
  14 | async function settledModal(page){const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await expect.poll(()=>page.locator('.admin-next-overlay-host').evaluate(el=>el.getAnimations({subtree:true}).every(a=>a.playState!=='running'&&!a.pending))).toBe(true);return dialog}
  15 | async function capture(page,name){await page.screenshot({path:resolve(output,`${test.info().project.name}-${name}.png`),fullPage:true,animations:'disabled'})}
  16 | async function axe(page){await expect.poll(()=>page.evaluate(()=>document.getAnimations().filter(a=>a.effect?.getComputedTiming().iterations!==Infinity).every(a=>a.playState!=='running'&&!a.pending))).toBe(true);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([])}
  17 | 
  18 | test('detail is lazy, keeps list position and rejects late document responses',async({page,context})=>{
  19 |  const state=await setup(page,context);await page.goto(path()+'?listQ=项目&listPage=2&listSize=12')
  20 |  await expect(page.getByRole('heading',{name:'团队知识使用指南',exact:true})).toBeVisible();expect(state.calls.map(c=>c.kind)).toEqual(['detail'])
  21 |  await expect(page.getByRole('link',{name:'← 文档列表'})).toHaveAttribute('href',/page=2/);await expect(page.getByRole('link',{name:'文档管理',exact:true})).toHaveAttribute('aria-current','page');await axe(page);await page.goto(path('execution'));await expect(page.locator('#doc-task-kind')).toHaveValue('build')
  22 |  state.delays.detail=300;await page.goto(path()+'?section=strategy');await page.goto('/admin/documents/9007199254740993001');await expect(page.getByRole('heading',{name:'第二份文档'})).toBeVisible();await expect(page.locator('.admin-next-doc-id').first()).toContainText('9007199254740993001');expect(state.writes).toHaveLength(0)
  23 | })
  24 | 
  25 | test('strategy preserves order, blocks dirty builds, validates operator and confirms exact target',async({page,context})=>{
  26 |  const state=await setup(page,context);await page.goto(path('strategy'))
  27 |  await expect(page.getByText('按章节组织资料',{exact:true})).toBeVisible();await page.getByRole('button',{name:'确认策略',exact:true}).click();await expect(page.getByRole('alert')).toContainText('操作人 ID');expect(state.writes).toHaveLength(0)
  28 |  await page.getByLabel('操作人 ID',{exact:true}).fill('42');const parent=page.locator('.admin-next-doc-columns > div').first();await parent.getByRole('checkbox',{name:'递归分块',exact:true}).check();await page.getByRole('button',{name:'parent 2 上移'}).click();await expect(page.getByRole('button',{name:'构建索引',exact:true})).toBeDisabled()
  29 |  await page.getByRole('button',{name:'确认策略',exact:true}).click();let dialog=await settledModal(page);await expect(dialog).toContainText(planId);await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(state.writes).toHaveLength(0)
  30 |  await page.getByRole('button',{name:'确认策略',exact:true}).click();dialog=await settledModal(page);state.statuses.confirm=503;await dialog.getByRole('button',{name:'确认提交'}).click();await expect(dialog.getByRole('alert')).toContainText('文档接口失败');await expect(parent.getByRole('checkbox',{name:'递归分块',exact:true})).toBeChecked()
  31 |  state.statuses.confirm=200;state.delays.confirm=150;await dialog.getByRole('button',{name:'确认提交'}).dblclick();await expect(dialog).toBeHidden();expect(state.writes.filter(c=>c.kind==='confirm')).toHaveLength(2);expect(state.writes[1].body).toMatchObject({documentId:docId,basePlanId:planId,operatorId:'42',parentSteps:[{stepNo:'1',strategyType:'2'},{stepNo:'2',strategyType:'1'}]})
  32 |  await expect(page.getByRole('button',{name:'构建索引',exact:true})).toBeEnabled();await page.getByRole('button',{name:'构建索引',exact:true}).click();dialog=await settledModal(page);await expect(dialog).toContainText('将为此文档创建索引构建任务');await dialog.getByRole('button',{name:'确认提交'}).click();await expect(page.getByRole('heading',{name:'任务与日志',exact:true})).toBeVisible();expect(state.writes.at(-1)).toEqual({kind:'index',body:{documentId:docId,planId,operatorId:'42'}})
  33 | })
  34 | 
  35 | test('tasks poll sequentially with precise cursors, stop at terminal, and history pages stay scoped',async({page,context})=>{
  36 |  const state=await setup(page,context);state.buildStatuses=['2','3'];await page.goto(path('tasks'));await expect(page.getByText('当前任务已完成',{exact:true})).toBeVisible();await expect.poll(()=>state.calls.filter(c=>c.kind==='build').length).toBe(2)
  37 |  expect(state.calls.filter(c=>c.kind==='build')[1].body).toMatchObject({taskId:indexId,sinceLogId:log.id});await page.getByRole('button',{name:'查看历史日志',exact:true}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='logs')).toBe(true);await page.getByRole('button',{name:'下一页日志'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='logs'&&c.body.pageNo==='2')).toBe(true)
  38 |  state.parseStatuses=['5'];await page.selectOption('#doc-task-kind','parse');await page.getByRole('textbox',{name:'任务 ID',exact:true}).fill(parseId);await page.getByRole('button',{name:'查看任务',exact:true}).click();await expect(page.getByText('已取消',{exact:true})).toBeVisible();expect(state.calls.filter(c=>c.kind==='parse').at(-1).body.taskId).toBe(parseId)
  39 |  await capture(page,'tasks');await tab(page,'文档概览');expect(state.writes).toHaveLength(0)
  40 | })
  41 | 
  42 | test('artifact preview is safe, releases image URLs and authenticated JSON downloads remain files',async({page,context})=>{
  43 |  const state=await setup(page,context);await page.addInitScript(()=>{const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);window.createdUrls=[];window.revokedUrls=[];URL.createObjectURL=value=>{const url=create(value);window.createdUrls.push(url);return url};URL.revokeObjectURL=url=>{window.revokedUrls.push(url);revoke(url)}})
  44 |  await page.goto(path('artifacts'));await page.locator('.admin-next-doc-items li').filter({hasText:'parsed.md'}).getByRole('button',{name:'预览'}).click();let dialog=await settledModal(page);await expect(dialog.getByRole('heading',{name:'标准正文'})).toBeVisible();expect(await page.evaluate(()=>window.artifactInjected)).toBeUndefined();expect(await dialog.locator('img').count()).toBe(0);await axe(page);await capture(page,'artifact-preview');await page.keyboard.press('Escape');await expect(dialog).toBeHidden()
> 45 |  await page.locator('.admin-next-doc-items li').filter({hasText:'page.png'}).getByRole('button',{name:'预览'}).click();dialog=await settledModal(page);await expect(dialog.getByRole('img',{name:'page.png'})).toBeVisible();await page.keyboard.press('Escape');await expect.poll(()=>page.evaluate(()=>window.revokedUrls.length)).toBe(1)
     |                                                                                                                                                                                                                                                                                                                                    ^ Error: expect(received).toBe(expected) // Object.is equality
  46 |  const [download]=await Promise.all([page.waitForEvent('download'),page.locator('.admin-next-doc-items li').filter({hasText:'parsed.md'}).getByRole('button',{name:'下载'}).click()]);expect(download.suggestedFilename()).toBe('artifact.json');expect(await readFile(await download.path(),'utf8')).toBe('{"safe":true}');expect(state.downloads).toBe(1);expect(state.writes).toHaveLength(0)
  47 | })
  48 | 
  49 | test('chunks paginate on the server and parent/sibling details preserve long identities',async({page,context})=>{
  50 |  const state=await setup(page,context);await page.goto(path('chunk'));await page.getByRole('button',{name:'下一页分块'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='chunks'&&c.body.pageNo==='2')).toBe(true);await page.getByRole('button',{name:'分块详情',exact:true}).click();const dialog=await settledModal(page);await expect(dialog).toContainText('父块完整内容');await capture(page,'chunk-detail');state.delays.chunk=300;await dialog.getByRole('button',{name:'第二块',exact:true}).click();await expect.poll(()=>state.calls.filter(c=>c.kind==='chunk').at(-1).body.chunkId).toBe('9007199254740993445');await page.keyboard.press('Escape');await expect(dialog).toBeHidden()
  51 | })
  52 | 
  53 | test('snapshot deep links, diagnostics and page overlays are read-only and correctly scoped',async({page,context})=>{
  54 |  const state=await setup(page,context);await page.goto(path('rag')+'&highlightTableId=9007199254740993333&highlightRows=1&highlightColumns=金额&highlightCells=1:2');await expect(page.getByRole('heading',{name:'费用明细'})).toBeVisible();expect(state.calls.find(c=>c.kind==='snapshot').body).toMatchObject({highlightTableId:'9007199254740993333',highlightRowNos:['1'],highlightColumnNames:['金额'],highlightCellCoordinates:['1:2']});await expect(page.locator('.admin-next-doc-highlight').first()).toBeVisible()
  55 |  await page.selectOption('#parse-mode','diagnostic');await expect(page.getByText('解析器',{exact:true})).toBeVisible();await page.selectOption('#parse-mode','locator');await page.selectOption('#overlay-page','1');await expect(page.getByRole('img',{name:'第 1 页'})).toBeVisible();await page.getByRole('button',{name:'定位 正文区域'}).click();await expect(page.getByText('原页内容',{exact:true})).toBeVisible();await axe(page);await capture(page,'page-overlay');expect(state.calls.find(c=>c.kind==='page').body).toMatchObject({parseTaskId:parseId,pageNo:'1'});expect(state.writes).toHaveLength(0)
  56 | })
  57 | 
  58 | test('explorer reads bounded nodes, relations, graph/table windows and RAPTOR branches',async({page,context})=>{
  59 |  const state=await setup(page,context);await page.goto(path('explorer'));await page.getByRole('button',{name:'文档章节',exact:true}).first().click();await expect(page.getByText('节点正文内容',{exact:true})).toBeVisible();await page.getByRole('button',{name:'下一页关系'}).first().click();await expect.poll(()=>state.calls.some(c=>c.kind==='relations'&&c.body.pageNo==='2')).toBe(true)
  60 |  await page.selectOption('#artifact-node-type','TABLE');await page.getByRole('button',{name:'费用明细',exact:true}).click();await expect(page.getByRole('region',{name:'表格窗口',exact:true})).toContainText('单元格内容');await page.getByRole('button',{name:'后六列'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='table'&&c.body.columnOffset==='6')).toBe(true);await page.getByRole('button',{name:'下一页表格行'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='table'&&c.body.pageNo==='2')).toBe(true)
  61 |  await page.selectOption('#artifact-node-type','RAPTOR_NODE');await page.getByRole('button',{name:'章节摘要',exact:true}).first().click();await expect.poll(()=>state.calls.some(c=>c.kind==='nodes'&&c.body.parentNodeId==='9007199254740993666')).toBe(true);await page.getByRole('button',{name:'章节摘要',exact:true}).last().click();await expect(page.getByRole('button',{name:'返回上一节点'})).toBeVisible();await page.getByRole('button',{name:'返回上一节点'}).click()
  62 |  await page.getByRole('button',{name:'图谱窗口',exact:true}).click();await expect(page.getByRole('img',{name:/当前图谱窗口/})).toBeVisible();expect(state.calls.find(c=>c.kind==='graph').body).toMatchObject({parseTaskId:parseId,indexTaskId:indexId,maxNodes:'80',maxEdges:'160',includeIsolates:true});await capture(page,'graph-window');await page.getByRole('button',{name:'项目实体',exact:true}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='nodes'&&c.body.nodeType==='KG_EVIDENCE')).toBe(true);await page.getByRole('heading',{name:'图谱证据',exact:true}).locator('..').getByRole('button',{name:'文档章节',exact:true}).first().click();await page.getByRole('button',{name:'定位原页'}).click();await expect(page.getByRole('img',{name:'第 1 页'})).toBeVisible();expect(state.writes).toHaveLength(0)
  63 | })
  64 | 
  65 | test('partial failures retain the document and drafts, retries never trigger real work',async({page,context})=>{
  66 |  const state=await setup(page,context);state.statuses.plan=503;await page.goto(path('strategy'));await expect(page.getByText('策略方案读取失败',{exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'团队知识使用指南',exact:true})).toBeVisible();state.statuses.plan=200;await page.getByRole('button',{name:'重试策略方案'}).click();await expect(page.getByText('按章节组织资料',{exact:true})).toBeVisible();state.statuses.artifacts=503;await tab(page,'解析产物');await expect(page.getByText('解析产物读取失败',{exact:true})).toBeVisible();expect(state.writes).toHaveLength(0)
  67 | })
  68 | 
  69 | for(const width of [390,768,1024,1440])test(`document workbench layout and accessibility at ${width}`,async({page,context},info)=>{
  70 |  await setup(page,context);await page.setViewportSize({width,height:width<1024?844:1000});await page.goto(path());await expect(page.getByRole('heading',{name:'文档摘要',exact:true})).toBeVisible();await page.waitForLoadState('networkidle');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await axe(page);await page.screenshot({path:resolve(output,`${info.project.name}-overview-${width}.png`),fullPage:true,animations:'disabled'})
  71 |  await tab(page,'策略方案');await expect(page.getByText('按章节组织资料',{exact:true})).toBeVisible();await page.waitForLoadState('networkidle');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await axe(page);await page.screenshot({path:resolve(output,`${info.project.name}-strategy-${width}.png`),fullPage:true,animations:'disabled'})
  72 | })
  73 | 
```