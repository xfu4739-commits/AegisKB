import {onBeforeUnmount,ref,shallowRef} from 'vue'
import {createReadResource} from '../dashboard/overviewState.js'
import {observationApi} from './observabilityApi.js'
import {validIdentity} from './observabilityModel.js'
export function useObservationRead(kind){const state=createReadResource(async(key,signal)=>{const payload=JSON.parse(key),data=await observationApi.read(kind,payload,signal);if(data&&['session','exchange'].includes(kind)&&data.conversationId!==payload.conversationId)throw new Error('返回会话与当前请求不匹配。');if(kind==='exchange'&&data?.exchange&&validIdentity(data.exchange.exchangeId)!==payload.exchangeId)throw new Error('返回轮次与当前请求不匹配。');return data});onBeforeUnmount(state.dispose);return {...state,read:payload=>state.load(JSON.stringify(payload))}}
export function createSessionMonitor(loader,interval=2500){const data=shallowRef(null),error=ref(''),loading=ref(false);let owner=0,controller,timer,disposed=false,id=''
 function pause(){owner++;clearTimeout(timer);controller?.abort();loading.value=false}
 async function load(token){if(disposed||token!==owner)return;loading.value=true;controller=new AbortController();error.value='';try{const result=await loader(id,controller.signal);if(disposed||token!==owner)return;if(result?.conversationId!==id)throw new Error('返回会话与当前请求不匹配。');data.value=result;if(result.running===true)timer=setTimeout(()=>load(token),interval)}catch(e){if(!disposed&&token===owner&&e.name!=='AbortError')error.value=e.message||'会话读取失败'}finally{if(!disposed&&token===owner)loading.value=false}}
 async function start(next){if(disposed)return;pause();if(id!==next)data.value=null;id=next;await load(owner)}
 function dispose(){pause();disposed=true}
 return {data,error,loading,start,pause,dispose}
}
