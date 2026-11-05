import { http } from '../../../shared/api/http.js'
const paths={detail:'detail/query',plan:'strategy/plan/query',parse:'parse-route/progress/query',build:'index/build/progress/query',logs:'task/log/query',artifacts:'parse-artifact/query',content:'parse-artifact/content/query',chunks:'chunk/query',chunk:'chunk/detail/query',snapshot:'rag/snapshot/query',diagnostic:'rag/parser-diagnostic/query',pages:'rag/page-overlay/index/query',page:'rag/page-overlay/detail/query',nodes:'rag/artifact/node/page/query',node:'rag/artifact/node/detail/query',relations:'rag/artifact/relation/page/query',graph:'rag/artifact/graph/window/query',table:'rag/artifact/table/window/query'}
export function wire(value){if(Array.isArray(value))return value.map(wire);if(value && typeof value==='object')return Object.fromEntries(Object.entries(value).filter(([,v])=>v!==undefined).map(([k,v])=>[k,wire(v)]));return typeof value==='number'||typeof value==='bigint'?String(value):value}
export function createDocumentApi(client=http){return {
 read:(kind,payload,signal)=>{if(!paths[kind])throw new Error('未知读取接口');return client.json(`/manage/document/${paths[kind]}`,wire(payload),{signal})},
 write:(kind,payload,signal)=>{if(!['confirm','index'].includes(kind))throw new Error('未知写入接口');return client.json(`/manage/document/${kind==='confirm'?'strategy/confirm':'index/build'}`,wire(payload),{signal})},
 download:(payload,signal)=>client.download('/manage/document/parse-artifact/download',wire(payload),{signal})
}}
export const documentApi=createDocumentApi()
