import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminSystemConfigView from './AdminSystemConfigView.vue'

const mocks = vi.hoisted(() => ({
  queryCurrent: vi.fn(),
  updateItem: vi.fn(),
  queryHistory: vi.fn(),
  queryHistoryDetail: vi.fn(),
  restoreHistory: vi.fn()
}))

vi.mock('../../api/api', () => ({
  APIError: class APIError extends Error {},
  manageApi: {
    querySystemConfigCurrent: mocks.queryCurrent,
    updateSystemConfigItem: mocks.updateItem,
    querySystemConfigHistory: mocks.queryHistory,
    querySystemConfigHistoryDetail: mocks.queryHistoryDetail,
    restoreSystemConfigHistory: mocks.restoreHistory
  }
}))

const current = {
  configVersion: 6,
  sourceType: 'DATABASE',
  categories: [{
    groupKey: 'retrieval',
    groupLabel: '检索与排序',
    groupDescription: '控制召回、通道、阈值、融合和执行边界。',
    categoryKey: 'relevance',
    categoryLabel: '相关性阈值',
    description: '控制候选进入后续阶段的最低要求。',
    items: [{
      configKey: 'ragRuntime.minVectorSimilarity',
      label: '向量最低相似度',
      description: '低于该比例的向量候选不会进入融合。',
      value: 0.45,
      valueType: 'DECIMAL',
      controlType: 'PERCENTAGE',
      minValue: 0,
      maxValue: 1,
      step: 0.01,
      displayScale: 100,
      unit: '%',
      effectiveModeLabel: '新会话生效'
    }, {
      configKey: 'ragRuntime.rerankEnabled',
      label: '启用重排',
      description: '决定是否运行重排阶段。',
      value: true,
      valueType: 'BOOLEAN',
      controlType: 'CHECKBOX',
      effectiveModeLabel: '新会话生效'
    }]
  }]
}

const historyRecord = {
  historyId: '9007199254740993',
  beforeVersion: 5,
  afterVersion: 6,
  sourceType: 'MANUAL',
  sourceTypeLabel: '手动修改',
  changeNote: '提高阈值',
  operatorName: 'admin',
  changedAt: '2026-07-23T12:00:00Z',
  changeCount: 1,
  changes: [{
    configKey: 'ragRuntime.minVectorSimilarity',
    label: '向量最低相似度',
    beforeValue: 0.45,
    afterValue: 0.52,
    valueType: 'DECIMAL',
    controlType: 'PERCENTAGE',
    displayScale: 100,
    unit: '%'
  }]
}

function mountView() {
  return mount(AdminSystemConfigView, {
    global: {
      stubs: {
        ChildPageDialog: {
          props: ['open', 'title', 'description'],
          template: '<div v-if="open" role="dialog" :aria-label="title"><h2>{{ title }}</h2><p>{{ description }}</p><slot /><slot name="footer" /></div>'
        }
      }
    }
  })
}

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.queryCurrent.mockResolvedValue(current)
  mocks.queryHistory.mockResolvedValue({ pageNo: 1, pageSize: 10, total: 1, totalPages: 1, records: [] })
  mocks.queryHistoryDetail.mockResolvedValue(historyRecord)
  mocks.updateItem.mockResolvedValue(current)
})

describe('system configuration page', () => {
  it('renders separate view and edit actions without a raw JSON editor', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('向量最低相似度')
    expect(wrapper.text()).toContain('45%')
    expect(wrapper.text()).toContain('当前版本')
    expect(wrapper.text()).toContain('v6')
    expect(wrapper.text()).not.toContain('配置来源')
    expect(wrapper.text()).not.toContain('更新时间')
    expect(wrapper.findAll('[data-config-view]')).toHaveLength(2)
    expect(wrapper.findAll('[data-config-edit]')).toHaveLength(2)
    expect(wrapper.find('textarea[data-raw-json]').exists()).toBe(false)
  })

  it('shows the complete current value in a read-only detail dialog', async () => {
    const promptItem = {
      configKey: 'chat.systemPrompt',
      label: '主对话系统提示词',
      description: '约束主对话助手的回答风格。',
      value: '第一行\n第二行',
      valueType: 'STRING',
      controlType: 'TEXTAREA',
      maxLength: 20000,
      effectiveModeLabel: '重启应用后生效'
    }
    mocks.queryCurrent.mockResolvedValue({
      ...current,
      categories: [{
        groupKey: 'conversation',
        groupLabel: '对话与回答',
        groupDescription: '控制对话助手、RAG 编排和回答上下文。',
        categoryKey: 'chatAgent',
        categoryLabel: '对话助手',
        description: '控制主对话助手。',
        items: [promptItem]
      }]
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-config-view]').trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-label')).toBe('查看 主对话系统提示词')
    expect(dialog.text()).toContain('chat.systemPrompt')
    expect(dialog.text()).toContain('第一行\n第二行')
    expect(dialog.text()).toContain('重启应用后生效')
    expect(dialog.find('input, textarea, select').exists()).toBe(false)
    expect(dialog.find('#config-item-edit-form').exists()).toBe(false)
    expect(dialog.findAll('button').map((button) => button.text().trim())).toEqual(['关闭'])
    expect(mocks.updateItem).not.toHaveBeenCalled()
  })

  it('groups small categories under a few tabs and keeps every section expanded by default', async () => {
    mocks.queryCurrent.mockResolvedValue({
      ...current,
      categories: [{
        groupKey: 'retrieval',
        groupLabel: '检索与排序',
        groupDescription: '控制召回、通道、阈值、融合和执行边界。',
        categoryKey: 'retrievalWindow',
        categoryLabel: '召回与窗口',
        description: '控制各通道召回规模和候选裁剪窗口。',
        items: [{
          configKey: 'ragRuntime.vectorTopK',
          label: '向量召回数量',
          description: '每个子问题从向量通道最多召回的候选数量。',
          value: 10,
          valueType: 'INTEGER',
          controlType: 'NUMBER',
          minValue: 1,
          maxValue: 200,
          step: 1,
          displayScale: 1,
          unit: '条',
          effectiveModeLabel: '新会话生效'
        }]
      }, ...current.categories, {
        groupKey: 'conversation',
        groupLabel: '对话与回答',
        groupDescription: '控制对话助手、RAG 编排和回答上下文。',
        categoryKey: 'chatAgent',
        categoryLabel: '对话助手',
        description: '控制主对话助手。',
        items: [{
          configKey: 'chat.historyPreviewTurns',
          label: '推荐问题历史轮数',
          description: '生成推荐问题时最多回看的历史轮数。',
          value: 4,
          valueType: 'INTEGER',
          controlType: 'NUMBER',
          minValue: 0,
          maxValue: 50,
          step: 1,
          displayScale: 1,
          unit: '轮',
          effectiveModeLabel: '新会话生效'
        }]
      }]
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-config-category-scroll]').exists()).toBe(false)
    expect(wrapper.findAll('[data-config-group-tab]')).toHaveLength(2)
    expect(wrapper.get('[data-config-group-tab="retrieval"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('[data-config-group-tab="retrieval"]').classes()).toContain('config-tone-retrieval')
    expect(wrapper.get('[data-config-group-tab="conversation"]').classes()).toContain('config-tone-conversation')
    const retrievalCategory = wrapper.get('[data-config-category="retrievalWindow"]')
    expect(retrievalCategory.classes()).toContain('config-category')
    expect(retrievalCategory.classes()).not.toContain('overflow-hidden')
    expect(retrievalCategory.get('[data-slot="accordion-trigger"]').classes()).toContain('rounded-md')
    expect(wrapper.get('[data-config-item="ragRuntime.vectorTopK"]').classes()).toEqual(expect.arrayContaining(['config-item', 'rounded-md']))
    expect(wrapper.get('[data-config-category-trigger="retrievalWindow"]').attributes('data-state')).toBe('open')
    expect(wrapper.get('[data-config-category-trigger="relevance"]').attributes('data-state')).toBe('open')
    expect(wrapper.text()).toContain('向量召回数量')
    expect(wrapper.text()).toContain('向量最低相似度')
    expect(wrapper.text()).not.toContain('推荐问题历史轮数')

    const collapseAllButton = wrapper.findAll('button').find((button) => button.text().includes('全部折叠'))
    await collapseAllButton.trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-config-category-trigger="retrievalWindow"]').attributes('data-state')).toBe('closed')
    expect(wrapper.get('[data-config-category-trigger="relevance"]').attributes('data-state')).toBe('closed')
    expect(wrapper.text()).not.toContain('向量最低相似度')
    expect(wrapper.text()).not.toContain('向量召回数量')

    await wrapper.get('#config-search').setValue('向量召回数量')
    await flushPromises()
    const expandAllButton = wrapper.findAll('button').find((button) => button.text().includes('全部展开'))
    await expandAllButton.trigger('click')
    await wrapper.get('#config-search').setValue('')
    await flushPromises()

    expect(wrapper.get('[data-config-category-trigger="retrievalWindow"]').attributes('data-state')).toBe('open')
    expect(wrapper.get('[data-config-category-trigger="relevance"]').attributes('data-state')).toBe('open')

    await wrapper.get('[data-config-group-tab="conversation"]').trigger('mousedown', { button: 0, ctrlKey: false })
    await flushPromises()

    expect(wrapper.get('[data-config-group-tab="conversation"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.text()).toContain('推荐问题历史轮数')
    expect(wrapper.text()).not.toContain('向量召回数量')
  })

  it('uses a percent input and converts the edited value before update', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('[data-config-edit]')[0].trigger('click')
    await flushPromises()

    const input = wrapper.get('[data-config-percent-input]')
    expect(input.attributes('type')).toBe('number')
    await input.setValue('52')
    await wrapper.get('#config-change-note').setValue('提高低相关候选过滤强度')
    await wrapper.get('#config-item-edit-form').trigger('submit')
    await flushPromises()

    expect(mocks.updateItem).toHaveBeenCalledWith({
      configKey: 'ragRuntime.minVectorSimilarity',
      value: 0.52,
      expectedVersion: 6,
      changeNote: '提高低相关候选过滤强度'
    })
  })

  it('edits prompt text in a dedicated textarea and preserves the complete history value', async () => {
    const promptItem = {
      configKey: 'chat.systemPrompt',
      label: '主对话系统提示词',
      description: '约束主对话助手的回答风格。',
      value: '第一行\n第二行',
      valueType: 'STRING',
      controlType: 'TEXTAREA',
      maxLength: 20000,
      effectiveModeLabel: '重启应用后生效'
    }
    mocks.queryCurrent.mockResolvedValue({
      ...current,
      categories: [{
        groupKey: 'conversation',
        groupLabel: '对话与回答',
        groupDescription: '控制对话助手、RAG 编排和回答上下文。',
        categoryKey: 'chatAgent',
        categoryLabel: '对话助手',
        description: '控制主对话助手。',
        items: [promptItem]
      }]
    })
    mocks.updateItem.mockResolvedValue({ ...current, configVersion: 7 })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('已配置 · 7 字符')
    await wrapper.get('[data-config-edit]').trigger('click')
    await flushPromises()
    const textarea = wrapper.get('#config-text-value')
    await textarea.setValue('新提示词\n保留换行')
    await wrapper.get('#config-change-note').setValue('调整系统指令')
    await wrapper.get('#config-item-edit-form').trigger('submit')
    await flushPromises()

    expect(mocks.updateItem).toHaveBeenCalledWith({
      configKey: 'chat.systemPrompt',
      value: '新提示词\n保留换行',
      expectedVersion: 6,
      changeNote: '调整系统指令'
    })
  })

  it('shows before and after values in history and opens the centered detail dialog', async () => {
    mocks.queryHistory.mockResolvedValue({
      pageNo: 1,
      pageSize: 10,
      total: 1,
      totalPages: 1,
      records: [historyRecord]
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[1].trigger('mousedown', { button: 0, ctrlKey: false })
    await flushPromises()

    expect(mocks.queryHistory).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('45%')
    expect(wrapper.text()).toContain('52%')
    const detailButton = wrapper.findAll('button').find((button) => button.text().includes('查看详情'))
    await detailButton.trigger('click')
    await flushPromises()

    expect(mocks.queryHistoryDetail).toHaveBeenCalledWith({ historyId: historyRecord.historyId })
    expect(wrapper.get('[role="dialog"]').text()).toContain('配置修改详情')
    expect(wrapper.get('[role="dialog"]').text()).toContain('45%')
    expect(wrapper.get('[role="dialog"]').text()).toContain('52%')
  })
})
