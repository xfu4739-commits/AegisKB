import { test,expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { readFile } from 'node:fs/promises'
import { mockDocument } from './fixtures/admin-document.js'
import { seedAdmin } from './fixtures/admin-auth.js'
import { docId,parseId,indexId,planId,log } from './fixtures/document-data.js'
const output=resolve(process.env.AEGIS_DOCUMENT_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A4'),errors=new WeakMap(),states=new WeakMap()
test.beforeEach(async({page})=>{errors.set(page,[]);page.on('pageerror',e=>errors.get(page).push(e.message));await page.clock.setFixedTime(new Date('2026-11-04T09:30:00+08:00'))})
test.afterEach(({page})=>{expect(errors.get(page)).toEqual([]);expect(states.get(page)?.catalog.auth.blocked||[]).toEqual([])})
async function setup(page,context){const state=await mockDocument(context);states.set(page,state);await seedAdmin(page);return state}
const path=section=>`/admin/documents/${docId}${section?`?section=${section}`:''}`
async function tab(page,name){await page.getByRole('navigation',{name:'文档工作台'}).getByRole('button',{name,exact:true}).click()}
async function settledModal(page){const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await expect.poll(()=>page.locator('.admin-next-overlay-host').evaluate(el=>el.getAnimations({subtree:true}).every(a=>a.playState!=='running'&&!a.pending))).toBe(true);return dialog}
async function capture(page,name){await page.screenshot({path:resolve(output,`${test.info().project.name}-${name}.png`),fullPage:true,animations:'disabled'})}
async function axe(page){await expect.poll(()=>page.evaluate(()=>document.getAnimations().filter(a=>a.effect?.getComputedTiming().iterations!==Infinity).every(a=>a.playState!=='running'&&!a.pending))).toBe(true);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([])}

test('detail is lazy, keeps list position and rejects late document responses',async({page,context})=>{
 const state=await setup(page,context);await page.goto(path()+'?listQ=项目&listPage=2&listSize=12')
 await expect(page.getByRole('heading',{name:'团队知识使用指南',exact:true})).toBeVisible();expect(state.calls.map(c=>c.kind)).toEqual(['detail'])
 await expect(page.getByRole('link',{name:'← 文档列表'})).toHaveAttribute('href',/page=2/);await expect(page.getByRole('link',{name:'文档管理',exact:true})).toHaveAttribute('aria-current','page');await axe(page);await page.goto(path('execution'));await expect(page.locator('#doc-task-kind')).toHaveValue('build')
 state.delays.detail=300;await page.goto(path()+'?section=strategy');await page.goto('/admin/documents/9007199254740993001');await expect(page.getByRole('heading',{name:'第二份文档'})).toBeVisible();await expect(page.locator('.admin-next-doc-id').first()).toContainText('9007199254740993001');expect(state.writes).toHaveLength(0)
})

test('strategy preserves order, blocks dirty builds, validates operator and confirms exact target',async({page,context})=>{
 const state=await setup(page,context);await page.goto(path('strategy'))
 await expect(page.getByText('按章节组织资料',{exact:true})).toBeVisible();await page.getByRole('button',{name:'确认策略',exact:true}).click();await expect(page.getByRole('alert')).toContainText('操作人 ID');expect(state.writes).toHaveLength(0)
 await page.getByLabel('操作人 ID',{exact:true}).fill('42');const parent=page.locator('.admin-next-doc-columns > div').first();await parent.getByRole('checkbox',{name:'递归分块',exact:true}).check();await page.getByRole('button',{name:'parent 2 上移'}).click();await expect(page.getByRole('button',{name:'构建索引',exact:true})).toBeDisabled()
 await page.getByRole('button',{name:'确认策略',exact:true}).click();let dialog=await settledModal(page);await expect(dialog).toContainText(planId);await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(state.writes).toHaveLength(0)
 await page.getByRole('button',{name:'确认策略',exact:true}).click();dialog=await settledModal(page);state.statuses.confirm=503;await dialog.getByRole('button',{name:'确认提交'}).click();await expect(dialog.getByRole('alert')).toContainText('文档接口失败');await expect(parent.getByRole('checkbox',{name:'递归分块',exact:true})).toBeChecked()
 state.statuses.confirm=200;state.delays.confirm=150;await dialog.getByRole('button',{name:'确认提交'}).dblclick();await expect(dialog).toBeHidden();expect(state.writes.filter(c=>c.kind==='confirm')).toHaveLength(2);expect(state.writes[1].body).toMatchObject({documentId:docId,basePlanId:planId,operatorId:'42',parentSteps:[{stepNo:'1',strategyType:'2'},{stepNo:'2',strategyType:'1'}]})
 await expect(page.getByRole('button',{name:'构建索引',exact:true})).toBeEnabled();await page.getByRole('button',{name:'构建索引',exact:true}).click();dialog=await settledModal(page);await expect(dialog).toContainText('将为此文档创建索引构建任务');await dialog.getByRole('button',{name:'确认提交'}).click();await expect(page.getByRole('heading',{name:'任务与日志',exact:true})).toBeVisible();expect(state.writes.at(-1)).toEqual({kind:'index',body:{documentId:docId,planId,operatorId:'42'}})
})

test('tasks poll sequentially with precise cursors, stop at terminal, and history pages stay scoped',async({page,context})=>{
 const state=await setup(page,context);state.buildStatuses=['2','3'];await page.goto(path('tasks'));await expect(page.getByText('当前任务已完成',{exact:true})).toBeVisible();await expect.poll(()=>state.calls.filter(c=>c.kind==='build').length).toBe(2)
 expect(state.calls.filter(c=>c.kind==='build')[1].body).toMatchObject({taskId:indexId,sinceLogId:log.id});await page.getByRole('button',{name:'查看历史日志',exact:true}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='logs')).toBe(true);await page.getByRole('button',{name:'下一页日志'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='logs'&&c.body.pageNo==='2')).toBe(true)
 state.parseStatuses=['5'];await page.selectOption('#doc-task-kind','parse');await page.getByRole('textbox',{name:'任务 ID',exact:true}).fill(parseId);await page.getByRole('button',{name:'查看任务',exact:true}).click();await expect(page.getByText('已取消',{exact:true})).toBeVisible();expect(state.calls.filter(c=>c.kind==='parse').at(-1).body.taskId).toBe(parseId)
 await capture(page,'tasks');await tab(page,'文档概览');expect(state.writes).toHaveLength(0)
})

test('artifact preview is safe, releases image URLs and authenticated JSON downloads remain files',async({page,context})=>{
 const state=await setup(page,context);await page.addInitScript(()=>{const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);window.createdUrls=[];window.revokedUrls=[];URL.createObjectURL=value=>{const url=create(value);window.createdUrls.push(url);return url};URL.revokeObjectURL=url=>{window.revokedUrls.push(url);revoke(url)}})
 await page.goto(path('artifacts'));await page.locator('.admin-next-doc-items li').filter({hasText:'parsed.md'}).getByRole('button',{name:'预览'}).click();let dialog=await settledModal(page);await expect(dialog.getByRole('heading',{name:'标准正文'})).toBeVisible();expect(await page.evaluate(()=>window.artifactInjected)).toBeUndefined();expect(await dialog.locator('img').count()).toBe(0);await axe(page);await capture(page,'artifact-preview');await page.keyboard.press('Escape');await expect(dialog).toBeHidden()
 await page.locator('.admin-next-doc-items li').filter({hasText:'page.png'}).getByRole('button',{name:'预览'}).click();dialog=await settledModal(page);await expect(dialog.getByRole('img',{name:'page.png'})).toBeVisible();await expect.poll(()=>dialog.evaluate(element=>element.contains(document.activeElement))).toBe(true);await page.keyboard.press('Escape');await expect(dialog).toBeHidden();await expect.poll(()=>page.evaluate(()=>window.revokedUrls.length)).toBe(1)
 const [download]=await Promise.all([page.waitForEvent('download'),page.locator('.admin-next-doc-items li').filter({hasText:'parsed.md'}).getByRole('button',{name:'下载'}).click()]);expect(download.suggestedFilename()).toBe('artifact.json');expect(await readFile(await download.path(),'utf8')).toBe('{"safe":true}');expect(state.downloads).toBe(1);expect(state.writes).toHaveLength(0)
})

test('chunks paginate on the server and parent/sibling details preserve long identities',async({page,context})=>{
 const state=await setup(page,context);await page.goto(path('chunk'));await page.getByRole('button',{name:'下一页分块'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='chunks'&&c.body.pageNo==='2')).toBe(true);await page.getByRole('button',{name:'分块详情',exact:true}).click();const dialog=await settledModal(page);await expect(dialog).toContainText('父块完整内容');await capture(page,'chunk-detail');state.delays.chunk=300;await dialog.getByRole('button',{name:'第二块',exact:true}).click();await expect.poll(()=>state.calls.filter(c=>c.kind==='chunk').at(-1).body.chunkId).toBe('9007199254740993445');await page.keyboard.press('Escape');await expect(dialog).toBeHidden()
})

test('snapshot deep links, diagnostics and page overlays are read-only and correctly scoped',async({page,context})=>{
 const state=await setup(page,context);await page.goto(path('rag')+'&highlightTableId=9007199254740993333&highlightRows=1&highlightColumns=金额&highlightCells=1:2');await expect(page.getByRole('heading',{name:'费用明细'})).toBeVisible();expect(state.calls.find(c=>c.kind==='snapshot').body).toMatchObject({highlightTableId:'9007199254740993333',highlightRowNos:['1'],highlightColumnNames:['金额'],highlightCellCoordinates:['1:2']});await expect(page.locator('.admin-next-doc-highlight').first()).toBeVisible()
 await page.selectOption('#parse-mode','diagnostic');await expect(page.getByText('解析器',{exact:true})).toBeVisible();await page.selectOption('#parse-mode','locator');await page.selectOption('#overlay-page','1');await expect(page.getByRole('img',{name:'第 1 页'})).toBeVisible();await page.getByRole('button',{name:'定位 正文区域'}).click();await expect(page.getByText('原页内容',{exact:true})).toBeVisible();await axe(page);await capture(page,'page-overlay');expect(state.calls.find(c=>c.kind==='page').body).toMatchObject({parseTaskId:parseId,pageNo:'1'});expect(state.writes).toHaveLength(0)
})

test('explorer reads bounded nodes, relations, graph/table windows and RAPTOR branches',async({page,context})=>{
 const state=await setup(page,context);await page.goto(path('explorer'));await page.getByRole('button',{name:'文档章节',exact:true}).first().click();await expect(page.getByText('节点正文内容',{exact:true})).toBeVisible();await page.getByRole('button',{name:'下一页关系'}).first().click();await expect.poll(()=>state.calls.some(c=>c.kind==='relations'&&c.body.pageNo==='2')).toBe(true)
 await page.selectOption('#artifact-node-type','TABLE');await page.getByRole('button',{name:'费用明细',exact:true}).click();await expect(page.getByRole('region',{name:'表格窗口',exact:true})).toContainText('单元格内容');await page.getByRole('button',{name:'后六列'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='table'&&c.body.columnOffset==='6')).toBe(true);await page.getByRole('button',{name:'下一页表格行'}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='table'&&c.body.pageNo==='2')).toBe(true)
 await page.selectOption('#artifact-node-type','RAPTOR_NODE');await page.getByRole('button',{name:'章节摘要',exact:true}).first().click();await expect.poll(()=>state.calls.some(c=>c.kind==='nodes'&&c.body.parentNodeId==='9007199254740993666')).toBe(true);await page.getByRole('button',{name:'章节摘要',exact:true}).last().click();await expect(page.getByRole('button',{name:'返回上一节点'})).toBeVisible();await page.getByRole('button',{name:'返回上一节点'}).click()
 await page.getByRole('button',{name:'图谱窗口',exact:true}).click();await expect(page.getByRole('img',{name:/当前图谱窗口/})).toBeVisible();expect(state.calls.find(c=>c.kind==='graph').body).toMatchObject({parseTaskId:parseId,indexTaskId:indexId,maxNodes:'80',maxEdges:'160',includeIsolates:true});await capture(page,'graph-window');await page.getByRole('button',{name:'项目实体',exact:true}).click();await expect.poll(()=>state.calls.some(c=>c.kind==='nodes'&&c.body.nodeType==='KG_EVIDENCE')).toBe(true);await page.getByRole('heading',{name:'图谱证据',exact:true}).locator('..').getByRole('button',{name:'文档章节',exact:true}).first().click();await page.getByRole('button',{name:'定位原页'}).click();await expect(page.getByRole('img',{name:'第 1 页'})).toBeVisible();expect(state.writes).toHaveLength(0)
})

test('partial failures retain the document and drafts, retries never trigger real work',async({page,context})=>{
 const state=await setup(page,context);state.statuses.plan=503;await page.goto(path('strategy'));await expect(page.getByText('策略方案读取失败',{exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'团队知识使用指南',exact:true})).toBeVisible();state.statuses.plan=200;await page.getByRole('button',{name:'重试策略方案'}).click();await expect(page.getByText('按章节组织资料',{exact:true})).toBeVisible();state.statuses.artifacts=503;await tab(page,'解析产物');await expect(page.getByText('解析产物读取失败',{exact:true})).toBeVisible();expect(state.writes).toHaveLength(0)
})

for(const width of [390,768,1024,1440])test(`document workbench layout and accessibility at ${width}`,async({page,context},info)=>{
 await setup(page,context);await page.setViewportSize({width,height:width<1024?844:1000});await page.goto(path());await expect(page.getByRole('heading',{name:'文档摘要',exact:true})).toBeVisible();await page.waitForLoadState('networkidle');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await axe(page);await page.screenshot({path:resolve(output,`${info.project.name}-overview-${width}.png`),fullPage:true,animations:'disabled'})
 await tab(page,'策略方案');await expect(page.getByText('按章节组织资料',{exact:true})).toBeVisible();await page.waitForLoadState('networkidle');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await axe(page);await page.screenshot({path:resolve(output,`${info.project.name}-strategy-${width}.png`),fullPage:true,animations:'disabled'})
})
