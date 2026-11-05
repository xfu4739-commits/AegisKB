<script setup>
import {useRoute,useRouter} from 'vue-router'
import {watch} from 'vue'
import {validIdentity} from './observabilityModel.js'
import AdminObservationSession from './AdminObservationSession.vue'
const route=useRoute()
const router=useRouter()
watch(()=>route.query.exchangeId,id=>{if(validIdentity(id))router.replace({name:'AdminObservationExchange',params:{conversationId:route.params.conversationId,exchangeId:validIdentity(id)},query:{...route.query,exchangeId:undefined}})},{immediate:true})
</script>
<template><AdminObservationSession v-if="!validIdentity(route.query.exchangeId)" :key="route.params.conversationId" :conversation-id="route.params.conversationId" /></template>
