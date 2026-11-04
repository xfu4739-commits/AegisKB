const text = (value) => value == null ? '' : String(value).trim()
export function sourceReferences(references) {
  if (!Array.isArray(references)) return []
  return references.flatMap((item, index) => {
    const identity = text(item?.citationIdentity) || text(item?.referenceId)
    if (!identity || item.contextOnly || item.citationEvidenceType === 'CONTEXT_ONLY') return []
    return [{ index: index + 1, identity, title: text(item.documentName) || text(item.title) || `来源 ${index + 1}`, snippet: text(item.quoteText) || text(item.snippet) || text(item.raptorSummary),
      knowledgeBaseName: text(item.knowledgeBaseName), sectionPath: text(item.sectionPath) || text(item.canonicalPath), page: text(item.pageRange) || text(item.pageNo), chunk: text(item.chunkId) || text(item.chunkNo), documentId: text(item.documentId) }]
  })
}
