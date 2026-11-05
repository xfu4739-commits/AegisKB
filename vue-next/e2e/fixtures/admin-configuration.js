import {mockAdminAuth} from './admin-auth.js'
export const configurationHistoryId='9007199254740993123'
const items=[{configKey:'rag.ratio',label:'最低相关比例',description:'过滤候选的相关比例。',value:0.29,valueType:'DECIMAL',controlType:'PERCENTAGE',minValue:0,maxValue:1,step:0.01,displayScale:100,unit:'%',effectiveModeLabel:'新会话生效'},{configKey:'rag.enabled',label:'启用检索',value:false,valueType:'BOOLEAN',controlType:'CHECKBOX',effectiveModeLabel:'新会话生效'},{configKey:'rag.count',label:'召回数量',value:0,valueType:'INTEGER',controlType:'NUMBER',minValue:0,maxValue:200,step:1,unit:'条',effectiveModeLabel:'新会话生效'},{configKey:'chat.prompt',label:'系统提示词',description:'保留完整文本与换行。',value:'第一行\n第二行',valueType:'STRING',controlType:'TEXTAREA',maxLength:20000,effectiveModeLabel:'重启应用后生效'},{configKey:'tool.duration',label:'工具超时',value:1000,valueType:'LONG',controlType:'DURATION',minValue:0,maxValue:60000,step:100,unit:'毫秒',effectiveModeLabel:'新构建任务生效'}]
export async function mockConfiguration(context){
 const auth=await mockAdminAuth(context),state={auth,calls:[],writes:[],blocked:[],statuses:{},delays:{},version:6,values:Object.fromEntries(items.map(x=>[x.configKey,x.value])),empty:false,unknown:false,conflict:false}
 const current=()=>({configVersion:state.version,sourceType:'DATABASE',categories:state.empty?[]:[{groupKey:'retrieval',groupLabel:'检索与排序',categoryKey:'recall',categoryLabel:'召回设置',items:items.slice(0,3).map(x=>({...x,value:state.values[x.configKey]}))},{groupKey:'conversation',groupLabel:'对话与工具',categoryKey:'agent',categoryLabel:'助手设置',items:items.slice(3).map(x=>({...x,value:state.values[x.configKey]})).concat(state.unknown?[{configKey:'unsupported',label:'未知配置类型',valueType:'OBJECT',controlType:'JSON',value:{a:1}}]:[])}]})
 const record=id=>({historyId:id,beforeVersion:4,afterVersion:5,sourceType:'MANUAL',sourceTypeLabel:'手动修改',operatorName:'fixture-admin',changedAt:'2026-11-05T02:00:00+08:00',changeNote:'调整候选过滤',changeCount:2,changes:[{...items[0],beforeValue:0.25,afterValue:0.29},{...items[3],beforeValue:'旧文本',afterValue:'第一行\n第二行'}]})
 await context.route(url=>url.pathname.startsWith('/manage/config/'),async route=>{
  const request=route.request(),path=new URL(request.url()).pathname,body=request.postDataJSON();const known=['current/query','item/update','history/page/query','history/detail/query','history/restore'].map(x=>'/manage/config/'+x)
  if(request.method()!=='POST'||!known.includes(path)){state.blocked.push(path);return route.abort()}
  state.calls.push({path,body});const write=path.endsWith('/update')||path.endsWith('/restore');if(write)state.writes.push({path,body})
  const delay=state.delays[body.historyId]||state.delays[body.pageNo]||state.delays[path];if(delay)await new Promise(r=>setTimeout(r,delay))
  let status=state.statuses[path]||200,code=String(status===200?'0':status),data,message=status===200?'':'隔离配置接口失败'
  if(write&&(state.conflict||body.expectedVersion!==state.version)){status=200;code='409';message='配置已被其他操作更新，请刷新后重试。'}
  if(code==='0'){
   if(path.endsWith('/current/query'))data=current()
   if(path.endsWith('/history/page/query'))data={pageNo:body.pageNo,pageSize:body.pageSize,total:2,totalPages:2,records:state.empty?[]:[record(body.pageNo===2?'9007199254740993124':configurationHistoryId)]}
   if(path.endsWith('/history/detail/query'))data=record(body.historyId)
   if(path.endsWith('/item/update')){state.values[body.configKey]=body.value;state.version++;data=current()}
   if(path.endsWith('/history/restore')){state.values['rag.ratio']=0.29;state.version++;data=current()}
  }
  try{await route.fulfill({status,json:{code,data:data??null,message}})}catch{/* request canceled */}
 })
 return state
}
