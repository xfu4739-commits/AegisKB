<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button } from 'ant-design-vue'
import { adminAuth } from '../../../shared/auth/adminAuth.js'
import { authSession } from '../../../shared/auth/session.js'
import { safeAdminReturn } from '../../../router/adminGuard.js'

const route = useRoute(), router = useRouter(), busy = ref(false)
const forbidden = computed(() => authSession.state.status === 'forbidden')
let disposed = false
async function retry() {
  if (busy.value) return
  busy.value = true
  const result = await adminAuth.confirm({ force: true })
  if (!disposed) {
    const redirect = safeAdminReturn(router, route.query.redirect)
    if (result === 'authenticated') await router.replace(redirect)
    else if (result === 'missing') await router.replace({ name: 'AdminLogin', query: { redirect } })
    busy.value = false
  }
}
async function relogin() {
  await adminAuth.logout()
  if (!disposed) await router.replace({ name: 'AdminLogin', query: { redirect: safeAdminReturn(router, route.query.redirect) } })
}
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <main class="admin-next-access" aria-labelledby="admin-access-title">
    <img src="/aegis-mark.svg" alt="" class="admin-next-access-logo">
    <h1 id="admin-access-title">{{ forbidden ? '无权访问' : '无法确认登录状态' }}</h1>
    <p v-if="authSession.state.error" role="alert">{{ authSession.state.error }}</p>
    <div class="admin-next-actions"><Button type="primary" :loading="busy" :disabled="busy" @click="retry">重试</Button><Button :disabled="busy" @click="relogin">重新登录</Button><RouterLink to="/chat">返回对话</RouterLink></div>
  </main>
</template>
