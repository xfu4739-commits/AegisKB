<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Form, FormItem, Input } from 'ant-design-vue'
import { EyeIcon, EyeSlashIcon } from '@heroicons/vue/24/outline'
import { adminAuth } from '../../../shared/auth/adminAuth.js'
import { authSession } from '../../../shared/auth/session.js'
import { safeAdminReturn } from '../../../router/adminGuard.js'
import { legacyLinks } from '../../../config/environment.js'

const route = useRoute(), router = useRouter(), legacy = legacyLinks()
const credentials = reactive({ username: '', password: '' })
const error = ref(''), visible = ref(false)
const { busy } = adminAuth
let disposed = false
async function submit() {
  error.value = ''
  try {
    const result = await adminAuth.login({ username: credentials.username.trim(), password: credentials.password })
    if (disposed) return
    const redirect = safeAdminReturn(router, route.query.redirect)
    if (result === 'authenticated') { credentials.password = ''; await router.replace(redirect) }
    else if (['error', 'forbidden'].includes(result)) await router.replace({ name: 'AdminAccess', query: { redirect } })
    else if (result === 'missing') error.value = '登录已失效，请重试。'
  } catch (failure) { if (!disposed && failure.name !== 'AbortError') error.value = failure.message }
}
onBeforeUnmount(() => { disposed = true; credentials.password = ''; adminAuth.cancelLogin() })
</script>

<template>
  <div class="admin-next-login-page">
    <header class="admin-next-login-header"><RouterLink to="/chat" class="admin-next-brand"><img src="/aegis-mark.svg" alt="">AegisKB</RouterLink><RouterLink to="/chat">返回对话</RouterLink></header>
    <main class="admin-next-login" aria-labelledby="admin-login-title">
      <h1 id="admin-login-title">登录管理后台</h1>
      <Alert v-if="error || authSession.state.logoutError" type="error" show-icon :message="error || authSession.state.logoutError" />
      <Form name="admin-login" :model="credentials" layout="vertical" :required-mark="false" @finish="submit">
        <FormItem label="用户名" name="username" html-for="admin-username" :rules="[{ required: true, whitespace: true, message: '请输入用户名' }]">
          <Input id="admin-username" v-model:value="credentials.username" autocomplete="username" :disabled="busy" />
        </FormItem>
        <FormItem label="密码" name="password" html-for="admin-password" :rules="[{ required: true, whitespace: true, message: '请输入密码' }]">
          <Input id="admin-password" v-model:value="credentials.password" :type="visible ? 'text' : 'password'" autocomplete="current-password" :disabled="busy">
            <template #suffix><Button type="text" size="small" :aria-label="visible ? '隐藏密码' : '显示密码'" :disabled="busy" @click="visible = !visible"><EyeSlashIcon v-if="visible" class="admin-next-icon" aria-hidden="true" /><EyeIcon v-else class="admin-next-icon" aria-hidden="true" /></Button></template>
          </Input>
        </FormItem>
        <Button type="primary" html-type="submit" block :loading="busy" :disabled="busy">登录</Button>
      </Form>
      <a class="admin-next-legacy-link" :href="legacy.admin" target="_blank" rel="noopener noreferrer">旧版后台 ↗</a>
    </main>
  </div>
</template>
