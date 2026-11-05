import { onBeforeUnmount, ref, shallowRef } from 'vue'
import { createReadResource } from '../dashboard/overviewState.js'
import { documentApi } from './documentApi.js'
import { isTaskRunning,latestLogId,mergeLogs } from './documentModel.js'
export function useDocumentRead(kind){const state=createReadResource((key,signal)=>documentApi.read(kind,JSON.parse(key),signal));onBeforeUnmount(state.dispose);return {...state,read:payload=>state.load(JSON.stringify(payload))}}
export function createTaskPoller(loader,interval=1500){const data=shallowRef(null),error=ref(''),loading=ref(false);let generation=0,controller,timer,disposed=false,payload
 function pause(){generation++;clearTimeout(timer);controller?.abort();loading.value=false}
 async function poll(owner){if(disposed||owner!==generation)return;loading.value=true;controller=new AbortController();try{const value=await loader(payload,controller.signal);if(disposed||owner!==generation)return;if(value?.documentId&&String(value.documentId)!==payload.documentId)throw new Error('任务所属文档不匹配。');if(payload.taskId&&value?.taskId&&value.taskId!==payload.taskId)throw new Error('返回任务与请求不匹配。');data.value={...value,logs:mergeLogs(data.value?.logs,value?.logs)};payload={...payload,taskId:value?.taskId||payload.taskId,sinceLogId:latestLogId(data.value.logs)||undefined};if(isTaskRunning(value))timer=setTimeout(()=>poll(owner),interval)}catch(e){if(!disposed&&owner===generation&&e.name!=='AbortError')error.value=e.message||'读取任务失败'}finally{if(!disposed&&owner===generation)loading.value=false}}
 async function start(value){if(disposed)return;pause();payload={...value};data.value=null;error.value='';await poll(generation)}
 function dispose(){pause();disposed=true}
 return {data,error,loading,start,pause,dispose}
}
