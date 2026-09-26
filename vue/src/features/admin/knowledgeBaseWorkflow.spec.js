import { describe, expect, it } from 'vitest'
import {
  buildKnowledgeBasePayload,
  createKnowledgeBaseDraft
} from './knowledgeBaseWorkflow'

describe('F06 knowledge-base workflow contracts', () => {
  it('normalizes boolean and string flags while retaining unknown JSON extensions', () => {
    const draft = createKnowledgeBaseDraft({
      id: 9,
      baseName: ' Policies ',
      isDefault: 1,
      retrievalConfigJson: JSON.stringify({ vectorTopK: 8, unknownRetrieval: 'keep', indexing: { childRecursiveMaxChars: 900, unknownIndexing: true } }),
      graphRagConfigJson: JSON.stringify({ graphRagChannelEnabled: '0', build: { graphRagBuildEnabled: 1, unknownBuild: 'keep' }, unknownGraph: 7 }),
      raptorConfigJson: JSON.stringify({ raptorChannelEnabled: true, build: { raptorBuildEnabled: false } }),
      metadataFilterJson: JSON.stringify({ tenant: 'internal' })
    })

    expect(draft.form.isDefault).toBe('1')
    expect(draft.config.graphRagChannelEnabled).toBe(false)
    expect(draft.config.graphRagBuildEnabled).toBe(true)

    const payload = buildKnowledgeBasePayload(draft)
    expect(payload.baseName).toBe('Policies')
    expect(payload.isDefault).toBe('1')
    expect(JSON.parse(payload.retrievalConfigJson)).toMatchObject({
      vectorTopK: 8,
      unknownRetrieval: 'keep',
      indexing: { childRecursiveMaxChars: 900, unknownIndexing: true }
    })
    expect(JSON.parse(payload.graphRagConfigJson)).toMatchObject({
      graphRagChannelEnabled: false,
      unknownGraph: 7,
      build: { graphRagBuildEnabled: true, unknownBuild: 'keep' }
    })
    expect(JSON.parse(payload.metadataFilterJson)).toEqual({ tenant: 'internal' })
  })

  it('rejects an empty name without mutating the draft', () => {
    const draft = createKnowledgeBaseDraft({ baseName: '   ' })
    expect(() => buildKnowledgeBasePayload(draft)).toThrow('知识库名称不能为空。')
    expect(draft.form.baseName).toBe('   ')
  })
})

