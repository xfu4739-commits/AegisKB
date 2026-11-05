<script setup>
import { useRoute } from 'vue-router'
import DocumentWorkspace from './DocumentWorkspace.vue'
const route=useRoute()
</script>
<template><DocumentWorkspace :key="String(route.params.documentId)" :document-id="String(route.params.documentId)" /></template>
