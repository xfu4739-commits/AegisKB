import { createRouter, createWebHistory } from 'vue-router'
import { installAdminGuard } from './adminGuard.js'
import { adminAuth } from '../shared/auth/adminAuth.js'
import { authSession } from '../shared/auth/session.js'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'Chat', component: () => import('../features/chat/ChatWorkspace.vue') },
    ...(import.meta.env.DEV ? [{ path: '/preview/chat', name: 'ChatPreview', component: () => import('../features/chat/ChatWorkspace.vue'), props: { preview: true } }] : []),
    { path: '/admin', component: () => import('../features/admin/shell/AdminFrame.vue'), children: [
      { path: 'login', name: 'AdminLogin', component: () => import('../features/admin/shell/AdminLoginView.vue') },
      { path: 'access', name: 'AdminAccess', component: () => import('../features/admin/shell/AdminAccessView.vue') },
      { path: '', component: () => import('../features/admin/shell/AdminShell.vue'), meta: { requiresAdminAuth: true }, children: [
        { path: '', redirect: { name: 'AdminDashboard' }, meta: { adminReturn: true } },
        { path: 'dashboard', name: 'AdminDashboard', component: () => import('../features/admin/dashboard/AdminDashboardView.vue'), meta: { title: '运营总览', adminReturn: true } },
        { path: 'quality-overview', name: 'AdminQualityOverview', component: () => import('../features/admin/dashboard/AdminQualityOverviewView.vue'), meta: { title: '知识运行全景', adminReturn: true } },
        { path: 'account', name: 'AdminAccount', component: () => import('../features/admin/shell/AdminAccountView.vue'), meta: { title: '账户', adminReturn: true } },
        { path: 'knowledge-bases', name: 'AdminKnowledgeBases', component: () => import('../features/admin/catalog/AdminKnowledgeBasesView.vue'), meta: { title: '知识库管理', adminReturn: true } },
        { path: 'documents', name: 'AdminDocuments', component: () => import('../features/admin/catalog/AdminDocumentsView.vue'), meta: { title: '文档管理', adminReturn: true } },
        { path: 'documents/:documentId', name: 'AdminDocumentDetail', component: () => import('../features/admin/document/AdminDocumentView.vue'), meta: { title: '文档详情', adminReturn: true } },
        { path: 'knowledge-route', name: 'AdminKnowledgeRoute', component: () => import('../features/admin/knowledge-route/AdminKnowledgeRouteView.vue'), meta: { title: '知识路由', adminReturn: true } },
        { path: 'knowledge-route/traces', name: 'AdminRouteTraces', component: () => import('../features/admin/knowledge-route/AdminRouteTracesView.vue'), meta: { title: '路由追踪', adminReturn: true } },
        { path: 'observability', name: 'AdminObservationList', component: () => import('../features/admin/observability/AdminObservationList.vue'), meta: { title: '对话观测', adminReturn: true } },
        { path: 'observability/:conversationId', name: 'AdminObservationSession', component: () => import('../features/admin/observability/AdminObservationSessionRoute.vue'), meta: { title: '会话链路', adminReturn: true } },
        { path: 'observability/:conversationId/exchanges/:exchangeId', name: 'AdminObservationExchange', component: () => import('../features/admin/observability/AdminObservationDetail.vue'), meta: { title: '轮次详情', adminReturn: true } },
        { path: 'settings/configuration', name: 'AdminConfiguration', component: () => import('../features/admin/configuration/AdminConfigurationView.vue'), meta: { title: '系统配置', adminReturn: true } },
        { path: ':pathMatch(.*)*', name: 'AdminNotFound', component: () => import('../features/admin/shell/AdminNotFoundView.vue'), meta: { title: '页面不存在' } }
      ] }
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/chat' }
  ]
})
installAdminGuard(router, authSession, adminAuth)
export default router
