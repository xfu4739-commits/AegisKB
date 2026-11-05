import {test,expect} from '@playwright/test'
import {mockAdminAuth,seedAdmin} from './fixtures/admin-auth.js'
import {resolve} from 'node:path'
import AxeBuilder from '@axe-core/playwright'
test('route configuration scope, explicit operator, cancel, save and trace facts',async({page,context})=>{
 const auth=await mockAdminAuth(context),writes=[]
 await context.route('**/manage/**',async route=>{
  const path=new URL(route.request().url()).pathname,body=route.request().postDataJSON();let data=[]
  if(path.endsWith('/base/list'))data=[{id:'a',name:'研发知识库'},{id:'b',name:'另一知识库'}]
  else if(path.endsWith('/scope/list'))data=body.knowledgeBaseId==='a'?[{id:'s',scopeName:'开发流程'}]:[]
  else if(path.endsWith('/topic/list'))data=[{id:'t',scopeId:'s',topicName:'发布'}]
  else if(path.endsWith('/document/page/query'))data={records:[{documentId:'9007199254740993123',knowledgeBaseId:'a',documentName:'开发指南'},{documentId:'outside',knowledgeBaseId:'b',documentName:'其他文档'}]}
  else if(path.endsWith('/trace/page/query'))data={records:[{id:'trace-long',question:'如何发布？',mode:'auto',routeStatus:'1',confidence:'0.8'}],totalSize:'1',totalPages:'1'}
  else if(path.endsWith('/scope/save')){writes.push(body);data={id:'new'}}
  else if(!path.endsWith('/topic/document/list')){await route.abort();throw new Error('unexpected '+path)}
  await route.fulfill({json:{code:'0',data}})
 })
 await seedAdmin(page);await page.goto('/admin/knowledge-route');await page.selectOption('#route-base','a');await expect(page.getByText('开发流程',{exact:true}).first()).toBeVisible()
 await page.getByRole('button',{name:'新建范围'}).click();await page.getByLabel('范围名称',{exact:true}).fill('交付');await page.getByLabel('操作人 ID',{exact:true}).fill('77');await page.getByRole('button',{name:'检查并确认'}).click();await page.getByRole('dialog',{name:'确认操作'}).getByRole('button',{name:'取消'}).click();expect(writes).toEqual([])
 await page.getByRole('button',{name:'检查并确认'}).click();await page.getByRole('dialog',{name:'确认操作'}).getByRole('button',{name:'确定'}).click();await expect.poll(()=>writes.length).toBe(1);expect(writes[0]).toMatchObject({scopeName:'交付',knowledgeBaseId:'a',operatorId:'77'})
 await page.getByRole('button',{name:'文档画像',exact:true}).click();await expect(page.getByText('开发指南',{exact:true})).toBeVisible();await expect(page.getByText('其他文档',{exact:true})).toHaveCount(0)
 await page.selectOption('#route-base','b');await expect(page.getByRole('button',{name:'重建选中画像'})).toBeDisabled()
 await page.goto('/admin/knowledge-route/traces');await page.getByRole('button',{name:'如何发布？'}).click();await expect(page.getByRole('dialog')).toContainText('0.8');await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toBeHidden()
 await page.setViewportSize({width:390,height:844});expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([])
 await page.screenshot({path:resolve(process.env.AEGIS_ROUTE_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A5',test.info().project.name+'-traces-mobile.png'),fullPage:true});expect(auth.blocked).toEqual([])
})


