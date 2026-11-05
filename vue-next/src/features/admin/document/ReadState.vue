<script setup>
import { Alert,Button,Skeleton } from 'ant-design-vue'
defineProps({state:Object,label:{type:String,default:'数据'}});defineEmits(['retry'])
</script>
<template><Alert v-if="state.error.value" type="error" show-icon :message="`${label}读取失败${state.data.value?'，保留上次结果':''}`" :description="state.error.value"><template #action><Button :disabled="state.loading.value" @click="$emit('retry')">重试{{ label }}</Button></template></Alert><Skeleton v-if="state.loading.value&&!state.data.value" active :aria-label="`正在读取${label}`" /></template>
