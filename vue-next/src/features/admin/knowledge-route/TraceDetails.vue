<script setup>
import { computed } from 'vue'
import { validIdentity } from '../observability/observabilityModel.js'
import { parseCandidates,routeStatus } from './routeModel.js'
import Facts from '../document/Facts.vue'
const props=defineProps({record:Object})
const groups=computed(()=>[['topScopesJson','范围候选'],['topTopicsJson','主题候选'],['topDocumentsJson','文档候选']].map(([key,title])=>({key,title,...parseCandidates(props.record[key])})))
const origin=computed(()=>{const conversationId=validIdentity(props.record.conversationId),exchangeId=validIdentity(props.record.exchangeId);if(!conversationId)return null;return {name:exchangeId?'AdminObservationExchange':'AdminObservationSession',params:{conversationId,...(exchangeId?{exchangeId}:{})},query:{source:'knowledge-route'}}})
</script>
<template><article class="admin-next-route-detail"><h3>{{ record.question||'未记录问题' }}</h3><dl><div v-for="[label,value] in [['改写问题',record.rewriteQuestion],['模式',record.mode],['状态',routeStatus(record.routeStatus)],['置信度',record.confidence],['耗时（ms）',record.costMs??record.elapsedMs],['实际路由',record.routeType??record.route],['通道',record.channels??record.channel],['诊断',record.errorMsg],['会话 ID',record.conversationId],['轮次 ID',record.exchangeId]]" :key="label"><dt>{{ label }}</dt><dd>{{ value==null||value===''?'未记录':value }}</dd></div></dl><RouterLink v-if="origin" :to="origin">查看会话来源</RouterLink><section v-for="group in groups" :key="group.key"><h3>{{ group.title }}</h3><p v-if="group.error" role="status">{{ group.error }}</p><ol v-else-if="group.items.length"><li v-for="(item,index) in group.items" :key="index"><strong>{{ item.scopeName||item.topicName||item.documentName||item.documentId||item.topicId||item.scopeId||'未记录名称' }}</strong><p>分数 {{ item.score??'未记录' }}</p><p v-if="item.reason">{{ item.reason }}</p><RouterLink v-if="typeof item.documentId==='string'&&item.documentId" :to="{name:'AdminDocumentDetail',params:{documentId:item.documentId}}">查看文档</RouterLink></li></ol><p v-else>未记录候选</p></section><details><summary>原始记录</summary><Facts :value="record" /></details></article></template>

