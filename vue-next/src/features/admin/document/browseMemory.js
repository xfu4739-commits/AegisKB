import { nextTick,onBeforeUnmount,ref } from 'vue'

// Store display choices only. API results and request controllers stay page-owned.
export function useBrowseMemory(memory,fields){
  let alive=true
  onBeforeUnmount(()=>{
    for(const [key,value] of Object.entries(fields))memory[key]=value.value
    memory.scrollY=window.scrollY
    alive=false
  })
  return async(top=memory.scrollY)=>{
    await nextTick()
    if(alive&&Number.isFinite(top))window.scrollTo({top,behavior:'instant'})
  }
}
export const remembered=(memory,key,fallback)=>ref(memory[key] ?? fallback)
