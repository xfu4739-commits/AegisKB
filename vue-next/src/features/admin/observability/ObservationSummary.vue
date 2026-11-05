<script setup>
import {computed,inject,nextTick,onBeforeUnmount,ref} from 'vue'
import {Button,Collapse,CollapsePanel,Descriptions,DescriptionsItem,Modal} from 'ant-design-vue'
import {observationSummarySections} from './observationPresentation.js'
import {observationPreview} from './observationPreview.js'
import ChatMarkdown from '../../chat/ChatMarkdown.vue'
import Facts from '../document/Facts.vue'
const props=defineProps({summary:Object}),container=inject('admin-next-container'),reader=ref(false),trigger=ref(null),active=ref([]),notice=ref('');let alive=true
const sections=computed(()=>observationSummarySections(props.summary?.summaryText)),metadata=computed(()=>Object.fromEntries(Object.entries(props.summary||{}).filter(([key])=>key!=='summaryText')))
onBeforeUnmount(()=>{alive=false})
async function open(){active.value=sections.value.length?[sections.value[0].key]:[];reader.value=true;await nextTick();if(alive)document.querySelector('.admin-next-observe-summary-reader')?.focus()}
function restore(){requestAnimationFrame(()=>{if(alive)trigger.value?.$el?.focus({preventScroll:true})})}
async function copy(){try{await navigator.clipboard.writeText(props.summary?.summaryText||'');notice.value='已复制摘要原文'}catch{notice.value='复制失败，请手动选择原文。'}}
</script>
<template><div class="admin-next-observe-summary-preview"><template v-if="sections.length"><Descriptions :column="2" size="small"><DescriptionsItem label="版本">{{ summary.summaryVersion??'—' }}</DescriptionsItem><DescriptionsItem label="覆盖轮次">{{ summary.coveredExchangeCount??'—' }}</DescriptionsItem></Descriptions><p v-if="sections[0].title!=='摘要'" class="admin-next-observe-summary-section-label">{{ sections[0].title }}</p><p class="admin-next-observe-summary-excerpt">{{ observationPreview(sections[0].body,160) }}</p><Button ref="trigger" type="link" class="admin-next-observe-reader-trigger" @click="open">查看完整摘要</Button></template><p v-else class="admin-next-observe-muted">尚无长期摘要</p></div><Modal wrap-class-name="admin-next-observe-summary-reader" :open="reader" title="长期摘要" :width="800" :get-container="container" :footer="null" destroy-on-close :after-close="restore" @cancel="reader=false"><div class="admin-next-observe-dialog"><div class="admin-next-observe-reader-actions"><span class="admin-next-observe-muted">{{ sections.length }} 个原文分段</span><Button @click="copy">复制摘要原文</Button></div><p v-if="notice" role="status">{{ notice }}</p><Collapse v-model:active-key="active" :bordered="false"><CollapsePanel v-for="section in sections" :key="section.key" :header="section.title"><ChatMarkdown :content="section.body" /></CollapsePanel></Collapse><details class="admin-next-observe-raw-summary"><summary>查看原始摘要</summary><pre>{{ summary?.summaryText }}</pre></details><details class="admin-next-observe-raw-summary"><summary>摘要元数据</summary><Facts :value="metadata" /></details></div></Modal></template>
