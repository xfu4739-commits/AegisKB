const field = (root, path, label, min, value, integer = true) => ({ root, path: path.split('.'), label, min, value, integer })
export const configGroups = [
  { title: '检索与通道', fields: [
    field('retrieval','vectorTopK','向量 topK',1,10), field('retrieval','keywordTopK','BM25 topK',1,10), field('retrieval','candidateTopK','融合候选 topK',1,40), field('retrieval','rerankCandidateTopK','精排候选 topK',1,24), field('retrieval','finalTopK','最终证据 topK',1,6), field('retrieval','minVectorSimilarity','向量阈值',0,.35,false), field('retrieval','keywordRelativeScoreFloor','BM25 相对阈值',0,.25,false),
    { root:'retrieval',path:['keywordChannelEnabled'],label:'关键词 / BM25',value:true,boolean:true }, {root:'retrieval',path:['tableChannelEnabled'],label:'表格通道',value:true,boolean:true},
    field('graphRag','graphRagTopK','GraphRAG topK',1,6),field('graphRag','graphRagMaxHops','GraphRAG 最大跳数',1,2),field('raptor','raptorTopK','RAPTOR topK',1,6),field('raptor','raptorSourceChunkTopK','RAPTOR 原文下钻 topK',1,4),
    {root:'graphRag',path:['graphRagChannelEnabled'],label:'GraphRAG 通道',value:true,boolean:true},{root:'raptor',path:['raptorChannelEnabled'],label:'RAPTOR 通道',value:true,boolean:true}
  ] },
  { title:'融合权重',fields: [['vectorWeight','向量权重',1],['keywordWeight','BM25 权重',1.1],['tableWeight','表格权重',1.2],['graphRagWeight','GraphRAG 权重',1.1],['raptorWeight','RAPTOR 权重',1.05],['rankWeight','排名分权重',1],['originalScoreWeight','原始分权重',.08],['metadataBoostWeight','元数据 boost 权重',.04],['maxMetadataBoost','最大 metadata boost',1]].map(([key,label,value])=>field('retrieval',`hybrid.${key}`,label,0,value,false)) },
  { title:'切块',fields: [field('retrieval','indexing.childRecursiveMaxChars','Child 递归最大字符',100,800),field('retrieval','indexing.childRecursiveOverlapChars','Child 重叠字符',0,120),field('retrieval','indexing.childSemanticMaxChars','Child 语义最大字符',100,700),field('retrieval','indexing.childSemanticMinChars','Child 语义最小字符',80,240),field('retrieval','indexing.childSemanticSimilarityThreshold','语义相似度阈值',0,.18,false),field('retrieval','indexing.parentBlockMaxChars','Parent 最大字符',300,2200),field('retrieval','indexing.parentBlockOverlapChars','Parent 重叠字符',0,180),field('retrieval','indexing.parentSemanticMaxChars','Parent 语义最大字符',300,1600),field('retrieval','indexing.parentSemanticMinChars','Parent 语义最小字符',120,480)] },
  { title:'构建参数',fields:[{root:'graphRag',path:['build','graphRagBuildEnabled'],label:'GraphRAG 构建',boolean:true,value:true},{root:'raptor',path:['build','raptorBuildEnabled'],label:'RAPTOR 构建',boolean:true,value:true},{root:'raptor',path:['build','raptorLlmSummaryEnabled'],label:'RAPTOR LLM 摘要',boolean:true,value:true},field('raptor','build.raptorMaxClusterSize','RAPTOR 簇大小',2,6),field('raptor','build.raptorMaxLevels','RAPTOR 最大层数',1,3),field('raptor','build.raptorSummaryQualityFloor','摘要质量阈值',0,.42,false)] }
]
export const configFields = configGroups.flatMap(group => group.fields)
export const getField = (configs, field) => field.path.reduce((value,key)=>value?.[key],configs[field.root])
export function setField(configs, field, value) {
  let target=configs[field.root]
  for(const key of field.path.slice(0,-1)) {
    if(!target[key] || typeof target[key]!=='object' || Array.isArray(target[key])) target[key]={}
    target=target[key]
  }
  if(value === '' || value === undefined) delete target[field.path.at(-1)]
  else target[field.path.at(-1)]=value
}
