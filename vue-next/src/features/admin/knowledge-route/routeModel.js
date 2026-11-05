export const scopedDocuments=(rows,kb)=>rows.filter(row=>row.knowledgeBaseId===kb)
export const routeStatus=value=>({'1':'成功','2':'低置信','3':'失败',SUCCESS:'成功',LOW_CONFIDENCE:'低置信',FAILED:'失败'}[value]||String(value??'未记录'))
export function parseCandidates(raw){if(raw==null||raw==='')return {items:[],error:''};try{const rows=typeof raw==='string'?JSON.parse(raw):raw;if(!Array.isArray(rows))throw new Error();return {items:rows.filter(x=>x&&typeof x==='object'&&!Array.isArray(x)),error:''}}catch{return {items:[],error:'候选数据格式异常，可查看原始记录。'}}}
export const pageSlice=(rows,page)=>rows.slice((page-1)*20,page*20)
const text=v=>String(v??'').trim()
export function buildRouteWrite(kind,input,context){
 const operatorId=text(input.operatorId),knowledgeBaseId=text(context.knowledgeBaseId)
 if(!operatorId||!knowledgeBaseId)throw new Error('请选择知识库并填写操作人 ID。')
 const valid=(rows,id,key='id')=>rows?.some(row=>(row[key]||row.id)===id)
 const documentId=text(input.documentId),topicId=text(input.topicId)
 if(['batch','profile'].includes(kind)){
  const documentIds=[...new Set(kind==='batch'?input.documentIds||[]:[documentId])]
  if(!documentIds.length||documentIds.some(id=>!valid(context.documents,id,'documentId')))throw new Error('请选择当前知识库已加载的文档。')
  return kind==='batch'?{documentIds,operatorId}:{documentId,operatorId}
 }
 let fields
 if(kind==='scope'){if(!text(input.scopeName))throw new Error('请输入范围名称。');if(input.parentScopeId&&(!valid(context.scopes,input.parentScopeId)||input.parentScopeId===input.id))throw new Error('父范围无效。');fields=['id','scopeName','parentScopeId','description','aliases','examples','sortOrder']}
 else if(kind==='topic'){if(!text(input.topicName)||!valid(context.scopes,input.scopeId))throw new Error('请输入主题名称并选择有效范围。');fields=['id','topicName','scopeId','description','aliases','examples','answerShape','executionPreference','sortOrder']}
 else if(['deleteScope','deleteTopic'].includes(kind)){if(!valid(kind==='deleteScope'?context.scopes:context.topics,input.id))throw new Error('目标不属于当前知识库。');fields=['id']}
 else if(['relation','removeRelation'].includes(kind)){
  if(!valid(context.topics,topicId)||!documentId)throw new Error('请选择有效主题与文档。')
  if(kind==='relation'&&(!valid(context.documents,documentId,'documentId')||!text(input.relationScore)||!Number.isFinite(Number(input.relationScore))||Number(input.relationScore)<0||Number(input.relationScore)>1))throw new Error('请选择当前文档，关联分数须为 0 到 1。')
  if(kind==='removeRelation'&&!context.relations?.some(row=>row.topicId===topicId&&row.documentId===documentId))throw new Error('关联不属于当前知识库。')
  fields=kind==='relation'?['topicId','documentId','relationScore','relationSource','reason']:['topicId','documentId']
 }else throw new Error('未知操作。')
 return {...Object.fromEntries(fields.map(key=>[key,text(input[key])])),knowledgeBaseId,operatorId}
}
