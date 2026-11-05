import { ref } from 'vue'
export function createMutation() {
  const pending=ref(false),error=ref(''); let disposed=false,controller
  async function run(send,success) {
    if(pending.value || disposed) return
    pending.value=true; error.value=''; controller=new AbortController()
    try { const result=await send(controller.signal); if(!disposed) await success(result) }
    catch(failure) { if(!disposed && failure.name!=='AbortError') error.value=failure.message || '操作失败，请重试。' }
    finally { if(!disposed) pending.value=false }
  }
  function dispose(){disposed=true;controller?.abort()}
  return {pending,error,run,dispose}
}
