import { test,expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { mockDocument } from './fixtures/admin-document.js'
import { seedAdmin } from './fixtures/admin-auth.js'
import { docId } from './fixtures/document-data.js'
const output=resolve(process.env.AEGIS_DOCUMENT_NAV_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A4/revision-interactions')
const errors=new WeakMap(),states=new WeakMap()
test.beforeEach(async({page,context})=>{errors.set(page,[]);page.on('pageerror',e=>errors.get(page).push(e.message));const state=await mockDocument(context);states.set(page,state);await seedAdmin(page)})
test.afterEach(({page})=>{expect(errors.get(page)).toEqual([]);expect(states.get(page).catalog.auth.blocked).toEqual([]);expect(states.get(page).writes).toEqual([])})
async function tab(page,name){await page.getByRole('navigation',{name:'文档工作台'}).getByRole('button',{name,exact:true}).click()}
async function screenshot(page,name){await page.screenshot({path:resolve(output,`${test.info().project.name}-${name}.png`),animations:'disabled',fullPage:true})}

test('artifact preview has explicit return and next/previous navigation, preserving the list filter',async({page})=>{
 await page.goto(`/admin/documents/${docId}?section=artifacts`)
 await page.getByRole('textbox',{name:'搜索产物文件名'}).fill('parsed')
 const trigger=page.getByRole('button',{name:'预览',exact:true});await trigger.click()
 const dialog=page.getByRole('dialog');await expect(dialog.getByRole('button',{name:'返回产物列表'})).toBeVisible();await screenshot(page,'artifact-return')
 await dialog.getByRole('button',{name:'返回产物列表'}).click();await expect(dialog).toBeHidden();await expect(trigger).toBeFocused()
 await tab(page,'分块');await tab(page,'解析产物');await expect(page.getByRole('textbox',{name:'搜索产物文件名'})).toHaveValue('parsed')
 await page.getByRole('textbox',{name:'搜索产物文件名'}).fill('');await page.getByRole('button',{name:'预览',exact:true}).first().click();await dialog.getByRole('button',{name:'下一个产物'}).click();await expect(dialog.getByRole('img',{name:'page.png'})).toBeVisible();await dialog.getByRole('button',{name:'上一个产物'}).click();await expect(dialog.getByRole('heading',{name:'标准正文'})).toBeVisible();await dialog.getByRole('button',{name:'返回产物列表'}).click();await expect(dialog).toBeHidden()
})

test('chunk content comes first, sibling exploration has a back path and closes to the same page',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto(`/admin/documents/${docId}?section=chunk&chunkPage=2`)
 await page.getByRole('button',{name:'分块详情',exact:true}).click();const dialog=page.getByRole('dialog')
 await expect(dialog.getByRole('heading',{name:'分块正文'})).toBeVisible();await dialog.getByRole('button',{name:'第二块',exact:true}).click();await expect(dialog.getByRole('button',{name:'返回上一分块'})).toBeEnabled();await dialog.getByRole('button',{name:'返回上一分块'}).click();await expect(dialog).toContainText('接入说明');await expect.poll(()=>states.get(page).calls.filter(c=>c.kind==='chunk').at(-1)?.body.chunkId).toBe('9007199254740993444');await screenshot(page,'chunk-return-mobile')
 await dialog.getByRole('button',{name:'返回分块列表'}).click();await expect(dialog).toBeHidden();await expect(page.getByRole('navigation',{name:'分块分页'})).toContainText('第 2 页')
})

test('table browsing survives tab switches and browser Back without losing row/column position',async({page})=>{
 await page.goto(`/admin/documents/${docId}?section=explorer`);await page.selectOption('#artifact-node-type','TABLE');await page.getByRole('button',{name:'费用明细',exact:true}).click();await page.getByRole('button',{name:'后六列'}).click();await expect(page.getByRole('columnheader',{name:'第 7 列'})).toBeVisible();await page.getByRole('button',{name:'下一页表格行'}).click()
 await tab(page,'解析产物');await page.goBack();await expect(page).toHaveURL(/section=explorer/);await expect(page.locator('#artifact-node-type')).toHaveValue('TABLE');await expect(page.getByRole('columnheader',{name:'第 7 列'})).toBeVisible();await expect(page.getByRole('button',{name:'上一页表格行'})).toBeEnabled();await expect.poll(()=>states.get(page).calls.filter(c=>c.kind==='table').at(-1)?.body).toMatchObject({pageNo:'2',columnOffset:'6'});await screenshot(page,'restored-table')
})

test('locating an original page offers a return to the same selected node and browse path',async({page})=>{
 await page.goto(`/admin/documents/${docId}?section=explorer`);await page.getByRole('button',{name:'文档章节',exact:true}).first().click();await expect(page.getByText('节点正文内容',{exact:true})).toBeVisible();await page.getByRole('button',{name:'定位原页'}).click();await expect(page.getByRole('img',{name:'第 1 页'})).toBeVisible()
 await page.getByRole('button',{name:'返回产物与关系',exact:true}).click();await expect(page.getByRole('region',{name:'选中节点'})).toContainText('节点正文内容');await page.getByRole('button',{name:'返回节点列表',exact:true}).click();await expect(page.getByRole('region',{name:'选中节点'})).toHaveCount(0);await expect(page.getByRole('heading',{name:'节点列表',exact:true})).toBeFocused()
 expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([])
})

