# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admin-final.spec.js >> same snapshot admin comparison at 390
- Location: e2e\admin-final.spec.js:14:31

# Error details

```
Error: /manage/document/task/log/query

expect(received).toEqual(expected) // deep equality

- Expected  - 1
+ Received  + 0

  Array [
    "http://localhost:5173",
-   "http://localhost:5174",
  ]
```

# Page snapshot

```yaml
- generic [ref=e5]:
  - link "跳到主要内容" [ref=e6] [cursor=pointer]:
    - /url: "#admin-next-main"
  - generic [ref=e7]:
    - banner [ref=e8]:
      - generic [ref=e9]:
        - button "打开后台导航" [ref=e10] [cursor=pointer]:
          - img [ref=e11]
        - navigation [ref=e13]:
          - list [ref=e14]:
            - listitem [ref=e15]:
              - link "管理后台" [ref=e17] [cursor=pointer]:
                - /url: /admin
              - text: /
            - listitem [ref=e18]:
              - generic [ref=e19]: 文档管理
      - generic [ref=e20]:
        - generic [ref=e21]: A8-fixture-admin
        - button "退出登录" [ref=e22] [cursor=pointer]:
          - img [ref=e23]
    - main [ref=e25]:
      - region "文档管理" [ref=e26]:
        - generic [ref=e27]:
          - heading "文档管理" [level=1] [ref=e28]
          - button "上传文档" [ref=e29] [cursor=pointer]:
            - generic [ref=e30]: 上传文档
        - search "文档筛选" [ref=e31]:
          - generic [ref=e32]: 搜索文档
          - textbox "搜索文档" [ref=e33]:
            - /placeholder: 文档名称或文件名
          - button "搜索" [ref=e34] [cursor=pointer]:
            - generic [ref=e35]: 搜索
          - button "刷新" [ref=e36] [cursor=pointer]:
            - generic [ref=e37]: 刷新
        - alert [ref=e38]:
          - img "close-circle" [ref=e39]:
            - img [ref=e40]
          - generic [ref=e42]:
            - generic [ref=e43]: 文档列表读取失败
            - generic [ref=e44]: A8 隔离读取失败
          - button "重试列表" [ref=e46] [cursor=pointer]:
            - generic [ref=e47]: 重试列表
```

# Test source

```ts
  1  | import {test,expect} from '@playwright/test'
  2  | import {mockRoute} from './fixtures/admin-route.js'
  3  | import {seedAdmin} from './fixtures/admin-auth.js'
  4  | import {resolve} from 'node:path'
  5  | import {mockAdminComparison,docId,parseId,indexId,comparisonSession,comparisonExchange,comparisonBase} from './fixtures/admin-comparison.js'
  6  | test('knowledge route selector consumes the actual baseName DTO field',async({page,context})=>{
  7  |  await mockRoute(context)
  8  |  await context.route('**/manage/knowledge/base/list',route=>route.fulfill({json:{code:'0',data:[{id:'a',baseName:'真实字段资料库'}]}}))
  9  |  await seedAdmin(page);await page.goto('/admin/knowledge-route');await expect(page.getByRole('option',{name:'真实字段资料库',exact:true})).toBeAttached()
  10 | })
  11 | const screens=[
  12 |  ['dashboard','/admin/dashboard','运营总览'],['quality','/admin/quality-overview','知识运行全景'],['bases','/admin/knowledge-bases','团队资料库'],['documents','/admin/documents','团队知识使用指南'],['document',`/admin/documents/${docId}`,'团队知识使用指南'],['route',`/admin/knowledge-route?kb=${comparisonBase}`,'开发流程'],['traces','/admin/knowledge-route/traces','如何维护团队知识？'],['sessions','/admin/observability','如何维护团队知识？'],['session',`/admin/observability/${comparisonSession}`,'如何维护团队知识？'],['exchange',`/admin/observability/${comparisonSession}/exchanges/${comparisonExchange}`,'如何维护团队知识？'],['configuration','/admin/settings/configuration','45%']
  13 | ]
  14 | for(const width of [390,1440])test('same snapshot admin comparison at '+width,async({page,context})=>{
  15 |  test.setTimeout(180000)
  16 |  const state=await mockAdminComparison(context),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.setViewportSize({width,height:width===390?844:1000})
  17 |  const output=resolve(process.env.AEGIS_FINAL_EVIDENCE_DIR||'../doc/重构前端/evidence/admin/A8/comparison')
  18 |  for(const [version,origin,username,password] of [['legacy','http://localhost:5173','#login-username','#login-password'],['next','http://localhost:5174','#admin-username','#admin-password']]){
  19 |   await page.goto(origin+'/admin/login');await page.locator(username).fill('');await page.locator(password).fill('');await expect(page.locator(password)).toHaveValue('');await page.screenshot({path:resolve(output,`${test.info().project.name}-login-${version}-${width}.png`),fullPage:true,animations:'disabled'})
  20 |  }
  21 |  for(const [name,path,text] of screens)for(const [version,origin,main] of [['legacy','http://localhost:5173','#admin-main-content'],['next','http://localhost:5174','#admin-next-main']]){
  22 |   await page.goto(origin+path);await expect(page.locator(main).getByText(text,{exact:true}).filter({visible:true}).first()).toBeVisible();await page.waitForLoadState('networkidle');await page.screenshot({path:resolve(output,`${test.info().project.name}-${name}-${version}-${width}.png`),fullPage:true,animations:'disabled'});expect(state.blocked).toEqual([]);expect(errors).toEqual([])
  23 |  }
  24 |  // The same completed task is opened through each version's actual navigation.
  25 |  for(const [version,origin] of [['legacy','http://localhost:5173'],['next','http://localhost:5174']]){
  26 |   await page.goto(origin+`/admin/documents/${docId}?section=tasks&indexTaskId=${indexId}`)
  27 |   await expect(page.getByText('当前任务已完成',{exact:true}).filter({visible:true}).first()).toBeVisible()
  28 |   await page.waitForLoadState('networkidle');await page.screenshot({path:resolve(output,`${test.info().project.name}-task-${version}-${width}.png`),fullPage:true,animations:'disabled'})
  29 |  }
  30 |  // Errors use the identical envelope for both versions.
  31 |  state.statuses['/manage/document/page/query']=503
  32 |  for(const [version,origin] of [['legacy','http://localhost:5173'],['next','http://localhost:5174']]){await page.goto(origin+'/admin/documents');await expect(page.getByText('A8 隔离读取失败',{exact:false}).first()).toBeVisible();await page.screenshot({path:resolve(output,`${test.info().project.name}-error-${version}-${width}.png`),fullPage:true,animations:'disabled'})}
  33 |  expect(state.blocked).toEqual([]);expect(errors).toEqual([])
  34 |  const hashes=new Map();for(const call of state.calls){const key=call.path+':'+call.status;if(!hashes.has(key))hashes.set(key,new Set());hashes.get(key).add(call.responseHash)}for(const values of hashes.values())expect(values.size).toBe(1)
> 35 |  for(const path of ['/manage/knowledge/base/list','/manage/document/detail/query','/manage/document/task/log/query','/manage/knowledge/route/trace/page/query','/api/chat/session/detail','/api/chat/exchange/detail','/manage/config/current/query'])expect([...new Set(state.calls.filter(c=>c.path===path).map(c=>c.origin))].sort(),path).toEqual(['http://localhost:5173','http://localhost:5174'])
     |                                                                                                                                                                                                                                                                                                                                               ^ Error: /manage/document/task/log/query
  36 |  await test.info().attach('same-response-audit',{body:JSON.stringify(state.calls,null,2),contentType:'application/json'})
  37 | })
  38 | test('final administrator navigation keeps chat public and rejects expired server auth',async({page,context})=>{
  39 |  const state=await mockAdminComparison(context);await page.goto('/admin/dashboard');await page.getByRole('link',{name:'返回对话',exact:true}).click();await expect(page).toHaveURL(/\/chat$/);await expect(page.getByRole('textbox',{name:'输入问题'})).toBeVisible();await page.goto('/admin/settings/configuration');await expect(page.getByRole('heading',{name:'系统配置',exact:true})).toBeVisible();state.auth=false;await page.reload();await expect(page).toHaveURL(/\/admin\/login/);await page.goto('/chat');await expect(page.getByRole('textbox',{name:'输入问题'})).toBeVisible();expect(state.blocked).toEqual([])
  40 | })
  41 | 
```