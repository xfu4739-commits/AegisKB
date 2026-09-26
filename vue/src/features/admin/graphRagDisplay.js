const ENTITY_TYPE_LABELS = Object.freeze({
  BUSINESS_OBJECT: '业务对象',
  CAPABILITY: '能力',
  COMPONENT: '组件',
  CONCEPT: '概念',
  DOCUMENT: '文档',
  EVENT: '事件',
  LOCATION: '地点',
  METHOD: '方法',
  MODULE: '模块',
  ORGANIZATION: '组织',
  PERSON: '人物',
  PROCESS: '流程',
  PRODUCT: '产品',
  SECTION: '章节',
  SERVICE: '服务',
  SYSTEM: '系统',
  TECHNOLOGY: '技术'
})

const RELATION_TYPE_LABELS = Object.freeze({
  ASSOCIATED_WITH: '关联',
  BELONGS_TO: '属于',
  CALLS: '调用',
  CONTAINS: '包含',
  DEPENDS_ON: '依赖',
  EXTENDS: '继承',
  FOLLOWS: '后续于',
  IMPLEMENTS: '实现',
  PART_OF: '属于',
  PRECEDES: '前置于',
  RECORDS: '记录',
  REFERENCES: '引用',
  RELATED_TO: '相关',
  REQUIRES: '需要',
  SUPPORTS: '支撑',
  SYNCHRONIZES_WITH: '同步',
  USES: '使用'
})

export function formatGraphEntityType(type) {
  return displayLabel(type, ENTITY_TYPE_LABELS, '未分类')
}

export function formatGraphRelationType(type) {
  return displayLabel(type, RELATION_TYPE_LABELS, '关联')
}

function displayLabel(value, labels, fallback) {
  const rawValue = String(value ?? '').trim()
  if (!rawValue) return fallback
  return labels[rawValue.toUpperCase()] || rawValue
}
