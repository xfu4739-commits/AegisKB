import {it,expect} from 'vitest'
import {observationPreview} from './observationPreview.js'
it('renders readable plain previews without markup or active content and preserves IDs',()=>{
 const text=observationPreview('# 发布方法\n\n**阅读** [说明](https://example.com) 9007199254740993123\n\n<script>window.previewInjected=1</script>',240)
 expect(text).toBe('发布方法 阅读 说明 9007199254740993123')
 expect(window.previewInjected).toBeUndefined()
 expect(observationPreview('一'.repeat(300),180)).toHaveLength(180)
})
