import { it,expect,vi } from 'vitest'
import { mergeLogs,latestLogId,tableHighlights,pipelineTypes,confirmPayload,buildPayload,isTaskRunning,safeImage,artifactMarkdown,jsonPaths } from './documentModel.js'
import { createDocumentApi } from './documentApi.js'
import { createTaskPoller } from './documentState.js'
it('long log IDs never pass through Number during merge, sort or cursor selection',()=>{
 const logs=mergeLogs([{id:'9007199254740993125',content:'old'}],[{id:'9007199254740993124'},{id:'9007199254740993125',content:'new'}])
 expect(logs.map(l=>l.id)).toEqual(['9007199254740993124','9007199254740993125']);expect(logs[1].content).toBe('new');expect(latestLogId(logs)).toBe('9007199254740993125')
})
it('existing table deep links keep identity and map rows, columns and cells to the wire',()=>{
 expect(tableHighlights({highlightTableId:'9007199254740993124',highlightRows:'1,2',highlightColumns:'金额,部门',highlightCells:'1:2'})).toEqual({highlightTableId:'9007199254740993124',highlightRowNos:['1','2'],highlightColumnNames:['金额','部门'],highlightCellCoordinates:['1:2']})
})
it('strategy payloads preserve order and require explicit operator, plan and supported pipelines',()=>{
 const plan={planId:'9007199254740993123',parentPipeline:{steps:[{strategyType:'2'},{strategyType:'1'}]},childPipeline:{steps:[{strategyType:'3'}]}}
 expect(pipelineTypes(plan,'parent')).toEqual(['2','1'])
 expect(confirmPayload({documentId:'123',planId:plan.planId,operatorId:'42',parent:['2','1'],child:['3'],adjustNote:'调整'}).parentSteps).toEqual([{stepNo:'1',strategyType:'2'},{stepNo:'2',strategyType:'1'}])
 expect(()=>confirmPayload({documentId:'123',planId:plan.planId,operatorId:'',parent:['1'],child:['2']})).toThrow()
 expect(()=>confirmPayload({documentId:'123',planId:plan.planId,operatorId:'42',parent:['9'],child:['2']})).toThrow()
})
it('build refuses unconfirmed, changed or active plans',()=>{
 const valid={documentId:'123',planId:'456',currentPlanId:'456',confirmed:true,dirty:false,running:false,operatorId:'42'}
 expect(buildPayload(valid)).toEqual({documentId:'123',planId:'456',operatorId:'42'})
 for(const extra of [{dirty:true},{confirmed:false},{running:true},{currentPlanId:'other'}])expect(()=>buildPayload({...valid,...extra})).toThrow()
})
it('terminal task states override stale running flags; unknown states never spin indefinitely',()=>{
 expect(isTaskRunning({taskStatus:'3',running:true})).toBe(false);expect(isTaskRunning({taskStatus:'4'})).toBe(false);expect(isTaskRunning({taskStatus:'2'})).toBe(true);expect(isTaskRunning({taskStatus:'unknown',running:true})).toBe(false)
})
it('artifact preview strips active HTML/images and rejects SVG or remote image URLs',()=>{
 expect(artifactMarkdown('<script>alert(1)</script>\n![x](https://example.com/pixel)\n[bad](javascript:alert(1))')).not.toMatch(/<script|<img|href="javascript:/)
 expect(jsonPaths('{"a":{"金额":120}}','金额')).toEqual([{path:'$["a"]["金额"]',value:'120'}]);expect(safeImage({dataUrl:'https://example.com/pixel'})).toBe('');expect(safeImage({dataUrl:'data:image/svg+xml;base64,abc'})).toBe('')
 expect(safeImage({dataUrl:'data:image/png;base64,aGVsbG8='})).toBe('data:image/png;base64,aGVsbG8=')
})
it('API keeps read/write endpoints, ID strings, boolean flags and numeric wire strings',async()=>{
 const client={json:vi.fn().mockResolvedValue({}),download:vi.fn()},api=createDocumentApi(client)
 await api.read('graph',{documentId:'123',maxNodes:80,includeIsolates:true});await api.read('parse',{documentId:'123',sinceLogId:'9007199254740993125'});await api.write('confirm',{documentId:'123',parentSteps:[{stepNo:1,strategyType:2}]})
 expect(client.json.mock.calls[0].slice(0,2)).toEqual(['/manage/document/rag/artifact/graph/window/query',{documentId:'123',maxNodes:'80',includeIsolates:true}]);expect(client.json.mock.calls[1][1].sinceLogId).toBe('9007199254740993125');expect(client.json.mock.calls[2][1].parentSteps).toEqual([{stepNo:'1',strategyType:'2'}])
})
it('polling is sequential, ends on terminal/error, and rejects stale responses after dispose',async()=>{
 vi.useFakeTimers();const load=vi.fn().mockResolvedValueOnce({taskId:'123',taskStatus:'2',logs:[{id:'9007199254740993125'}]}).mockResolvedValueOnce({taskId:'123',taskStatus:'3',logs:[]})
 const poll=createTaskPoller(load,100);await poll.start({documentId:'1'});await vi.advanceTimersByTimeAsync(100);await vi.advanceTimersByTimeAsync(1000);expect(load).toHaveBeenCalledTimes(2);expect(load.mock.calls[1][0].sinceLogId).toBe('9007199254740993125');expect(poll.data.value.logs).toHaveLength(1)
 poll.dispose();vi.useRealTimers()
})
it('slow polling never overlaps, stops on error, and disposal rejects a late response',async()=>{
 vi.useFakeTimers();let resolve
 const loader=vi.fn().mockImplementationOnce(()=>new Promise(r=>{resolve=r})).mockRejectedValueOnce(new Error('offline'))
 const state=createTaskPoller(loader,100),first=state.start({documentId:'1'})
 await vi.advanceTimersByTimeAsync(1000);expect(loader).toHaveBeenCalledTimes(1)
 resolve({documentId:'1',taskId:'2',taskStatus:'2',logs:[]});await first;await vi.advanceTimersByTimeAsync(100);expect(state.error.value).toBe('offline');await vi.advanceTimersByTimeAsync(1000);expect(loader).toHaveBeenCalledTimes(2);state.dispose()
 let finish;const leaving=createTaskPoller(()=>new Promise(r=>{finish=r}),100);const pending=leaving.start({documentId:'1'});leaving.dispose();finish({taskId:'2',taskStatus:'2'});await pending;expect(leaving.data.value).toBeNull();vi.useRealTimers()
})
