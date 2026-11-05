import { http } from '../../../shared/api/http.js'
const reads={bases:'base/list',scopes:'scope/list',topics:'topic/list',profile:'document/profile/detail',relations:'topic/document/list',traces:'route/trace/page/query'}
const writes={scope:'scope/save',deleteScope:'scope/delete',topic:'topic/save',deleteTopic:'topic/delete',profile:'document/profile/regenerate',batch:'document/profile/batch/regenerate',relation:'topic/document/save',removeRelation:'topic/document/remove'}
function wire(v){if(Array.isArray(v))return v.map(wire);if(v&&typeof v==='object')return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,wire(x)]));return typeof v==='number'?String(v):v}
export function createRouteApi(client=http){function call(paths,kind,payload,signal){if(!paths[kind])throw new Error('未知路由配置接口');return client.json('/manage/knowledge/'+paths[kind],wire(payload),{signal})}return {read:(kind,payload={},signal)=>call(reads,kind,payload,signal),write:(kind,payload,signal)=>call(writes,kind,payload,signal)}}
export const routeApi=createRouteApi()
