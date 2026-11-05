import {http} from '../../../shared/api/http.js'
const paths={list:'session/list',session:'session/detail',exchange:'exchange/detail',retrieval:'exchange/retrieval/results',channels:'exchange/channel/executions',benchmarks:'stage/benchmarks'}
export function createObservationApi(client=http){return {read:(kind,payload={},signal)=>{if(!paths[kind])throw new Error('未知观测接口');return client.json('/api/chat/'+paths[kind],payload,{signal})},rebuild:(conversationId,signal)=>client.json('/api/chat/session/summary/rebuild',{conversationId},{signal})}}
export const observationApi=createObservationApi()
