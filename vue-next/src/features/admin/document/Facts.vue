<script setup>
defineOptions({name:'DocumentFacts'})
defineProps({value:[Object,Array,String,Number,Boolean],labels:{type:Object,default:()=>({})}})
const scalar=value=>value==null||typeof value!=='object'
</script>
<template><p v-if="value==null" class="admin-next-doc-muted">未记录</p><span v-else-if="scalar(value)">{{ value===''?'未记录':value }}</span><ol v-else-if="Array.isArray(value)" class="admin-next-doc-fact-list"><li v-for="(item,i) in value" :key="i"><DocumentFacts :value="item" :labels="labels" /></li><li v-if="!value.length">暂无记录</li></ol><dl v-else class="admin-next-doc-facts"><template v-for="(item,key) in value" :key="key"><div v-if="scalar(item)"><dt>{{ labels[key] || key }}</dt><dd>{{ item==null||item===''?'未记录':item }}</dd></div><div v-else class="admin-next-doc-facts-wide"><dt class="chat-next-sr-only">{{ labels[key] || key }}</dt><dd><details><summary>{{ labels[key] || key }}<span v-if="Array.isArray(item)"> · {{ item.length }}</span></summary><DocumentFacts :value="item" :labels="labels" /></details></dd></div></template></dl></template>

