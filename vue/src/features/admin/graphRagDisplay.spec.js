import { describe, expect, it } from 'vitest'
import { formatGraphEntityType, formatGraphRelationType } from './graphRagDisplay'

describe('GraphRAG display labels', () => {
  it('projects confirmed entity type enums into concise Chinese labels', () => {
    expect(formatGraphEntityType('SECTION')).toBe('章节')
    expect(formatGraphEntityType('CONCEPT')).toBe('概念')
  })

  it('projects confirmed relation type enums without changing their stored value', () => {
    expect(formatGraphRelationType('ASSOCIATED_WITH')).toBe('关联')
    expect(formatGraphRelationType('RELATED_TO')).toBe('相关')
  })

  it('keeps unknown non-empty labels visible and uses explicit empty fallbacks', () => {
    expect(formatGraphEntityType('DOMAIN_TERM')).toBe('DOMAIN_TERM')
    expect(formatGraphRelationType('CUSTOM_LINK')).toBe('CUSTOM_LINK')
    expect(formatGraphEntityType('')).toBe('未分类')
    expect(formatGraphRelationType(null)).toBe('关联')
  })
})
