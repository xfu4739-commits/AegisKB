import {test,expect} from '@playwright/test'
import {mockRoute} from './fixtures/admin-route.js'
import {seedAdmin} from './fixtures/admin-auth.js'
import {resolve} from 'node:path'
import {mockAdminComparison,docId,parseId,indexId,comparisonSession,comparisonExchange,comparisonBase} from './fixtures/admin-comparison.js'
test('knowledge route selector consumes the actual baseName DTO field',async({page,context})=>{
 await mockRoute(context)
 await context.route('**/manage/knowledge/base/list',route=>route.fulfill({json:{code:'0',data:[{id:'a',baseName:'真实字段资料库'}]}}))
 await seedAdmin(page);await page.goto('/admin/knowledge-route');await expect(page.getByRole('option',{name:'真实字段资料库',exact:true})).toBeAttached()
})
const screens=[
 ['dashboard','/admin/dashboard','运营总览'],['quality','/admin/quality-overview','知识运行全景'],['bases','/admin/knowledge-bases','团队资料库'],['documents','/admin/documents','团队知识使用指南'],['document',`/admin/documents/${docId}`,'团队知识使用指南'],['route',`/admin/knowledge-route?kb=${comparisonBase}`,'开发流程'],['traces','/admin/knowledge-route/traces','如何维护团队知识？'],['sessions','/admin/observability','如何维护团队知识？'],['session',`/admin/observability/${comparisonSession}`,'如何维护团队知识？'],['exchange',`/admin/observability/${comparisonSession}/exchanges/${comparisonExchange}`,'如何维护团队知识？'],['configuration','/admin/settings/configuration','45%']
]
for(const width of [390,1440])test('same snapshot admin comparison at '+width,async({page,context})=>{
 test.setTimeout(180000)
 const state=await mockAdminComparison(context),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.setViewportSize({width,height:width===390?844:1000})
 const output=resolve(process.env.AEGIS_FINAL_EVIDENCE_DIR||'../doc/重构前端/evidence/admin/A8/comparison')
 for(const [version,origin,username,password] of [['legacy','http://localhost:5173','#login-username','#login-password'],['next','http://localhost:5174','#admin-username','#admin-password']]){
  await page.goto(origin+'/admin/login');await page.locator(username).fill('');await page.locator(password).fill('');await expect(page.locator(password)).toHaveValue('');await page.screenshot({path:resolve(output,`${test.info().project.name}-login-${version}-${width}.png`),fullPage:true,animations:'disabled'})
 }
 for(const [name,path,text] of screens)for(const [version,origin,main] of [['legacy','http://localhost:5173','#admin-main-content'],['next','http://localhost:5174','#admin-next-main']]){
  await page.goto(origin+path);await expect(page.locator(main).getByText(text,{exact:true}).filter({visible:true}).first()).toBeVisible();await page.waitForLoadState('networkidle');await page.screenshot({path:resolve(output,`${test.info().project.name}-${name}-${version}-${width}.png`),fullPage:true,animations:'disabled'});expect(state.blocked).toEqual([]);expect(errors).toEqual([])
 }
 // The same completed task is opened through each version's actual navigation.
 for(const [version,origin] of [['legacy','http://localhost:5173'],['next','http://localhost:5174']]){
  await page.goto(origin+`/admin/documents/${docId}?section=tasks&indexTaskId=${indexId}`)
  await expect(page.getByText('当前任务已完成',{exact:true}).filter({visible:true}).first()).toBeVisible()
  if(version==='next'){await page.getByRole('button',{name:'查看历史日志',exact:true}).click();await expect(page.getByRole('button',{name:'上一页日志',exact:true})).toBeVisible()}
  await page.waitForLoadState('networkidle');await page.screenshot({path:resolve(output,`${test.info().project.name}-task-${version}-${width}.png`),fullPage:true,animations:'disabled'})
 }
 // Errors use the identical envelope for both versions.
 state.statuses['/manage/document/page/query']=503
 for(const [version,origin] of [['legacy','http://localhost:5173'],['next','http://localhost:5174']]){await page.goto(origin+'/admin/documents');await expect(page.getByText('A8 隔离读取失败',{exact:false}).first()).toBeVisible();await page.screenshot({path:resolve(output,`${test.info().project.name}-error-${version}-${width}.png`),fullPage:true,animations:'disabled'})}
 expect(state.blocked).toEqual([]);expect(errors).toEqual([])
 const hashes=new Map();for(const call of state.calls){const key=call.path+':'+call.status;if(!hashes.has(key))hashes.set(key,new Set());hashes.get(key).add(call.responseHash)}for(const values of hashes.values())expect(values.size).toBe(1)
 for(const path of ['/manage/knowledge/base/list','/manage/document/detail/query','/manage/document/task/log/query','/manage/knowledge/route/trace/page/query','/api/chat/session/detail','/api/chat/exchange/detail','/manage/config/current/query'])expect([...new Set(state.calls.filter(c=>c.path===path).map(c=>c.origin))].sort(),path).toEqual(['http://localhost:5173','http://localhost:5174'])
 await test.info().attach('same-response-audit',{body:JSON.stringify(state.calls,null,2),contentType:'application/json'})
})
test('final administrator navigation keeps chat public and rejects expired server auth',async({page,context})=>{
 const state=await mockAdminComparison(context);await page.goto('/admin/dashboard');await page.getByRole('link',{name:'返回对话',exact:true}).click();await expect(page).toHaveURL(/\/chat$/);await expect(page.getByRole('textbox',{name:'输入问题'})).toBeVisible();await page.goto('/admin/settings/configuration');await expect(page.getByRole('heading',{name:'系统配置',exact:true})).toBeVisible();state.auth=false;await page.reload();await expect(page).toHaveURL(/\/admin\/login/);await page.goto('/chat');await expect(page.getByRole('textbox',{name:'输入问题'})).toBeVisible();expect(state.blocked).toEqual([])
})
