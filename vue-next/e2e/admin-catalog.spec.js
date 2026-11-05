import { test,expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { mockCatalog } from './fixtures/admin-catalog.js'
import { bases,baseId,documents } from './fixtures/catalog-data.js'
import { seedAdmin } from './fixtures/admin-auth.js'
const output=resolve(process.env.AEGIS_CATALOG_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A3')
const errors=new WeakMap(),states=new WeakMap()
test.beforeEach(async({page})=>{errors.set(page,[]);page.on('pageerror',e=>errors.get(page).push(e.message));await page.clock.setFixedTime(new Date('2026-11-04T09:30:00+08:00'))})
test.afterEach(({page})=>{expect(errors.get(page)).toEqual([]);expect(states.get(page)?.auth.blocked || []).toEqual([])})
async function setup(page,context,scene){const state=await mockCatalog(context,scene);states.set(page,state);await seedAdmin(page);return state}
async function settledModal(page){const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await expect.poll(()=>page.locator('.admin-next-overlay-host').evaluate(host=>host.getAnimations({subtree:true}).every(a=>a.playState!=='running'&&!a.pending))).toBe(true);return dialog}
async function axe(page){expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([])}
const row=(page,name)=>page.locator('.admin-next-catalog-list article').filter({hasText:name})

test('base details are read-only; edited config preserves extensions and only saves once',async({page,context})=>{
 const state=await setup(page,context);await page.goto('/admin/knowledge-bases')
 await row(page,'团队资料库').getByRole('button',{name:'团队资料库',exact:true}).click();const dialog=await settledModal(page)
 await expect(dialog.getByLabel('知识库名称',{exact:true})).toBeDisabled();await expect(dialog).toContainText(baseId);await axe(page)
 await dialog.getByRole('button',{name:'编辑',exact:true}).click();await dialog.getByLabel('知识库名称',{exact:true}).fill('团队资料库新版')
 await dialog.getByText('检索与通道',{exact:true}).click();await dialog.getByLabel('向量 topK',{exact:true}).fill('20')
 state.delay=200;await dialog.getByRole('button',{name:'保存',exact:true}).dblclick()
 await expect(dialog).toBeHidden();expect(state.writes).toHaveLength(1)
 const sent=state.writes[0].body;expect(sent.id).toBe(baseId);expect(sent.operatorId).toBeUndefined()
 expect(JSON.parse(sent.retrievalConfigJson)).toMatchObject({vectorTopK:20,vendorExtension:{value:17},hybrid:{customWeight:.27}})
 await expect(row(page,'团队资料库新版')).toBeVisible()
})

test('new base validates, cancel never writes, failed save retains draft and can retry',async({page,context})=>{
 const state=await setup(page,context);await page.goto('/admin/knowledge-bases')
 await page.getByRole('button',{name:'新建知识库',exact:true}).click();let dialog=await settledModal(page)
 await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog).toContainText('知识库名称不能为空');expect(state.writes).toHaveLength(0)
 await dialog.getByLabel('知识库名称',{exact:true}).fill('隔离新库');state.writeStatus=503
 await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog.getByRole('alert')).toContainText('隔离场景：操作失败');await expect(dialog.getByLabel('知识库名称',{exact:true})).toHaveValue('隔离新库')
 state.writeStatus=200;await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog).toBeHidden();await expect(row(page,'隔离新库')).toBeVisible()
 await page.getByRole('button',{name:'新建知识库',exact:true}).click();dialog=await settledModal(page);await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(state.writes).toHaveLength(2)
})

test('base deletion confirms exact target, explains disable-only, and preserves on failure',async({page,context})=>{
 const state=await setup(page,context);await page.goto('/admin/knowledge-bases')
 const remove=row(page,'产品手册').getByRole('button',{name:'删除知识库 产品手册'});await remove.click();let dialog=await settledModal(page);await expect(dialog).toContainText('已有文档不会');await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(state.writes).toHaveLength(0);await expect(remove).toBeFocused()
 await remove.click();dialog=await settledModal(page);state.writeStatus=500;await dialog.getByRole('button',{name:'确认停用'}).click();await expect(dialog.getByRole('alert')).toContainText('操作失败');await expect(row(page,'产品手册')).toBeVisible()
 state.writeStatus=200;await dialog.getByRole('button',{name:'确认停用'}).click();await expect(dialog).toBeHidden();await expect(row(page,'产品手册')).toHaveCount(0);expect(state.writes[1].body).toEqual({id:bases[1].id})
})

test('list filters and pagination survive URL reload/back and late requests cannot replace new scope',async({page,context})=>{
 const state=await setup(page,context);await page.goto('/admin/documents?page=2&size=12')
 await expect(row(page,'项目文档 13')).toBeVisible();expect(state.calls.find(c=>c.path.endsWith('/page/query')).body).toEqual({keyword:'',pageNo:'2',pageSize:'12'})
 await page.getByRole('textbox',{name:'搜索文档'}).fill('团队');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(row(page,'团队知识使用指南')).toBeVisible();await expect(page).toHaveURL(/q=/);await page.reload();await expect(page.getByRole('textbox',{name:'搜索文档'})).toHaveValue('团队')
 state.queryDelays['项目']=300;await page.getByRole('textbox',{name:'搜索文档'}).fill('项目');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect.poll(()=>state.calls.some(c=>c.body?.keyword==='项目')).toBe(true)
 await page.getByRole('textbox',{name:'搜索文档'}).fill('团队');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect.poll(()=>state.completed.some(c=>c.body?.keyword==='项目')).toBe(true);await expect(page.locator('.admin-next-catalog-list article')).toHaveCount(1)
 await page.getByRole('button',{name:'清除筛选'}).click();await expect(page.locator('.admin-next-catalog-list article')).toHaveCount(12);await page.goBack();await expect(page.getByRole('textbox',{name:'搜索文档'})).toHaveValue('团队')
 await page.goto('/admin/knowledge-bases?q=产品&page=99');await expect(row(page,'产品手册')).toBeVisible();await expect(page.getByRole('navigation',{name:'知识库分页'})).toContainText('共 1 个');await expect(page).not.toHaveURL(/page=99/)
})

test('document deletion refuses active tasks and confirms one string ID; last-page deletion moves back',async({page,context})=>{
 const state=await setup(page,context);await page.goto('/admin/documents')
 await expect(row(page,'项目文档 2').getByRole('button',{name:'删除文档 项目文档 2',exact:true})).toBeDisabled()
 const button=row(page,'团队知识使用指南').getByRole('button',{name:'删除文档 团队知识使用指南'});await button.click();let dialog=await settledModal(page);await expect(dialog).toContainText(documents[0].documentId);await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(state.writes).toHaveLength(0)
 await button.click();dialog=await settledModal(page);state.writeStatus=500;await dialog.getByRole('button',{name:'确认删除'}).click();await expect(dialog.getByRole('alert')).toContainText('操作失败');state.writeStatus=200;await dialog.getByRole('button',{name:'确认删除'}).click();await expect(dialog).toBeHidden();expect(state.writes[1].body).toEqual({documentId:documents[0].documentId})
 state.documents=state.documents.slice(0,13);await page.goto('/admin/documents?page=2&size=12');await expect(page.locator('.admin-next-catalog-list article')).toHaveCount(1);await page.locator('.admin-next-catalog-list article').getByRole('button',{name:/删除文档/}).click();dialog=await settledModal(page);await dialog.getByRole('button',{name:'确认删除'}).click();await expect(dialog).toBeHidden();await expect(page.getByRole('navigation',{name:'文档分页'})).toContainText('第 1 / 1 页')
})

test('upload is explicit multipart, never defaults scope/operator, retains file on failure and sends once',async({page,context})=>{
 const state=await setup(page,context);await page.goto('/admin/documents');await page.getByRole('button',{name:'上传文档',exact:true}).click();const dialog=await settledModal(page)
 await expect(dialog.getByLabel('所属知识库',{exact:true})).toHaveValue('');await expect(dialog.getByLabel('操作人 ID',{exact:true})).toHaveValue('')
 await dialog.getByRole('button',{name:'上传并解析'}).click();await expect(dialog.getByRole('alert')).toContainText('请选择有效的知识库');expect(state.writes).toHaveLength(0)
 await dialog.getByLabel('所属知识库',{exact:true}).selectOption(baseId);await dialog.getByLabel('操作人 ID',{exact:true}).fill('9007199254740993123');await dialog.getByLabel('文档名称（可选）',{exact:true}).fill('新资料')
 await dialog.getByLabel('文件',{exact:true}).setInputFiles({name:'upload.md',mimeType:'text/markdown',buffer:Buffer.from('# test')});await axe(page)
 state.writeStatus=503;await dialog.getByRole('button',{name:'上传并解析'}).click();await expect(dialog.getByRole('alert')).toContainText('操作失败');expect(await dialog.getByLabel('文件',{exact:true}).evaluate(el=>el.files[0]?.name)).toBe('upload.md')
 state.writeStatus=200;state.delay=200;await dialog.getByRole('button',{name:'上传并解析'}).dblclick();await expect(dialog).toBeHidden();expect(state.writes).toHaveLength(2)
 const call=state.writes[1];expect(call.body).toEqual({documentName:'新资料',knowledgeBaseId:baseId,operatorId:'9007199254740993123'});expect(call.fileName).toBe('upload.md');expect(call.metaJson).toBe(true);expect(call.multipart).toContain('boundary=')
 await expect(page.getByRole('status')).toContainText('解析和索引尚未完成');await expect(page.getByRole('link',{name:'任务详情'})).toHaveAttribute('href',/parseTaskId/)
})

test('empty data, independent knowledge-option failure and stale refresh are truthful',async({page,context})=>{
 const state=await setup(page,context,'empty');await page.goto('/admin/knowledge-bases');await expect(page.getByText('暂无知识库',{exact:true})).toBeVisible();await page.goto('/admin/documents');await expect(page.getByText('暂无文档',{exact:true})).toBeVisible()
 state.optionsStatus=503;await page.getByRole('button',{name:'上传文档',exact:true}).click();const dialog=await settledModal(page);await expect(dialog).toContainText('知识库选项读取失败');await expect(dialog.getByRole('button',{name:'上传并解析'})).toBeDisabled();state.optionsStatus=200;state.bases=structuredClone(bases);await dialog.getByRole('button',{name:'重试知识库'}).click();await expect(dialog.getByLabel('所属知识库',{exact:true})).toBeEnabled();await expect(dialog.getByLabel('所属知识库',{exact:true})).toHaveValue('');await dialog.getByRole('button',{name:'取消',exact:true}).click()
 state.documents=structuredClone(documents);await page.getByRole('button',{name:'刷新',exact:true}).click();await expect(row(page,'团队知识使用指南')).toBeVisible();state.listStatus=503;await page.getByRole('button',{name:'刷新',exact:true}).click();await expect(page.getByText('刷新失败，仍显示上次列表',{exact:true})).toBeVisible();await expect(row(page,'团队知识使用指南')).toBeVisible()
})

for(const width of [390,768,1024,1440]) test(`catalog layouts and dialogs at ${width}`,async({page,context},info)=>{
 await setup(page,context);await page.setViewportSize({width,height:width<1024?844:1000})
 for(const [path,title,name] of [['knowledge-bases','知识库管理','bases'],['documents','文档管理','documents']]){
  await page.goto(`/admin/${path}`);await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible();await expect(page.locator('.admin-next-catalog-list article').first()).toBeVisible();await page.waitForLoadState('networkidle');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await axe(page);await page.screenshot({path:resolve(output,`${info.project.name}-${name}-${width}.png`),fullPage:true,animations:'disabled'})
  await page.getByRole('button',{name:path==='documents'?'上传文档':'新建知识库',exact:true}).click();const dialog=await settledModal(page);await axe(page);expect(await dialog.evaluate(el=>el.getBoundingClientRect().right<=innerWidth && el.getBoundingClientRect().left>=0)).toBe(true);await page.screenshot({path:resolve(output,`${info.project.name}-${name}-dialog-${width}.png`),fullPage:true,animations:'disabled'});await page.keyboard.press('Escape');await expect(dialog).toBeHidden()
 }
})
