const DEFAULT_FORM = Object.freeze({
  id: '',
  baseName: '',
  description: '',
  embeddingModel: '',
  retrievalConfigJson: '',
  graphRagConfigJson: '',
  raptorConfigJson: '',
  metadataFilterJson: '',
  isDefault: '0',
  sortOrder: '0',
  operatorId: '10001'
})

function parseJsonObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return { ...value }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

export function normalizeBooleanFlag(value) {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value === 1
  const normalized = String(value ?? '').trim().toLowerCase()
  return normalized === '1' || normalized === 'true'
}

export function createKnowledgeBaseDraft(input = {}) {
  const retrieval = parseJsonObject(input.retrievalConfigJson)
  const graphRag = parseJsonObject(input.graphRagConfigJson)
  const raptor = parseJsonObject(input.raptorConfigJson)
  const metadata = parseJsonObject(input.metadataFilterJson)
  return {
    form: {
      ...DEFAULT_FORM,
      ...input,
      id: String(input.id || ''),
      isDefault: normalizeBooleanFlag(input.isDefault) ? '1' : '0',
      sortOrder: String(input.sortOrder ?? '0')
    },
    config: {
      ...retrieval,
      ...(retrieval.indexing || {}),
      ...(retrieval.hybrid || {}),
      ...graphRag,
      ...(graphRag.build || {}),
      ...raptor,
      ...(raptor.build || {}),
      keywordChannelEnabled: normalizeBooleanFlag(retrieval.keywordChannelEnabled),
      tableChannelEnabled: normalizeBooleanFlag(retrieval.tableChannelEnabled),
      graphRagChannelEnabled: normalizeBooleanFlag(graphRag.graphRagChannelEnabled),
      graphRagBuildEnabled: normalizeBooleanFlag(graphRag.build?.graphRagBuildEnabled),
      raptorChannelEnabled: normalizeBooleanFlag(raptor.raptorChannelEnabled),
      raptorBuildEnabled: normalizeBooleanFlag(raptor.build?.raptorBuildEnabled),
      raptorLlmSummaryEnabled: normalizeBooleanFlag(raptor.build?.raptorLlmSummaryEnabled)
    },
    source: { retrieval, graphRag, raptor, metadata }
  }
}

function compactForm(form) {
  return {
    ...form,
    id: String(form.id || ''),
    baseName: String(form.baseName || '').trim(),
    description: String(form.description || '').trim(),
    embeddingModel: String(form.embeddingModel || '').trim(),
    isDefault: normalizeBooleanFlag(form.isDefault) ? '1' : '0',
    sortOrder: String(form.sortOrder ?? '0'),
    operatorId: String(form.operatorId || '')
  }
}

export function buildKnowledgeBasePayload(draft = {}) {
  const form = compactForm(draft.form || {})
  if (!form.baseName) throw new Error('知识库名称不能为空。')
  const config = draft.config || {}
  const source = draft.source || {}
  const retrieval = {
    ...(source.retrieval || {}),
    vectorTopK: config.vectorTopK ?? source.retrieval?.vectorTopK,
    keywordChannelEnabled: normalizeBooleanFlag(config.keywordChannelEnabled),
    tableChannelEnabled: normalizeBooleanFlag(config.tableChannelEnabled),
    indexing: {
      ...(source.retrieval?.indexing || {}),
      ...(config.childRecursiveMaxChars == null ? {} : { childRecursiveMaxChars: config.childRecursiveMaxChars })
    }
  }
  const graphRag = {
    ...(source.graphRag || {}),
    graphRagChannelEnabled: normalizeBooleanFlag(config.graphRagChannelEnabled),
    build: {
      ...(source.graphRag?.build || {}),
      graphRagBuildEnabled: normalizeBooleanFlag(config.graphRagBuildEnabled)
    }
  }
  const raptor = {
    ...(source.raptor || {}),
    raptorChannelEnabled: normalizeBooleanFlag(config.raptorChannelEnabled),
    build: {
      ...(source.raptor?.build || {}),
      raptorBuildEnabled: normalizeBooleanFlag(config.raptorBuildEnabled),
      raptorLlmSummaryEnabled: normalizeBooleanFlag(config.raptorLlmSummaryEnabled)
    }
  }
  return {
    ...form,
    retrievalConfigJson: JSON.stringify(retrieval),
    graphRagConfigJson: JSON.stringify(graphRag),
    raptorConfigJson: JSON.stringify(raptor),
    metadataFilterJson: Object.keys(source.metadata || {}).length ? JSON.stringify(source.metadata) : ''
  }
}

export function parseKnowledgeBaseJson(value) {
  return parseJsonObject(value)
}

