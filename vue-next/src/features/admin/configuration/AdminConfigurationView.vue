<script setup>
import {computed,inject,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {Button,Card,Descriptions,DescriptionsItem,Empty,Input,Modal,Tag} from 'ant-design-vue'
import {ArrowPathIcon,MagnifyingGlassIcon} from '@heroicons/vue/24/outline'
import {configurationApi as api} from './configurationApi.js'
import {configGroups,controlValue,editable,findItem,formatValue,historyIdentity,storedValue,version} from './configurationModel.js'
import {createReadResource} from '../dashboard/overviewState.js'
import ReadState from '../document/ReadState.vue'
import ConfigurationInput from './ConfigurationInput.vue'
import './configuration.css'
const route=useRoute(),router=useRouter(),container=inject('admin-next-container')
const current=createReadResource((_,signal)=>api.current(signal)),history=createReadResource((page,signal)=>api.history({pageNo:page,pageSize:10},signal)),detail=createReadResource((id,signal)=>api.detail(id,signal))
const tab=computed(()=>route.query.tab==='history'?'history':'current'),keyword=ref(String(route.query.q||'')),groups=computed(()=>configGroups(current.data.value?.categories,keyword.value)),groupKey=computed(()=>groups.value.some(x=>x.key===route.query.group)?route.query.group:groups.value[0]?.key),group=computed(()=>groups.value.find(x=>x.key===groupKey.value)),historyPage=computed(()=>{const n=Number(route.query.page);return Number.isSafeInteger(n)&&n>0?n:1})
const mode=ref(''),selected=ref(null),value=ref(''),note=ref(''),baseVersion=ref(null),pending=ref(false),error=ref(''),notice=ref(''),conflict=ref(false),compared=ref(false),controller=new AbortController();let trigger,alive=true
const latestItem=computed(()=>findItem(current.data.value,selected.value?.configKey)),title=computed(()=>mode.value==='edit'?'编辑配置':mode.value==='view'?'配置详情':mode.value==='restore'?'恢复历史版本':'修改历史详情')
watch(keyword,q=>{if(q!==String(route.query.q||''))router.replace({query:{...route.query,q:q||undefined}})})
watch(()=>route.query.q,q=>{keyword.value=String(q||'')})
watch([tab,historyPage],()=>{if(tab.value==='history')history.load(historyPage.value)},{immediate:true})
onMounted(()=>current.load());onBeforeUnmount(()=>{alive=false;controller.abort();current.dispose();history.dispose();detail.dispose()})
function setTab(next){router.push({query:{...route.query,tab:next==='history'?'history':undefined}})}
function restoreFocus(){requestAnimationFrame(()=>{if(alive&&trigger?.isConnected)trigger.focus()})}
function close(){if(!pending.value){mode.value='';error.value=''}}
function start(next,event){if(pending.value)return false;trigger=event.currentTarget;error.value='';conflict.value=false;compared.value=false;note.value='';mode.value=next;nextTick(()=>document.querySelector('.admin-next-config-modal')?.focus());return true}
function openItem(item,next,event){if(next==='edit'&&!editable(item))return;try{const baseline=next==='edit'?version(current.data.value?.configVersion):null;const draft=next==='edit'?controlValue(item):'';if(!start(next,event))return;selected.value={...item};baseVersion.value=baseline;value.value=draft}catch(e){notice.value=e.message}}
async function openHistory(record,next,event){try{const id=historyIdentity(record.historyId),baseline=next==='restore'?version(current.data.value?.configVersion):null;if(!start(next,event))return;selected.value={...record,historyId:id};baseVersion.value=baseline;await detail.load(id)}catch(e){notice.value=e.message}}
async function compare(){await current.load();compared.value=!current.error.value&&!!current.data.value}
function rebase(){try{if(!compared.value||!latestItem.value||!editable(latestItem.value)||latestItem.value.valueType!==selected.value.valueType||latestItem.value.controlType!==selected.value.controlType)throw new Error('配置定义已变化，请关闭后重新打开编辑。');baseVersion.value=version(current.data.value.configVersion);selected.value={...latestItem.value};conflict.value=false;compared.value=false;error.value=''}catch(e){error.value=e.message}}
async function submit(){
 if(pending.value||conflict.value)return
 let payload
 try{
  const expectedVersion=version(baseVersion.value),changeNote=note.value.trim()
  if(mode.value==='edit'){const stored=storedValue(selected.value,value.value);if(!changeNote)throw new Error('请填写修改说明。');payload={configKey:selected.value.configKey,value:stored,expectedVersion,changeNote}}
  else if(mode.value==='restore'){if(!detail.data.value||detail.loading.value||detail.error.value||historyIdentity(detail.data.value.historyId)!==selected.value.historyId)throw new Error('请先读取目标历史详情。');payload={historyId:selected.value.historyId,expectedVersion,changeNote}}
  else return
  if(changeNote.length>255)throw new Error('说明最多 255 个字符。')
 }catch(e){error.value=e.message;return}
 pending.value=true;error.value=''
 try{
  if(mode.value==='edit')await api.update(payload,controller.signal);else await api.restore(payload,controller.signal)
  if(!alive)return
  mode.value='';notice.value='操作已提交，正在重新读取当前配置。';await current.load();if(!alive)return
  notice.value=current.error.value?'操作已提交，但当前配置读取失败，请刷新核对，不要重复提交。':'当前配置已重新读取。'
  if(tab.value==='history')await history.load(historyPage.value);else history.data.value=null
 }catch(e){if(alive&&e.name!=='AbortError'){error.value=e.message;conflict.value=e.status===409||String(e.code)==='409'}}finally{if(alive)pending.value=false}
}
function refresh(){if(tab.value==='history')history.load(historyPage.value);else current.load()}
const descriptionValue=item=>item?.masked?'已脱敏':typeof item?.value==='object'?JSON.stringify(item.value,null,2):String(item?.value??'—')
const changedValue=(change,value)=>change.masked?'已脱敏':change.valueType==='STRING'?String(value??'—'):formatValue(change,value)
const displayTime=value=>{if(value==null)return '—';const date=new Date(value);return Number.isNaN(date.getTime())?String(value):date.toLocaleString('zh-CN',{hour12:false})}
</script>
<template>
<section class="admin-next-config">
 <header class="admin-next-config-heading"><div class="admin-next-config-actions"><h1>系统配置</h1><Tag v-if="current.data.value">v{{ current.data.value.configVersion }}</Tag></div><Button :loading="current.loading.value||history.loading.value" :disabled="pending" @click="refresh"><template #icon><ArrowPathIcon class="admin-next-icon" aria-hidden="true" /></template>刷新配置</Button></header>
 <p v-if="notice" role="status" class="admin-next-config-notice">{{ notice }}</p>
 <nav aria-label="配置视图" class="admin-next-config-tabs"><Button :type="tab==='current'?'primary':'text'" :aria-pressed="tab==='current'" :disabled="pending" @click="setTab('current')">当前配置</Button><Button :type="tab==='history'?'primary':'text'" :aria-pressed="tab==='history'" :disabled="pending" @click="setTab('history')">修改历史</Button></nav>
 <template v-if="tab==='current'">
  <ReadState :state="current" label="当前配置" @retry="current.load()" />
  <Card v-if="current.data.value" class="admin-next-config-workspace" :body-style="{padding:0}">
   <div class="admin-next-config-search"><label for="configuration-search" class="chat-next-sr-only">搜索配置</label><Input id="configuration-search" v-model:value="keyword" placeholder="搜索名称、说明或配置键" allow-clear><template #prefix><MagnifyingGlassIcon class="admin-next-icon" aria-hidden="true" /></template></Input></div>
   <Empty v-if="!groups.length" description="没有匹配的配置项" />
   <div v-else class="admin-next-config-columns"><nav aria-label="配置分组" class="admin-next-config-groups"><button v-for="entry in groups" :key="entry.key" type="button" :aria-current="entry.key===groupKey?'true':undefined" @click="router.replace({query:{...route.query,group:entry.key}})"><span>{{ entry.label }}</span><span>{{ entry.categories.reduce((sum,c)=>sum+c.items.length,0) }}</span></button></nav>
    <div class="admin-next-config-categories"><section v-for="category in group?.categories" :key="category.categoryKey"><h2>{{ category.categoryLabel }}</h2><article v-for="item in category.items" :key="item.configKey" class="admin-next-config-item" data-config-item><div class="admin-next-config-item-name"><h3>{{ item.label||item.configKey }}</h3><p v-if="item.description">{{ item.description }}</p><code>{{ item.configKey }}</code></div><div class="admin-next-config-value"><strong>{{ formatValue(item) }}</strong><small>{{ item.effectiveModeLabel||'生效方式未记录' }}</small></div><div class="admin-next-config-actions"><Button size="small" @click="openItem(item,'view',$event)">查看</Button><Button v-if="editable(item)" size="small" :disabled="current.loading.value||pending" @click="openItem(item,'edit',$event)">编辑</Button><span v-else class="admin-next-config-muted">仅查看</span></div></article></section></div>
   </div>
  </Card>
 </template>
 <template v-else>
  <ReadState :state="history" label="修改历史" @retry="history.load(historyPage)" />
  <Card><template #title><h2>修改记录</h2></template><Empty v-if="history.data.value&&!history.data.value.records?.length" description="暂无修改历史" /><ol class="admin-next-config-history"><li v-for="record in history.data.value?.records||[]" :key="record.historyId"><div class="admin-next-config-heading"><div class="admin-next-config-actions"><h2>v{{ record.beforeVersion }} → v{{ record.afterVersion }}</h2><Tag>{{ record.sourceTypeLabel||record.sourceType||'未记录' }}</Tag><span class="admin-next-config-muted">{{ record.changeCount??'—' }} 项变化</span></div><div class="admin-next-config-actions"><Button @click="openHistory(record,'history',$event)">查看详情</Button><Button :disabled="!current.data.value||current.loading.value||pending" @click="openHistory(record,'restore',$event)">恢复此版本</Button></div></div><p>{{ record.changeNote||'未填写修改说明' }}</p><div class="admin-next-config-muted">{{ record.operatorName||'—' }} · {{ displayTime(record.changedAt) }}</div><div class="admin-next-config-history-preview"><div v-for="change in record.changes?.slice(0,3)||[]" :key="change.configKey"><span>{{ change.label||change.configKey }}</span><span>{{ formatValue(change,change.beforeValue) }} → <strong>{{ formatValue(change,change.afterValue) }}</strong></span></div></div></li></ol>
   <nav class="admin-next-config-heading admin-next-config-pagination" aria-label="配置历史分页"><span>第 {{ historyPage }} 页 / {{ history.data.value?.totalPages??'—' }}</span><div class="admin-next-config-actions"><Button :disabled="historyPage<=1||history.loading.value" @click="router.push({query:{...route.query,page:String(historyPage-1)}})">上一页</Button><Button :disabled="history.loading.value||!history.data.value||historyPage>=Number(history.data.value.totalPages||0)" @click="router.push({query:{...route.query,page:String(historyPage+1)}})">下一页</Button></div></nav>
  </Card>
 </template>
 <Modal wrap-class-name="admin-next-config-modal" :open="!!mode" :title="title" :width="800" :get-container="container" :footer="null" :closable="!pending" :mask-closable="false" :keyboard="!pending" :after-close="restoreFocus" destroy-on-close @cancel="close">
  <div class="admin-next-config-dialog">
   <template v-if="mode==='view'||mode==='edit'"><h2>{{ selected?.label||selected?.configKey }}</h2><p v-if="selected?.description" class="admin-next-config-muted">{{ selected.description }}</p><Descriptions :column="1" size="small" bordered><DescriptionsItem label="配置键">{{ selected?.configKey }}</DescriptionsItem><DescriptionsItem label="生效方式">{{ selected?.effectiveModeLabel||'未记录' }}</DescriptionsItem><DescriptionsItem label="类型">{{ selected?.valueType }}</DescriptionsItem><DescriptionsItem v-if="selected?.minValue!=null||selected?.maxValue!=null" label="存储范围">{{ selected?.minValue??'不限' }} ～ {{ selected?.maxValue??'不限' }} {{ selected?.controlType==='PERCENTAGE'?'（比例原值）':selected?.unit }}</DescriptionsItem></Descriptions></template>
   <section v-if="mode==='view'" class="admin-next-config-full-value"><h3>当前值</h3><pre>{{ descriptionValue(selected) }}</pre><Button @click="close">关闭</Button></section>
   <template v-if="mode==='history'||mode==='restore'"><ReadState :state="detail" label="历史详情" @retry="detail.load(selected.historyId)" /><template v-if="detail.data.value"><Descriptions :column="1" size="small" bordered><DescriptionsItem label="历史版本">v{{ detail.data.value.beforeVersion }} → v{{ detail.data.value.afterVersion }}</DescriptionsItem><DescriptionsItem label="历史记录 ID">{{ detail.data.value.historyId }}</DescriptionsItem><DescriptionsItem label="修改说明">{{ detail.data.value.changeNote||'未填写' }}</DescriptionsItem><DescriptionsItem label="操作者">{{ detail.data.value.operatorName||'—' }}</DescriptionsItem><DescriptionsItem label="时间">{{ displayTime(detail.data.value.changedAt) }}</DescriptionsItem><DescriptionsItem v-if="detail.data.value.restoreFromHistoryId" label="恢复来源">{{ detail.data.value.restoreFromHistoryId }}</DescriptionsItem></Descriptions><h3>该次修改的差异</h3><div v-for="change in detail.data.value.changes||[]" :key="change.configKey" class="admin-next-config-diff"><h4>{{ change.label||change.configKey }}</h4><code>{{ change.configKey }}</code><div><section><h5>修改前</h5><pre>{{ changedValue(change,change.beforeValue) }}</pre></section><section><h5>修改后</h5><pre>{{ changedValue(change,change.afterValue) }}</pre></section></div></div></template><Button v-if="mode==='history'" @click="close">关闭</Button></template>
   <form v-if="mode==='edit'||mode==='restore'" class="admin-next-config-form" @submit.prevent="submit">
    <template v-if="mode==='edit'"><div class="admin-next-config-compare"><span>当前值：{{ formatValue(selected) }}</span><span>提交基于 v{{ baseVersion }}</span></div><label for="configuration-value">配置值</label><ConfigurationInput v-model="value" :item="selected" :disabled="pending" /><p v-if="selected.controlType==='PERCENTAGE'" class="admin-next-config-muted">输入百分数（%），提交时换算为比例。</p><p v-else-if="selected.unit" class="admin-next-config-muted">单位：{{ selected.unit }}<span v-if="selected.step!=null"> · 建议步长 {{ selected.step }}</span></p></template>
    <div v-else class="admin-next-config-warning"><p>将整份系统配置恢复到历史记录的修改后版本 v{{ detail.data.value?.afterVersion??'—' }}。</p><p>上方仅列出该次历史变化，不代表全部恢复影响。恢复基于当前 v{{ baseVersion }} 创建新版本，原历史保留。</p></div>
    <label for="configuration-note">{{ mode==='edit'?'修改说明':'恢复说明（可选）' }}</label><Input.TextArea id="configuration-note" v-model:value="note" :rows="3" :maxlength="255" :disabled="pending" />
    <p v-if="error" role="alert" class="admin-next-config-error">{{ error }}</p>
    <div v-if="conflict" class="admin-next-config-conflict"><p>草稿已保留。请重新读取当前版本后比较，不会自动重试写入。</p><Button :loading="current.loading.value" @click="compare">重新读取并比较</Button><p v-if="current.error.value" role="alert">{{ current.error.value }}</p><template v-if="compared"><p>服务器当前版本：v{{ current.data.value.configVersion }}</p><template v-if="mode==='edit'"><p>当前服务器值：{{ formatValue(latestItem) }}</p><pre v-if="latestItem?.valueType==='STRING'&&!latestItem.masked">{{ latestItem.value }}</pre><Button @click="rebase">采用新版本继续编辑</Button></template><p v-else>请关闭并重新打开恢复确认，核对新的当前版本。</p></template></div>
    <div class="admin-next-config-form-actions"><Button :disabled="pending" @click="close">取消</Button><Button type="primary" html-type="submit" :loading="pending" :disabled="pending||conflict||(mode==='restore'&&(!detail.data.value||detail.loading.value||!!detail.error.value))">{{ mode==='edit'?'保存修改':'确认恢复' }}</Button></div>
   </form>
  </div>
 </Modal>
</section>
</template>
