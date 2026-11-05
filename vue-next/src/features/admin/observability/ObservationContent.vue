<script setup>
import {ref} from 'vue'
import {Button} from 'ant-design-vue'
import ChatMarkdown from '../../chat/ChatMarkdown.vue'
defineProps({text:{type:String,default:''},label:{type:String,default:'内容'},markdown:Boolean})
const notice=ref('')
async function copy(text){try{await navigator.clipboard.writeText(text);notice.value='已复制'}catch{notice.value='复制失败，请手动选择内容。'}}
</script>
<template><div class="admin-next-observe-content"><Button :aria-label="`复制${label}`" @click="copy(text)">复制</Button><ChatMarkdown v-if="markdown" :content="text" /><pre v-else>{{ text||'未记录' }}</pre><p v-if="notice" role="status">{{ notice }}</p></div></template>
