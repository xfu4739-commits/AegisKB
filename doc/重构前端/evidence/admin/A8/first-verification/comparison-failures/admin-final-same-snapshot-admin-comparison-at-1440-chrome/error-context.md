# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admin-final.spec.js >> same snapshot admin comparison at 1440
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
              - generic [ref=e76]: 文档管理
      - generic [ref=e77]:
        - generic [ref=e78]: A
        - generic [ref=e79]: A8-fixture-admin
        - button "退出登录" [ref=e80] [cursor=pointer]:
          - img [ref=e81]
    - main [ref=e83]:
      - region "文档管理" [ref=e84]:
        - generic [ref=e85]:
          - heading "文档管理" [level=1] [ref=e86]
          - button "上传文档" [ref=e87] [cursor=pointer]:
            - generic [ref=e88]: 上传文档
        - search "文档筛选" [ref=e89]:
          - generic [ref=e90]: 搜索文档
          - textbox "搜索文档" [ref=e91]:
            - /placeholder: 文档名称或文件名
          - button "搜索" [ref=e92] [cursor=pointer]:
            - generic [ref=e93]: 搜索
          - button "刷新" [ref=e94] [cursor=pointer]:
            - generic [ref=e95]: 刷新
        - alert [ref=e96]:
          - img "close-circle" [ref=e97]:
            - img [ref=e98]
          - generic [ref=e100]:
            - generic [ref=e101]: 文档列表读取失败
            - generic [ref=e102]: A8 隔离读取失败
          - button "重试列表" [ref=e104] [cursor=pointer]:
            - generic [ref=e105]: 重试列表
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