import {mockAdminAuth} from './admin-auth.js'
export const conversation='9007199254740993123',exchange='9007199254740993224',documentId='9007199254740993333'
export async function mockObservation(context){
 const auth=await mockAdminAuth(context),state={auth,calls:[],writes:[],blocked:[],statuses:{},delays:{},running:false,polls:0}
 const answer='## 发布方法\n\n这是保留的答案 [7] [99]。\n\n<script>window.obsInjected=1</script>\n\n```js\nconst ready = true\n```'
 const reference={referenceId:'7',citationIdentity:'chunk:stable',documentId,taskId:'task-long',documentName:'发布指南',tableId:'42',tableEvidenceRowNos:[2],tableEvidenceColumnNames:['金额'],tableEvidenceCellCoordinates:['2:1'],snippet:'后端绑定的原文'}
 function turn(id=exchange){return {exchangeId:id,question:id===exchange?'如何发布？':'另一轮问题',answer,status:'COMPLETED',totalResponseTimeMs:0,firstResponseTimeMs:0,references:[reference,{referenceId:'8',citationIdentity:'ctx:1',contextOnly:true},{referenceId:'9',documentName:'无稳定身份'}],debugTrace:{ragSystemPrompt:'System: 按证据回答',ragUserPrompt:'User: 如何发布？',modelUsageTraces:[{totalTokens:0}]}}}
 const paths=['/api/chat/knowledge-base/options','/api/chat/document/options','/api/chat/session/list','/api/chat/session/detail','/api/chat/exchange/detail','/api/chat/exchange/retrieval/results','/api/chat/exchange/channel/executions','/api/chat/stage/benchmarks','/api/chat/session/summary/rebuild']
 await context.route(url=>url.pathname.startsWith('/api/')||url.pathname.startsWith('/manage/'),async route=>{
  const request=route.request(),path=new URL(request.url()).pathname,body=request.postDataJSON();if(request.method()!=='POST'||!paths.includes(path)){state.blocked.push(path);return route.abort()}
  state.calls.push({path,body});if(path.endsWith('/rebuild'))state.writes.push({path,body})
  const delay=state.delays[body.exchangeId]||state.delays[body.keyword]||state.delays[path]||0;if(delay)await new Promise(r=>setTimeout(r,delay))
  const status=state.statuses[path]||200;let data
  if(path.endsWith('/options'))data=[]
  else if(path.endsWith('/session/list'))data={sessions:[{conversationId:conversation,latestUserMessage:body.keyword||'发布流程',latestAssistantMessage:state.listAnswer||'最近回答',chatMode:'AUTO_DOCUMENT',latestTurnStatus:'COMPLETED',messageCount:'4',updatedAt:'2026-11-04T10:00:00Z'}],totalPages:'2',totalSize:'22'}
  else if(path.endsWith('/session/detail')){state.polls++;data={conversationId:body.conversationId,running:state.running,messageCount:4,memorySummary:{summaryText:state.summaryText||'原长期摘要',summaryVersion:2,coveredExchangeCount:2},exchanges:[turn(),turn('9007199254740993225')]}}
  else if(path.endsWith('/exchange/detail'))data={conversationId:body.conversationId,exchange:turn(body.exchangeId),stageTraces:[{stageId:'stage-long',stageCode:'ROUTE',stageName:'路由决策',stageState:'COMPLETED',durationMs:0,snapshot:{route:'RETRIEVAL'}},{stageId:'budget',stageCode:'EVIDENCE_BUDGET',stageName:'证据预算',snapshot:{renderedReferenceDetails:[reference],contextOnlyReferenceDetails:[{contextIdentity:'ctx:1'}]}},{stageId:'citation',stageCode:'CITATION_BINDING',stageName:'引用绑定',snapshot:{conservationStatus:'PASSED',explicitCitationIdentities:['chunk:stable'],sourceSnapshotIdentities:['chunk:stable'],bindings:[{token:'[7]',identity:'chunk:stable'}],rejectedTokens:['[99]']}},{stageId:'final',stageCode:'FINALIZE',snapshot:{sourceSnapshotIdentities:['chunk:stable']}}]}
  else if(path.endsWith('/retrieval/results'))data=[{id:'candidate-long',documentName:'候选文档',channelType:'vector',originalScore:0,rrfScore:0,finalRank:1,citationIdentity:'chunk:stable',citationEvidenceType:'SOURCE_EVIDENCE',chunkTextPreview:'候选正文'},{id:'context-long',documentName:'上下文文档',contextOnly:true,citationEvidenceType:'CONTEXT_ONLY',contextIdentity:'ctx:1',chunkTextPreview:'仅作上下文'}]
  else if(path.endsWith('/channel/executions'))data=[{id:'channel-long',channelType:'vector',executionState:1,durationMs:0,recalledCount:2,acceptedCount:1,finalSelectedCount:1}]
  else if(path.endsWith('/stage/benchmarks'))data=[{stageCode:'ROUTE',p50DurationMs:0,p90DurationMs:15,sampleCount:20}]
  else data={summaryText:'重建后的摘要',summaryVersion:3}
  if(state.noSummary&&path.endsWith('/session/detail'))data.memorySummary=null
  if(state.noStages&&path.endsWith('/exchange/detail'))data.stageTraces=[]
  try{await route.fulfill({status,json:{code:status===200?'0':'FAIL',data:status===200?data:null,message:status===200?'':'隔离观测接口失败'}})}catch{/* canceled request */}
 })
 return state
}
