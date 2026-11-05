import { mockAdminAuth } from './admin-auth.js'
export const routeDocId='9007199254740993123'
export async function mockRoute(context){
 const auth=await mockAdminAuth(context),state={auth,calls:[],writes:[],blocked:[],writeStatus:200,readStatus:200,writeDelay:0,baseDelays:{},traceDelays:{}}
 const paths=['base/list','scope/list','topic/list','document/profile/detail','topic/document/list','route/trace/page/query','scope/save','scope/delete','topic/save','topic/delete','document/profile/regenerate','document/profile/batch/regenerate','topic/document/save','topic/document/remove']
 const scopeRows=Array.from({length:25},(_,i)=>({id:'s'+i,knowledgeBaseId:'a',scopeName:i?'范围 '+i:'开发流程',description:'范围说明',aliases:'开发,发布',examples:'如何发布',sortOrder:String(i)}))
 const topicRows=[{id:'t0',scopeId:'s0',topicName:'发布',answerShape:'steps',executionPreference:'hybrid'},{id:'t1',scopeId:'s1',topicName:'测试'}]
 await context.route(url=>url.pathname.startsWith('/manage/'),async route=>{
  const req=route.request(),path=new URL(req.url()).pathname,key=path.replace('/manage/knowledge/',''),body=req.postDataJSON();state.calls.push({path,body})
  if(req.method()!=='POST'||(!paths.includes(key)&&path!=='/manage/document/page/query')){state.blocked.push(path);return route.abort()}
  const write=!key.endsWith('/list')&&!key.endsWith('/detail')&&!key.endsWith('/query')
  if(write)state.writes.push({key,body})
  const delay=write?state.writeDelay:state.baseDelays[body.knowledgeBaseId]||state.traceDelays[body.conversationId]||0
  if(delay)await new Promise(r=>setTimeout(r,delay))
  const status=write?state.writeStatus:state.readStatus;let data=[]
  if(key==='base/list')data=[{id:'a',baseName:'研发知识库'},{id:'b',baseName:'产品知识库'}]
  else if(key==='scope/list')data=body.knowledgeBaseId==='a'?scopeRows:[{id:'bs',scopeName:'产品范围'}]
  else if(key==='topic/list')data=body.knowledgeBaseId==='a'?topicRows:[{id:'bt',scopeId:'bs',topicName:'产品主题'}]
  else if(path==='/manage/document/page/query')data={records:[{documentId:routeDocId,knowledgeBaseId:'a',documentName:'开发指南'},{documentId:'other',knowledgeBaseId:'b',documentName:'产品手册'}],total:'2'}
  else if(key==='topic/document/list')data=body.knowledgeBaseId==='a'?[{topicId:'t0',topicName:'发布',documentId:routeDocId,documentName:'开发指南',relationScore:'0.85',relationSource:'manual',reason:'关联说明'}]:[]
  else if(key==='document/profile/detail'||key==='document/profile/regenerate')data={documentId:body.documentId,summary:'文档画像摘要',coreTopics:['发布'],sampleQuestions:['如何发布']}
  else if(key==='route/trace/page/query')data={records:[{id:'trace-'+body.pageNo,conversationId:body.conversationId||'conversation-long',exchangeId:'exchange-long',question:body.conversationId||'如何发布？',mode:body.mode||'auto',routeStatus:body.routeStatus||'1',confidence:'0',topScopesJson:'[{"scopeId":"s0","scopeName":"开发流程","score":0.8}]',topTopicsJson:'broken',topDocumentsJson:JSON.stringify([{documentId:routeDocId,documentName:'开发指南',score:0,reason:'后端返回的候选理由'}])}],totalSize:'22',totalPages:'2'}
  else if(write)data={id:body.id||'new'}
  try{await route.fulfill({status,json:{code:status===200?'0':'FAIL',data:status===200?data:null,message:status===200?'':'隔离接口失败'}})}catch{ /* canceled request */ }
 })
 return state
}
