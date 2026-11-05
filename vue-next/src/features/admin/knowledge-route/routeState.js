import { computed,onBeforeUnmount,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { createReadResource } from '../dashboard/overviewState.js'
import { routeApi } from './routeApi.js'
import { catalogApi } from '../catalog/catalogApi.js'
import { pageSlice,scopedDocuments } from './routeModel.js'
export function useRouteConfiguration(){
 const route=useRoute(),router=useRouter(),selected=ref([]),editor=ref(null)
 const query=key=>typeof route.query[key]==='string'?route.query[key]:''
 const kb=computed(()=>query('kb')),tab=computed(()=>['scopes','topics','profiles','relations'].includes(query('tab'))?query('tab'):'scopes'),scope=computed(()=>query('scope')),topic=computed(()=>query('topic')),keyword=ref(query('q'))
 function resource(kind){const r=createReadResource((key,signal)=>key==='null'?Promise.resolve(null):kind==='documents'?catalogApi.documents(JSON.parse(key),signal):routeApi.read(kind,JSON.parse(key),signal));onBeforeUnmount(r.dispose);return {...r,read:payload=>r.load(JSON.stringify(payload))}}
 const bases=resource('bases'),scopes=resource('scopes'),topics=resource('topics'),relations=resource('relations'),documents=resource('documents'),profile=resource('profile')
 const normalize=r=>(Array.isArray(r.data.value)?r.data.value:[]).map(x=>({...x,id:x.id||x.scopeId||x.topicId}))
 const docs=computed(()=>scopedDocuments(documents.data.value?.records||[],kb.value))
 const context=computed(()=>({knowledgeBaseId:kb.value,scopes:normalize(scopes),topics:normalize(topics),documents:docs.value,relations:Array.isArray(relations.data.value)?relations.data.value:[]}))
 const availableTopics=computed(()=>context.value.topics.filter(x=>!scope.value||x.scopeId===scope.value))
 const rows=computed(()=>{
  let data=tab.value==='profiles'?docs.value:tab.value==='topics'?availableTopics.value:tab.value==='relations'?context.value.relations.filter(x=>(!scope.value||availableTopics.value.some(t=>t.id===x.topicId))&&(!topic.value||x.topicId===topic.value)):context.value.scopes
  const search=query('q').trim().toLocaleLowerCase();return data.filter(x=>!search||[x.scopeName,x.topicName,x.documentName,x.description,x.reason,x.documentId].some(v=>String(v||'').toLocaleLowerCase().includes(search)))
 })
 const page=computed(()=>Math.min(Math.max(1,Number(query('page'))||1),Math.max(1,Math.ceil(rows.value.length/20)))),visible=computed(()=>pageSlice(rows.value,page.value))
 function navigate(patch){return router.push({query:{...route.query,...patch}})}
 function switchBase(value){return router.push({query:{kb:value,tab:tab.value}})}
 function read(){if(!kb.value)return;for(const r of [scopes,topics,relations])r.read({knowledgeBaseId:kb.value});documents.read({keyword:'',page:1,size:200})}
 let trigger
 function closeEditor(){editor.value=null;requestAnimationFrame(()=>{if(trigger?.isConnected)trigger.focus({preventScroll:true})})}
 function open(kind,initial={}){trigger=document.activeElement;editor.value={kind,initial:structuredClone({...initial}),context:structuredClone(context.value)}}
 function viewProfile(documentId){navigate({doc:documentId})}
 async function saved(){closeEditor();selected.value=[];read();if(query('doc'))profile.read({documentId:query('doc')})}
 watch(kb,()=>{selected.value=[];editor.value=null;for(const r of [scopes,topics,relations,documents,profile])r.load('null');read()},{immediate:true})
 watch(()=>route.fullPath,()=>{keyword.value=query('q');editor.value=null})
 watch([()=>query('doc'),docs],()=>{const id=query('doc');if(id&&docs.value.some(x=>x.documentId===id))profile.read({documentId:id});else profile.load('null')})
 watch(docs,()=>{selected.value=selected.value.filter(id=>docs.value.some(x=>x.documentId===id))})
 bases.read({})
 const ready=computed(()=>[scopes,topics,relations,documents].every(x=>!x.loading.value&&!x.error.value)&&Boolean(kb.value))
 return {kb,tab,scope,topic,keyword,selected,editor,bases,scopes,topics,relations,documents,profile,docs,context,availableTopics,rows,page,visible,ready,read,open,closeEditor,saved,viewProfile,navigate,switchBase,search:()=>navigate({q:keyword.value,page:'1'})}
}

