import {test,expect} from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import {resolve} from 'node:path'
import {mockRoute,routeDocId} from './fixtures/admin-route.js'
import {seedAdmin} from './fixtures/admin-auth.js'
const states=new WeakMap(),errors=new WeakMap()
test.beforeEach(async({page,context})=>{states.set(page,await mockRoute(context));errors.set(page,[]);page.on('pageerror',e=>errors.get(page).push(e.message));await seedAdmin(page)})
test.afterEach(({page})=>{expect(states.get(page).blocked).toEqual([]);expect(states.get(page).auth.blocked).toEqual([]);expect(errors.get(page)).toEqual([])})
const dialog=page=>page.getByRole('dialog',{name:'路由配置操作',exact:true})
async function prepare(page){await dialog(page).getByLabel('操作人 ID',{exact:true}).fill('77');await dialog(page).getByRole('button',{name:'检查并确认'}).click()}
async function confirm(page){await page.getByRole('dialog',{name:'确认操作',exact:true}).getByRole('button',{name:'确定',exact:true}).click()}
test('scope and topic filters, loaded pagination, browser Back and reload restore URL state',async({page})=>{
 await page.goto('/admin/knowledge-route?kb=a');await page.getByRole('button',{name:'下一页配置'}).click();await expect(page.getByText('范围 24',{exact:true}).first()).toBeVisible();await page.reload();await expect(page.getByText('范围 24',{exact:true}).first()).toBeVisible()
 await page.getByRole('button',{name:'主题',exact:true}).click();await page.selectOption('#route-filter-scope','s0');await expect(page.getByText('发布',{exact:true}).first()).toBeVisible();await page.selectOption('#route-filter-scope','s1');await expect(page.getByText('测试',{exact:true}).first()).toBeVisible();await page.goBack();await expect(page.locator('#route-filter-scope')).toHaveValue('s0')
 await page.getByRole('button',{name:'文档关联',exact:true}).click();await page.selectOption('#route-filter-topic','t0');await page.selectOption('#route-filter-scope','s1');await expect(page.locator('#route-filter-topic')).toHaveValue('');expect(states.get(page).writes).toEqual([])
})
test('switching KB or clearing KB rejects delayed old data and clears selections',async({page})=>{
 const state=states.get(page);state.baseDelays.a=500;await page.goto('/admin/knowledge-route?kb=a');await page.selectOption('#route-base','b');await expect(page.getByText('产品范围',{exact:true}).first()).toBeVisible();await page.waitForTimeout(550);await expect(page.getByText('开发流程',{exact:true})).toHaveCount(0)
 await page.getByRole('button',{name:'文档画像',exact:true}).click();await page.getByRole('checkbox',{name:'产品手册'}).check();await page.selectOption('#route-base','');await expect(page.getByText('选择知识库后查看配置')).toBeVisible();expect(state.writes).toEqual([])
})
test('batch confirmation freezes exact selected IDs, cancel is read-only, failure retains draft and duplicate submit locks',async({page})=>{
 const state=states.get(page);await page.goto('/admin/knowledge-route?kb=a&tab=profiles');await page.getByRole('checkbox',{name:'开发指南'}).check();await page.getByRole('button',{name:'重建选中画像'}).click();await prepare(page);const confirmation=page.getByRole('dialog',{name:'确认操作'});await expect(confirmation).toContainText(routeDocId);await confirmation.getByRole('button',{name:'取消',exact:true}).click();expect(state.writes).toEqual([])
 state.writeStatus=500;await dialog(page).getByRole('button',{name:'检查并确认'}).click();await confirm(page);await expect(dialog(page).getByRole('alert')).toContainText('隔离接口失败');await expect(dialog(page).getByLabel('操作人 ID')).toHaveValue('77')
 state.writeStatus=200;state.writeDelay=400;await dialog(page).getByRole('button',{name:'检查并确认'}).click();await confirm(page);await expect(page.getByRole('dialog',{name:'确认操作'}).getByRole('button',{name:'确定'})).toBeDisabled();await expect(dialog(page)).toBeHidden();expect(state.writes).toHaveLength(2);expect(state.writes[1].body).toEqual({documentIds:[routeDocId],operatorId:'77'})
})
test('topic fields, relation save/remove and explicit deletion keep original protocol',async({page})=>{
 const state=states.get(page);await page.goto('/admin/knowledge-route?kb=a&tab=topics&scope=s0');await page.getByRole('button',{name:'编辑',exact:true}).click();await expect(dialog(page).getByLabel('回答结构')).toHaveValue('steps');await expect(dialog(page).getByLabel('执行偏好')).toHaveValue('hybrid');await prepare(page);await confirm(page);await expect(dialog(page)).toBeHidden();expect(state.writes.at(-1).body).toMatchObject({id:'t0',scopeId:'s0',answerShape:'steps',executionPreference:'hybrid',knowledgeBaseId:'a'})
 await page.getByRole('button',{name:'文档关联',exact:true}).click();await page.getByRole('button',{name:'新增关联'}).click();await dialog(page).getByLabel('主题',{exact:true}).selectOption('t0');await dialog(page).getByLabel('文档',{exact:true}).selectOption(routeDocId);await dialog(page).getByLabel('关联分数').fill('0.7');await prepare(page);await confirm(page);await expect(dialog(page)).toBeHidden();expect(state.writes.at(-1).body).toMatchObject({topicId:'t0',documentId:routeDocId,relationScore:'0.7',relationSource:'manual'})
 await page.getByRole('button',{name:'移除关联'}).click();await prepare(page);await confirm(page);await expect(dialog(page)).toBeHidden();expect(state.writes.at(-1).key).toBe('topic/document/remove')
 await page.getByRole('button',{name:'知识范围',exact:true}).click();await page.getByRole('button',{name:'删除',exact:true}).first().click();await prepare(page);await confirm(page);await expect(dialog(page)).toBeHidden();expect(state.writes.at(-1).body).toMatchObject({id:'s0',knowledgeBaseId:'a',operatorId:'77'})
})
test('trace candidates preserve zeros and IDs, malformed JSON stays diagnostic, source links are read-only, Back restores filters',async({page})=>{
 await page.goto('/admin/knowledge-route/traces?conversationId=new&mode=shadow&page=2');const trigger=page.getByRole('button',{name:'new',exact:true});await trigger.click();const detail=page.getByRole('dialog',{name:'追踪详情'});await expect(detail).toContainText('候选数据格式异常');await expect(detail).toContainText('分数 0');await expect(detail.getByRole('link',{name:'查看文档'})).toHaveAttribute('href','/admin/documents/'+routeDocId);await expect(detail.getByRole('link',{name:'查看会话来源'})).toHaveAttribute('href',/observability\/new\/exchanges\/exchange-long/)
 await page.goBack();await expect(detail).toBeHidden();await expect(page.locator('#trace-conversation')).toHaveValue('new');await expect(page.getByRole('navigation',{name:'追踪分页'})).toContainText('第 2 页');expect(states.get(page).writes).toEqual([])
})
for(const width of [390,768,1024,1440])test('route layout, keyboard return and accessibility at '+width,async({page})=>{
 await page.setViewportSize({width,height:900});await page.goto('/admin/knowledge-route?kb=a');const trigger=page.getByRole('button',{name:'新建范围'});await trigger.click();await expect(dialog(page)).toBeVisible();await page.keyboard.press('Escape');await expect(dialog(page)).toBeHidden();await expect(trigger).toBeFocused();await page.waitForTimeout(350);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:resolve(process.env.AEGIS_ROUTE_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A5',`${test.info().project.name}-config-${width}.png`),fullPage:true,animations:'disabled'})
 await page.goto('/admin/knowledge-route/traces');await page.getByRole('button',{name:'如何发布？'}).click();await page.waitForTimeout(350);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);await page.screenshot({path:resolve(process.env.AEGIS_ROUTE_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A5',`${test.info().project.name}-trace-detail-${width}.png`),fullPage:true,animations:'disabled'})
})



