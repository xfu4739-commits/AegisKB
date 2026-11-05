<script setup>
import { inject } from 'vue'
import { Alert, Button, Input, Modal } from 'ant-design-vue'
import { extensions } from './catalogModel.js'
defineProps({open:Boolean,draft:Object,bases:Array,basesLoading:Boolean,basesError:String,pending:Boolean,error:String})
defineEmits(['close','submit','retryBases','afterClose'])
const popupContainer=inject('admin-next-container')
</script>
<template>
  <Modal :open="open" title="上传文档" :get-container="popupContainer" :closable="!pending" :keyboard="!pending" :mask-closable="!pending" destroy-on-close @cancel="$emit('close')" @after-close="$emit('afterClose')">
    <Alert v-if="basesError" type="error" show-icon message="知识库选项读取失败" :description="basesError"><template #action><Button :disabled="basesLoading || pending" @click="$emit('retryBases')">重试知识库</Button></template></Alert>
    <Alert v-if="error" type="error" show-icon :message="error" role="alert" />
    <form class="admin-next-catalog-form" @submit.prevent="$emit('submit')"><fieldset :disabled="pending">
      <div class="admin-next-catalog-field"><label for="upload-base">所属知识库</label><select id="upload-base" v-model="draft.knowledgeBaseId" :disabled="basesLoading || Boolean(basesError)"><option value="">请选择知识库</option><option v-for="base in bases" :key="base.id" :value="base.id">{{ base.baseName }}</option></select><span v-if="basesLoading" role="status">正在读取知识库</span><span v-else-if="!basesError && !bases.length">暂无知识库，请先创建知识库。</span></div>
      <div class="admin-next-catalog-field"><label for="upload-file">文件</label><input id="upload-file" type="file" :accept="extensions.map(ext=>`.${ext}`).join(',')" @change="draft.file=$event.target.files?.[0] || null" /></div>
      <div class="admin-next-catalog-field"><label for="upload-name">文档名称（可选）</label><Input id="upload-name" v-model:value="draft.documentName" placeholder="默认使用文件名" /></div>
      <div class="admin-next-catalog-field"><label for="upload-operator">操作人 ID</label><Input id="upload-operator" v-model:value="draft.operatorId" inputmode="numeric" autocomplete="off" aria-describedby="upload-operator-hint" /><span id="upload-operator-hint" class="admin-next-catalog-subtle">用于任务日志，由你明确填写；不代表当前登录账号身份。</span></div>
      <p class="admin-next-catalog-subtle">上传成功后，后端将自动创建解析与策略推荐任务。</p>
    </fieldset></form>
    <p v-if="pending" role="status">正在上传并提交解析任务…</p>
    <template #footer><Button :disabled="pending" @click="$emit('close')">取消</Button><Button type="primary" :loading="pending" :disabled="pending || basesLoading || Boolean(basesError) || !bases.length" @click="$emit('submit')">上传并解析</Button></template>
  </Modal>
</template>
