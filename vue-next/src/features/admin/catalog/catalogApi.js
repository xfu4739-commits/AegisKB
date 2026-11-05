import { http } from '../../../shared/api/http.js'
export function createCatalogApi(client=http) {
  return {
    bases:signal=>client.json('/manage/knowledge/base/list',{}, {signal}),
    saveBase:(payload,signal)=>client.json('/manage/knowledge/base/save',payload,{signal}),
    deleteBase:(id,signal)=>client.json('/manage/knowledge/base/delete',{id},{signal}),
    documents:({keyword,page,size},signal)=>client.json('/manage/document/page/query',{keyword,pageNo:String(page),pageSize:String(size)},{signal}),
    deleteDocument:(documentId,signal)=>client.json('/manage/document/delete',{documentId},{signal}),
    upload:(form,signal)=>client.multipart('/manage/document/upload',form,{signal})
  }
}
export const catalogApi=createCatalogApi()
