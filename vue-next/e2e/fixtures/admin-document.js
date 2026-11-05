import { mockCatalog } from './admin-catalog.js'
import { docId,parseId,indexId,detail,plan,log,artifact,imageArtifact,png,snapshot,chunk,nodeFor } from './document-data.js'
export async function mockDocument(context){const catalog=await mockCatalog(context);const state={catalog,calls:[],writes:[],detail:structuredClone(detail),plan:structuredClone(plan),statuses:{},delays:{},buildStatuses:[],parseStatuses:[],downloads:0}
 const names={detail:'detail/query',plan:'strategy/plan/query',parse:'parse-route/progress/query',build:'index/build/progress/query',logs:'task/log/query',artifacts:'parse-artifact/query',content:'parse-artifact/content/query',chunks:'chunk/query',chunk:'chunk/detail/query',snapshot:'rag/snapshot/query',diagnostic:'rag/parser-diagnostic/query',pages:'rag/page-overlay/index/query',page:'rag/page-overlay/detail/query',nodes:'rag/artifact/node/page/query',node:'rag/artifact/node/detail/query',relations:'rag/artifact/relation/page/query',graph:'rag/artifact/graph/window/query',table:'rag/artifact/table/window/query',confirm:'strategy/confirm',index:'index/build',download:'parse-artifact/download'}
 const paths=Object.fromEntries(Object.entries(names).map(([key,path])=>[`/manage/document/${path}`,key]));const aborted=new Set();context.on('requestfailed',req=>{if(req.failure()?.errorText?.includes('ERR_ABORTED'))aborted.add(req)})
 await context.route(url=>Boolean(paths[url.pathname]),async route=>{const request=route.request(),kind=paths[new URL(request.url()).pathname];if(request.method()!=='POST'){catalog.auth.blocked.push(`${request.method()} ${kind}`);return route.abort()}const body=request.postDataJSON();state.calls.push({kind,body});if(['confirm','index'].includes(kind))state.writes.push({kind,body});if(state.delays[kind])await new Promise(r=>setTimeout(r,state.delays[kind]));const status=state.statuses[kind]||200;let data
 if(status===200){switch(kind){
 case 'detail':data={...state.detail,documentId:body.documentId,documentName:body.documentId===docId?state.detail.documentName:'第二份文档'};break
 case 'plan':data=state.plan;break
 case 'confirm':state.plan.plan.parentPipeline.steps=body.parentSteps;state.plan.plan.childPipeline.steps=body.childSteps;state.plan.plan.planStatus='2';state.detail.currentPlanId=state.plan.plan.planId;state.detail.strategyStatus='3';data={documentId:body.documentId,planId:state.plan.plan.planId};break
 case 'index':state.detail.indexStatus='2';state.detail.indexStatusName='构建中';state.detail.latestTaskStatus='1';data={documentId:body.documentId,taskId:indexId};break
 case 'parse':case 'build':{const sequence=kind==='parse'?state.parseStatuses:state.buildStatuses;const taskStatus=sequence.length?sequence.shift():'3';if(kind==='build'&&taskStatus==='3'){state.detail.indexStatus='3';state.detail.indexStatusName='索引完成';state.detail.latestTaskStatus='3'}data={documentId:body.documentId,taskId:body.taskId||(kind==='parse'?parseId:indexId),taskStatus,taskStatusName:{'1':'新建','2':'进行中','3':'成功','4':'失败','5':'已取消'}[taskStatus],currentStageName:'处理完成',planReady:kind==='parse',running:taskStatus==='2',building:taskStatus==='2',latestLogId:log.id,logs:body.sinceLogId?[]:[log],totalLogCount:'1'};break}
 case 'logs':data={taskId:body.taskId,total:'35',logs:[{...log,id:body.pageNo==='2'?'9007199254740993221':log.id}]};break
 case 'artifacts':data={documentId:body.documentId,taskId:parseId,artifacts:[artifact,imageArtifact]};break
 case 'content':data=body.artifactId===imageArtifact.artifactId?{artifact:imageArtifact,image:true,dataUrl:`data:image/png;base64,${png}`}:{artifact,markdown:true,content:'# 标准正文\n\n安全内容\n<script>window.artifactInjected=true</script>\n![remote](https://example.com/pixel)',contentLength:'128'};break
 case 'chunks':data={documentId:body.documentId,taskId:indexId,pageNo:body.pageNo,pageSize:body.pageSize,total:'25',records:[{...chunk,chunkNo:body.pageNo==='2'?'21':'1'}]};break
 case 'chunk':data={documentId:body.documentId,chunk:{...chunk,chunkId:body.chunkId},parentBlock:{parentBlockId:chunk.parentBlockId,parentText:'父块完整内容'},siblingChunks:[chunk,{...chunk,chunkId:'9007199254740993445',chunkNo:'2',title:'第二块'}]};break
 case 'snapshot':data=snapshot;break
 case 'diagnostic':data={documentId:body.documentId,parseTaskId:parseId,trace:{providerName:'PDF',pageCount:'1',bboxBlockCoverage:1,warnings:['部分页使用 OCR']}};break
 case 'pages':data={documentId:body.documentId,parseTaskId:parseId,totalPages:'1',pages:[{pageNo:'1',displayPageNo:'1',hasOverlay:true}]};break
 case 'page':data={documentId:body.documentId,parseTaskId:parseId,page:{pageNo:'1',pageImageArtifactId:imageArtifact.artifactId,pageWidth:100,pageHeight:100,overlays:[{overlayId:'r1',type:'TEXT',label:'正文区域',leftRatio:.1,topRatio:.1,widthRatio:.7,heightRatio:.3,textPreview:'原页内容'}]}};break
 case 'nodes':{const type=body.nodeType;data={total:body.parentNodeId?'1':'22',records:[nodeFor(type,body.parentNodeId?'9007199254740993778':type==='KG_COMMUNITY'?'9007199254740993779':'9007199254740993666')],pageNo:body.pageNo,pageSize:body.pageSize};break}
 case 'node':data={node:{nodeId:body.nodeId},content:'节点正文内容',attributes:[{label:'来源页',value:'1'}],presentation:{kind:'text',keywords:['接入'],relatedGroups:[]}};break
 case 'relations':data={total:'12',records:[{edge:{edgeId:'edge1',label:'关联',detail:'后端返回的关系'},node:nodeFor('PARSE_BLOCK','9007199254740993888')}],pageNo:body.pageNo,pageSize:body.pageSize};break
 case 'table':data={tableNodeId:body.tableNodeId,totalRows:'35',totalColumns:'8',columns:[{columnId:String(Number(body.columnOffset)+1),columnName:`第 ${Number(body.columnOffset)+1} 列`}],records:[{rowId:'1',rowNo:'1',cells:[{columnId:String(Number(body.columnOffset)+1),cellText:'单元格内容'}]}]};break
 case 'graph':{const node=nodeFor('KG_ENTITY');data={stats:{totalEntities:'200',returnedNodes:'1',returnedEdges:'0',truncated:true},nodes:[{nodeId:node.nodeId,sourceId:node.sourceId,label:node.label}],edges:[]};break}
 case 'download':state.downloads++;return route.fulfill({status:200,headers:{'content-type':'application/json','content-disposition':'attachment; filename="artifact.json"'},body:'{"safe":true}'})
 }}
 try{await route.fulfill({status,json:status===200?{code:'0',data}:{code:String(status),message:'隔离场景：文档接口失败'}})}catch(e){if(!aborted.has(request))throw e}
 })
 return state
}
