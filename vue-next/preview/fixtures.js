// Development-only data. Loaded by Vite's serve middleware, never by application code.
const answer = `## 把信息变成可以执行的计划

可以先从 **目标、约束、行动** 三个角度整理问题。下面的内容仅用于界面演示，不是模型生成的答案。

### 1. 先明确要解决什么

用一句话描述预期结果，再列出当前已经知道的信息。把事实与假设分开，能让后续讨论更加清晰。

> 清晰的边界，是有效协作的开始。

### 2. 对比可选方案

| 方案 | 适用场景 | 优点 | 需要关注的限制 |
| --- | --- | --- | --- |
| 从小范围开始 | 需求还在探索 | 可以快速得到反馈 | 需要提前约定验收条件 |
| 分步推进 | 工作包含多个环节 | 每一步都可以回看 | 注意阶段之间的依赖 |
| 先整理资料 | 信息分散在多份文件中 | 减少遗漏与重复 | 保留原始信息的上下文 |

### 3. 用简单结构记录行动

\`\`\`js
const nextStep = {
  goal: '明确本轮要完成的事情',
  actions: ['整理信息', '比较方案', '验证结果'],
  status: 'ready'
}
console.log(nextStep)
\`\`\`

- 为每个行动设定一个清楚的结果。
- 把还不确定的信息单独记录下来。
- 根据反馈调整计划，而不是一次填满所有细节。

### 4. 保留阅读与追溯的线索

较长的资料可以按章节分段阅读。遇到暂时无法核实的内容，例如 [1] 或 [99]，保留原样并继续核对；这个演示不会为它们生成来源绑定。

你可以查看 [Vue 的公开文档](https://vuejs.org/guide/introduction.html)，也可以复制上方代码，检查长内容在不同屏幕下的阅读体验。
`

export function previewFixture(scenario = 'history') {
  return {
    knowledgeBases: [{ id: 'demo-kb-a', name: '产品资料（演示）' }, { id: 'demo-kb-b', name: '团队手册（演示）' }],
    documents: [{ id: 'demo-doc-a', knowledgeBaseId: 'demo-kb-a', name: '产品协作指南（演示）' }, { id: 'demo-doc-b', knowledgeBaseId: 'demo-kb-b', name: '团队工作手册（演示）' }],
    sessions: scenario === 'welcome' ? [] : [
      { id: 'demo-planning', title: '如何把零散的信息整理成清晰可执行的计划？', group: '今天', messages: [
        { id: 'demo-user-1', role: 'user', content: '我有一些零散的想法和资料，应该怎么整理成可以执行的计划？', time: '10:24' },
        { id: 'demo-assistant-1', role: 'assistant', content: answer, time: '10:24', recommendations: ['帮我整理一个行动清单', '如何判断哪些信息还需要核实？'] }
      ] },
      { id: 'demo-long-title', title: '这是一段用于检查长标题的演示会话：当问题包含中文、英文 ContinuousLongIdentifierWithoutSpaces01234567890123456789 以及多个条件时，列表仍然应该保持清晰', group: '昨天', messages: [
        { id: 'demo-user-2', role: 'user', content: '请用一个简短例子解释如何拆解复杂问题。', time: '16:10' },
        { id: 'demo-assistant-2', role: 'assistant', content: '先明确 **目标**，再列出 **约束**，最后确定第一个可以验证的行动。\n\n这是一条固定演示消息。', time: '16:10' },
        { id: 'demo-assistant-error', role: 'assistant', content: '', error: '演示回答暂时未完成，你可以尝试重新展示。', time: '16:11' }
      ] }
    ]
  }
}
