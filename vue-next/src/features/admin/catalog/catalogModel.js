import { configFields, getField, setField } from './catalogSchema.js'
export const extensions = ['pdf','doc','docx','xlsx','txt','md','html','htm','png','jpg','jpeg','bmp','gif']
const clone = value => JSON.parse(JSON.stringify(value))
function jsonObject(value, label) {
  if(value == null || value === '') return {}
  let parsed
  try { parsed=typeof value==='string'?JSON.parse(value):clone(value) } catch { throw new Error(`${label} JSON 无效，请修正后再编辑。`) }
  if(!parsed || typeof parsed!=='object' || Array.isArray(parsed)) throw new Error(`${label} JSON 必须是对象。`)
  return parsed
}
export function createBaseDraft(input = {}) {
  const configs={retrieval:jsonObject(input.retrievalConfigJson,'检索配置'),graphRag:jsonObject(input.graphRagConfigJson,'GraphRAG 配置'),raptor:jsonObject(input.raptorConfigJson,'RAPTOR 配置')}
  jsonObject(input.metadataFilterJson,'元数据配置')
  if(!input.id) for(const field of configFields) setField(configs,field,field.value)
  return {id:input.id || '',baseName:input.baseName || '',description:input.description || '',embeddingModel:input.embeddingModel || '',isDefault:String(input.isDefault || '0')==='1',sortOrder:String(input.sortOrder ?? '0'),metadataFilterJson:input.metadataFilterJson || '',configs}
}
export function buildBasePayload(draft) {
  if(!draft.baseName.trim()) throw new Error('知识库名称不能为空。')
  if(!/^-?\d+$/.test(String(draft.sortOrder)) || !Number.isSafeInteger(Number(draft.sortOrder))) throw new Error('排序必须是安全范围内的整数。')
  const configs=clone(draft.configs)
  for(const field of configFields) {
    const value=getField(configs,field)
    if(value===undefined || value===null || value==='') continue
    if(field.boolean) { if(![true,false,'true','false','1','0',1,0].includes(value)) throw new Error(`${field.label} 必须为布尔值。`); setField(configs,field,[true,'true','1',1].includes(value)); continue }
    const number=Number(value)
    if(typeof value==='boolean' || !Number.isFinite(number) || number<field.min) throw new Error(`${field.label} 必须大于等于 ${field.min}。`)
    if(field.integer && !Number.isSafeInteger(number)) throw new Error(`${field.label} 必须是安全范围内的整数。`)
    setField(configs,field,number)
  }
  const metadata=jsonObject(draft.metadataFilterJson,'元数据配置')
  return { id:draft.id,baseName:draft.baseName.trim(),description:draft.description.trim(),embeddingModel:draft.embeddingModel.trim(),isDefault:draft.isDefault?'1':'0',sortOrder:String(draft.sortOrder),retrievalConfigJson:JSON.stringify(configs.retrieval),graphRagConfigJson:JSON.stringify(configs.graphRag),raptorConfigJson:JSON.stringify(configs.raptor),metadataFilterJson:Object.keys(metadata).length?JSON.stringify(metadata):'' }
}
export function readListQuery(query, defaultSize=12) {
  const positive=(v,fallback)=>/^\d+$/.test(String(v)) && Number.isSafeInteger(Number(v)) && Number(v)>0?Number(v):fallback
  return {keyword:typeof query.q==='string'?query.q.trim():'',page:positive(query.page,1),size:[10,12,24,48].includes(Number(query.size))?Number(query.size):defaultSize}
}
export function pageSlice(items,keyword,page,size) {
  const needle=keyword.toLocaleLowerCase(), filtered=items.filter(item=>[item.baseName,item.description,item.embeddingModel].some(value=>String(value||'').toLocaleLowerCase().includes(needle)))
  const totalPages=Math.max(1,Math.ceil(filtered.length/size)), currentPage=Math.min(page,totalPages)
  return {records:filtered.slice((currentPage-1)*size,currentPage*size),total:filtered.length,totalPages,page:currentPage}
}
export function normalizeBases(data) {
  if(!Array.isArray(data) || data.some(row=>!row || typeof row.id!=='string')) throw new Error('知识库列表格式不正确。')
  return data
}
export function normalizeDocumentPage(data) {
  if(!data || !Array.isArray(data.records) || data.records.some(row=>!row || typeof row.documentId!=='string') || data.total==null || String(data.total).trim()==='' || !Number.isSafeInteger(Number(data.total)) || Number(data.total)<0) throw new Error('文档分页数据格式不正确。')
  return {...data,total:Number(data.total),pageNo:Number(data.pageNo),pageSize:Number(data.pageSize)}
}
export const documentBusy = row => ['1','2'].includes(String(row.latestTaskStatus)) || String(row.parseStatus)==='2' || String(row.indexStatus)==='2'
export function validateUpload(draft,bases) {
  const errors=[]
  if(!bases.some(base=>base.id===draft.knowledgeBaseId)) errors.push('请选择有效的知识库。')
  if(!/^[1-9]\d*$/.test(draft.operatorId || '') || BigInt(draft.operatorId)>9223372036854775807n) errors.push('请填写有效的操作人 ID（正整数）。')
  if(!draft.file || !draft.file.size) errors.push('请选择非空文件。')
  else if(!extensions.includes(draft.file.name.split('.').at(-1).toLowerCase())) errors.push('不支持此文件类型。')
  return errors
}
export function uploadForm(draft,bases) {
  const errors=validateUpload(draft,bases); if(errors.length) throw new Error(errors[0])
  const form=new FormData(); form.append('file',draft.file); form.append('meta',new Blob([JSON.stringify({documentName:draft.documentName.trim(),knowledgeBaseId:draft.knowledgeBaseId,operatorId:draft.operatorId})],{type:'application/json'})); return form
}
export function fileSize(value) { const n=Number(value); return value==null || !Number.isFinite(n)?'—':n<1024?`${n} B`:n<1048576?`${(n/1024).toFixed(1)} KB`:`${(n/1048576).toFixed(1)} MB` }
export function dateText(value) { if(!value) return '—'; const d=new Date(value); return Number.isNaN(d.valueOf())?String(value):d.toLocaleString('zh-CN',{hour12:false}) }
