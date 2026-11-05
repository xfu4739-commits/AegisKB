import {createHash} from 'node:crypto'
import {allowReadSnapshot} from './admin-inventory.js'
import {detail,plan,docId,parseId,indexId,log,snapshot} from './document-data.js'
import {qualityData} from './overview-data.js'
import {configSnapshot} from './admin-baseline.js'
export {docId,parseId,indexId}
export const comparisonSession='a8-session',comparisonExchange='a8-exchange',comparisonBase=detail.knowledgeBaseId
const turn={exchangeId:comparisonExchange,question:'如何维护团队知识？',answer:'定期核对知识来源和使用范围。',status:'COMPLETED',totalResponseTimeMs:320,firstResponseTimeMs:120,references:[]}
const session={conversationId:comparisonSession,running:false,chatMode:'OPEN_CHAT',messageCount:2,updatedAt:'2026-11-05T09:00:00+08:00',memorySummary:{summaryText:'【会话目标】\n维护团队知识。\n【已确认事实】\n知识范围已确认。',summaryVersion:2,coveredExchangeCount:1},exchanges:[turn]}
const trace={id:'a8-trace',conversationId:comparisonSession,exchangeId:comparisonExchange,question:turn.question,mode:'auto',routeStatus:'1',routeStatusName:'成功',confidence:'0.82',durationMs:'120',knowledgeBaseId:comparisonBase,topScopesJson:'[]',topTopicsJson:'[]',topDocumentsJson:'[]'}
export const comparisonSnapshots={
 '/admin/auth/me':{username:'A8-fixture-admin'},
 '/api/chat/knowledge-base/options':[], '/api/chat/document/options':[],
 '/manage/knowledge/base/list':[{id:comparisonBase,baseName:'团队资料库',description:'项目资料与使用指南',embeddingModel:'fixture-model',isDefault:'1',documentCount:'1',retrievableDocumentCount:'1',sortOrder:'0',retrievalConfigJson:'{}',graphRagConfigJson:'{}',raptorConfigJson:'{}'}],
 '/manage/document/page/query':{records:[detail],pageNo:'1',pageSize:'20',total:'1',totalPages:'1'},
 '/manage/document/detail/query':{...detail,taskHistory:[{taskId:parseId,taskType:'1',taskTypeName:'文档解析',taskStatus:'3',taskStatusName:'成功'},{taskId:indexId,taskType:'2',taskTypeName:'索引构建',taskStatus:'3',taskStatusName:'成功'}]},
 '/manage/document/strategy/plan/query':plan,
 '/manage/document/parse-route/progress/query':{documentId:docId,taskId:parseId,taskStatus:'3',taskStatusName:'成功',running:false,planReady:true,currentStageName:'处理完成',logs:[log],totalLogCount:'1'},
 '/manage/document/index/build/progress/query':{documentId:docId,taskId:indexId,taskStatus:'3',taskStatusName:'成功',building:false,currentStageName:'处理完成',logs:[log],totalLogCount:'1'},
 '/manage/document/task/log/query':{logs:[log],total:'1'},
 '/manage/document/rag/snapshot/query':snapshot,
 '/manage/document/rag/parser-diagnostic/query':{documentId:docId,parseTaskId:parseId,trace:{providerName:'PDF',pageCount:'1',warnings:[]}},
 '/manage/document/rag/page-overlay/index/query':{documentId:docId,parseTaskId:parseId,totalPages:'0',pages:[]},
 '/manage/document/parse-artifact/query':{documentId:docId,taskId:parseId,artifacts:[]},
 '/manage/document/chunk/query':{documentId:docId,records:[],parentBlocks:[],pageNo:'1',pageSize:'20',total:'0'},
 '/manage/observability/quality/overview/query':qualityData('0'),
 '/manage/knowledge/scope/list':[{id:'a8-scope',scopeId:'a8-scope',knowledgeBaseId:comparisonBase,scopeName:'开发流程',description:'开发与发布流程',sortOrder:'0'}],
 '/manage/knowledge/topic/list':[{id:'a8-topic',topicId:'a8-topic',scopeId:'a8-scope',knowledgeBaseId:comparisonBase,topicName:'发布',description:'发布流程',sortOrder:'0'}],
 '/manage/knowledge/topic/document/list':[],
 '/manage/knowledge/route/trace/page/query':{records:[trace],pageNo:'1',pageSize:'20',total:'1',totalSize:'1',totalPages:'1'},
 '/api/chat/session/list':{sessions:[{conversationId:comparisonSession,latestUserMessage:turn.question,latestAssistantMessage:turn.answer,latestTurnStatus:'COMPLETED',chatMode:'OPEN_CHAT',running:false,messageCount:2,updatedAt:session.updatedAt}],totalSize:'1',totalPages:'1',pageNo:1,pageSize:20},
 '/api/chat/session/detail':session,
 '/api/chat/exchange/detail':{conversationId:comparisonSession,exchange:turn,stageTraces:[{stageId:'a8-route',stageCode:'ROUTE',stageName:'路由决策',stageState:'COMPLETED',durationMs:120,snapshot:{route:'RETRIEVAL'}}]},
 '/api/chat/exchange/retrieval/results':[], '/api/chat/exchange/channel/executions':[], '/api/chat/stage/benchmarks':[],
 '/manage/config/current/query':configSnapshot,
 '/manage/config/history/page/query':{records:[],pageNo:1,pageSize:10,total:0,totalPages:0}
}
export async function mockAdminComparison(context){
 const state={calls:[],blocked:[],statuses:{},auth:true}
 const token='fixture.'+Buffer.from(JSON.stringify({sub:'a8-fixture',exp:4102444800})).toString('base64url')+'.not-a-real-signature'
 await context.addInitScript(({token})=>{if(!['http://localhost:5173','http://localhost:5174'].includes(location.origin))return;if(location.pathname==='/admin/login'){localStorage.removeItem('aegis-kb-admin-token');localStorage.removeItem('aegis-kb-admin-user')}else{localStorage.setItem('aegis-kb-admin-token',token);localStorage.setItem('aegis-kb-admin-user','A8-fixture-admin')}},{token})
 await context.route(url=>/^\/(?:api|manage|admin\/auth)\//.test(url.pathname),async route=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname
  try{
   if(!['/api/chat/knowledge-base/options','/api/chat/document/options'].includes(path))allowReadSnapshot(request.method(),path)
   else if(request.method()!=='POST')throw new Error('选项读取方法不合法')
   if(!Object.hasOwn(comparisonSnapshots,path))throw new Error('缺少对比快照：'+path)
   const status=path==='/admin/auth/me'&&!state.auth?401:state.statuses[path]||200
   const response=status===200?{code:'0',data:comparisonSnapshots[path]}:{code:String(status),data:null,message:'A8 隔离读取失败'}
   state.calls.push({origin:url.origin,path,body:request.postDataJSON(),status,responseHash:createHash('sha256').update(JSON.stringify(response)).digest('hex')})
   await route.fulfill({status,json:response})
  }catch(e){state.blocked.push(String(e.message));await route.abort('blockedbyclient')}
 })
 return state
}
