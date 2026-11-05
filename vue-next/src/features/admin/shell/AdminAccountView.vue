<script setup>
import { Descriptions, DescriptionsItem } from 'ant-design-vue'
import { authSession } from '../../../shared/auth/session.js'
</script>
<template>
  <section aria-labelledby="admin-account-title"><h1 id="admin-account-title">账户</h1><div class="admin-next-account-panel"><Descriptions :column="1" :colon="false"><DescriptionsItem label="用户名">{{ authSession.state.profile?.username }}</DescriptionsItem></Descriptions></div></section>
</template>
