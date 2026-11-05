<script setup>
import {Checkbox,Input} from 'ant-design-vue'
defineProps({item:Object,modelValue:[String,Boolean,Number],disabled:Boolean})
defineEmits(['update:modelValue'])
</script>
<template>
 <Checkbox v-if="item.valueType==='BOOLEAN'" id="configuration-value" :checked="modelValue===true" :disabled="disabled" @update:checked="$emit('update:modelValue',$event)">启用</Checkbox>
 <Input.TextArea v-else-if="item.valueType==='STRING'" id="configuration-value" :value="modelValue" :rows="8" :maxlength="item.maxLength" :disabled="disabled" @update:value="$emit('update:modelValue',$event)" />
 <Input v-else id="configuration-value" :value="modelValue" inputmode="decimal" :disabled="disabled" @update:value="$emit('update:modelValue',$event)" />
</template>
