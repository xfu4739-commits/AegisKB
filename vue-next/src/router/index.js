import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'Chat', component: () => import('../features/chat/ChatWorkspace.vue') },
    ...(import.meta.env.DEV ? [{ path: '/preview/chat', name: 'ChatPreview', component: () => import('../features/chat/ChatWorkspace.vue'), props: { preview: true } }] : []),
    { path: '/:pathMatch(.*)*', redirect: '/chat' }
  ]
})
