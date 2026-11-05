<script setup>
import { UserCircleIcon, ChatBubbleLeftRightIcon, ArrowTopRightOnSquareIcon, Squares2X2Icon, ChartBarSquareIcon, FolderIcon, DocumentTextIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
import { legacyLinks } from '../../../config/environment.js'
import { useRoute } from 'vue-router'
defineProps({ collapsed: Boolean })
defineEmits(['navigate'])
const legacy = legacyLinks()
const route = useRoute()
const groups = [
  { label: '工作空间', items: [{ to: '/admin/dashboard', name: 'AdminDashboard', label: '运营总览', icon: Squares2X2Icon }, { to: '/admin/quality-overview', name: 'AdminQualityOverview', label: '知识运行全景', icon: ChartBarSquareIcon }] },
  { label: '知识管理', items: [{ to: '/admin/knowledge-bases', name: 'AdminKnowledgeBases', label: '知识库管理', icon: FolderIcon },{ to:'/admin/documents',name:'AdminDocuments',label:'文档管理',icon:DocumentTextIcon, Cog6ToothIcon },{to:'/admin/knowledge-route',name:'AdminKnowledgeRoute',label:'知识路由',icon:FolderIcon},{to:'/admin/knowledge-route/traces',name:'AdminRouteTraces',label:'路由追踪',icon:ChartBarSquareIcon}] },
  { label: '运行与设置', items: [{to:'/admin/observability',name:'AdminObservationList',label:'对话观测',icon:ChatBubbleLeftRightIcon},{to:'/admin/settings/configuration',name:'AdminConfiguration',label:'系统配置',icon:Cog6ToothIcon},{ to: '/admin/account', name: 'AdminAccount', label: '账户', icon: UserCircleIcon }] }
]
</script>

<template>
  <div class="admin-next-nav" :class="{ 'is-collapsed': collapsed }">
    <RouterLink to="/admin" class="admin-next-brand" aria-label="AegisKB 管理后台" @click="$emit('navigate')"><img src="/aegis-mark.svg" alt=""><span v-if="!collapsed">AegisKB</span></RouterLink>
    <nav aria-label="后台主导航" class="admin-next-nav-groups">
      <section v-for="group in groups" :key="group.label"><h2 :class="{ 'chat-next-sr-only': collapsed }">{{ group.label }}</h2><RouterLink v-for="item in group.items" :key="item.name" :to="item.to" class="admin-next-nav-item" :aria-current="(route.name === item.name || item.name==='AdminObservationList' && ['AdminObservationSession','AdminObservationExchange'].includes(route.name) || item.name==='AdminDocuments' && route.name==='AdminDocumentDetail') ? 'page' : undefined" :title="collapsed ? item.label : undefined" @click="$emit('navigate')"><component :is="item.icon" class="admin-next-icon" aria-hidden="true" /><span :class="{ 'chat-next-sr-only': collapsed }">{{ item.label }}</span></RouterLink></section>
    </nav>
    <div class="admin-next-nav-bottom">
      <RouterLink to="/chat" :title="collapsed ? '返回对话' : undefined" @click="$emit('navigate')"><ChatBubbleLeftRightIcon class="admin-next-icon" aria-hidden="true" /><span :class="{ 'chat-next-sr-only': collapsed }">返回对话</span></RouterLink>
      <a :href="legacy.admin" target="_blank" rel="noopener noreferrer" :title="collapsed ? '旧版后台' : undefined"><ArrowTopRightOnSquareIcon class="admin-next-icon" aria-hidden="true" /><span :class="{ 'chat-next-sr-only': collapsed }">旧版后台</span></a>
    </div>
  </div>
</template>
