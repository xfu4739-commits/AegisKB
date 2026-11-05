import {http} from '../../../shared/api/http.js'
// Config DTOs consume original JSON types; never apply manage numeric string coercion.
export function createConfigurationApi(client=http){
 const call=(path,payload={},signal)=>client.json('/manage/config/'+path,payload,{signal})
 return {current:signal=>call('current/query',{},signal),history:(payload,signal)=>call('history/page/query',payload,signal),detail:(historyId,signal)=>call('history/detail/query',{historyId},signal),update:(payload,signal)=>call('item/update',payload,signal),restore:(payload,signal)=>call('history/restore',payload,signal)}
}
export const configurationApi=createConfigurationApi()
