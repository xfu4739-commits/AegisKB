import AxeBuilder from '@axe-core/playwright'
import {test,expect} from '@playwright/test'
import {resolve} from 'node:path'
import {mockObservation,conversation} from './fixtures/admin-observation.js'
import {seedAdmin} from './fixtures/admin-auth.js'
test('session list has structured columns and never expands assistant answers',async({page,context})=>{
 const state=await mockObservation(context);state.listAnswer='不该出现在列表里的回答正文';await seedAdmin(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/admin/observability')
 await expect(page.getByRole('columnheader',{name:'会话',exact:true})).toBeVisible();await expect(page.getByRole('columnheader',{name:'状态',exact:true})).toBeVisible();await expect(page.getByText(state.listAnswer,{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'发布流程',exact:true})).toBeVisible();expect(state.writes).toEqual([])
 await page.screenshot({path:resolve(process.env.AEGIS_OBSERVATION_EVIDENCE_DIR||'../doc/重构前端/evidence/admin/A6/layout-revision','list-structured-'+test.info().project.name+'.png'),fullPage:true,animations:'disabled'})
 await page.locator('#obs-mode').selectOption('DOCUMENT');await page.locator('#obs-status').selectOption('FAILED');await page.getByRole('button',{name:'筛选',exact:true}).click();await expect.poll(()=>state.calls.filter(x=>x.path.endsWith('/session/list')).at(-1)?.body).toMatchObject({chatMode:'DOCUMENT',turnStatus:'FAILED',pageNo:'1'});expect(state.writes).toEqual([])
})
test('session focuses on rounds, summary uses an explicit reader with original text available',async({page,context})=>{
 const state=await mockObservation(context);state.summaryText='【长期会话摘要】\n这里是概要。\n\n【会话目标】\n明确用户需求。\n\n【已确认事实】\n- 原始事实，不应重写。\n\n【待跟进问题】\n这是需要展开才能阅读的完整跟进内容。';await seedAdmin(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/admin/observability/'+conversation)
 const rounds=page.getByRole('heading',{name:'会话轮次',exact:true});await expect(rounds).toBeVisible();const trigger=page.getByRole('button',{name:'查看完整摘要',exact:true});await expect(trigger).toBeVisible();await expect(page.getByText('这是需要展开才能阅读的完整跟进内容。',{exact:true})).toHaveCount(0);await page.screenshot({path:resolve(process.env.AEGIS_OBSERVATION_EVIDENCE_DIR||'../doc/重构前端/evidence/admin/A6/layout-revision','session-structured-'+test.info().project.name+'.png'),fullPage:true,animations:'disabled'})
 await trigger.click();const reader=page.getByRole('dialog',{name:'长期摘要',exact:true});await expect(reader).toBeVisible();await expect(reader.getByText('会话目标',{exact:true})).toBeVisible();await page.waitForTimeout(350);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);await page.keyboard.press('Escape');await expect(reader).toBeHidden();await expect(trigger).toBeFocused();expect(state.writes).toEqual([])
})
