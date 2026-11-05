import { mockAdminAuth } from './admin-auth.js'
import { bases,documents } from './catalog-data.js'
export async function mockCatalog(context,scene='full') {
  const auth=await mockAdminAuth(context)
  const state={auth,bases:scene==='empty'?[]:structuredClone(bases),documents:scene==='empty'?[]:structuredClone(documents),calls:[],writes:[],listStatus:200,optionsStatus:200,writeStatus:200,delay:0,queryDelays:{},completed:[]}
  const allowed=['/manage/knowledge/base/list','/manage/knowledge/base/save','/manage/knowledge/base/delete','/manage/document/page/query','/manage/document/delete','/manage/document/upload']
  const aborted=new Set();context.on('requestfailed',request=>{if(request.failure()?.errorText.includes('ERR_ABORTED'))aborted.add(request)})
  await context.route(url=>allowed.includes(url.pathname),async route=>{
    const request=route.request(),path=new URL(request.url()).pathname
    if(request.method()!=='POST'){auth.blocked.push(`${request.method()} ${path}`);return route.abort()}
    let body=path.endsWith('/upload')?null:request.postDataJSON()
    const call={path,body};state.calls.push(call)
    const writing=!path.endsWith('/list')&&!path.endsWith('/query')
    if(writing){
      if(path.endsWith('/upload')){
        const raw=request.postDataBuffer().toString('utf8')
        const json=raw.match(/name="meta"[^\r\n]*\r\n[\s\S]*?\r\n\r\n([\s\S]*?)\r\n--/)?.[1]
        body=json?JSON.parse(json):null;call.body=body;call.fileName=raw.match(/name="file"; filename="([^"]+)"/)?.[1];call.multipart=request.headers()['content-type'];call.metaJson=/name="meta"[^\r\n]*\r\nContent-Type: application\/json/i.test(raw)
      }
      state.writes.push(call);if(state.delay)await new Promise(resolve=>setTimeout(resolve,state.delay))
    }
    const delay=state.queryDelays[body?.keyword];if(delay)await new Promise(resolve=>setTimeout(resolve,delay))
    let status=writing?state.writeStatus:path.endsWith('/list')?state.optionsStatus:state.listStatus,data
    if(status===200){
      if(path.endsWith('/base/list'))data=state.bases
      else if(path.endsWith('/base/save')){const row={...body,id:body.id || '9007199254740993999',documentCount:'0',retrievableDocumentCount:'0'};const i=state.bases.findIndex(item=>item.id===row.id);if(i<0)state.bases.push(row);else state.bases[i]={...state.bases[i],...row};data=row}
      else if(path.endsWith('/base/delete')){state.bases=state.bases.filter(row=>row.id!==body.id);data=true}
      else if(path.endsWith('/page/query')){const rows=state.documents.filter(row=>(row.documentName+row.originalFileName).includes(body.keyword || ''));const page=Number(body.pageNo),size=Number(body.pageSize);data={records:rows.slice((page-1)*size,page*size),total:String(rows.length),pageNo:body.pageNo,pageSize:body.pageSize}}
      else if(path.endsWith('/document/delete')){state.documents=state.documents.filter(row=>row.documentId!==body.documentId);data={documentId:body.documentId}}
      else if(path.endsWith('/upload')){data={documentId:'9007199254740993888',taskId:'9007199254740993777',documentName:body.documentName || call.fileName};state.documents.unshift({...documents[0],...data,parseStatus:'2',parseStatusName:'解析中',indexStatus:'1',indexStatusName:'待构建',latestTaskStatus:'1'})}
    }
    try{await route.fulfill({status,json:status===200?{code:'0',data}:{code:String(status),message:'隔离场景：操作失败'}})}catch(e){if(!aborted.has(request))throw e}finally{state.completed.push(call)}
  })
  return state
}
