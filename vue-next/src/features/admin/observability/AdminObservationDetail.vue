<script setup>
import {useRoute} from 'vue-router'
import ObservationExchange from './ObservationExchange.vue'
const route=useRoute()
</script>
<template><ObservationExchange :key="route.params.conversationId+':'+route.params.exchangeId" :conversation-id="route.params.conversationId" :exchange-id="route.params.exchangeId" /></template>
