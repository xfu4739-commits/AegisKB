import { describe,it,expect } from 'vitest'
import { buildRouteWrite, scopedDocuments, parseCandidates, routeStatus, pageSlice } from './routeModel.js'
import { createRouteApi } from './routeApi.js'
describe('route configuration boundaries',()=>{
 it('does not label missing trace status as failure or invent candidates',()=>{expect(routeStatus(null)).toBe('未记录');expect(routeStatus('alien')).toBe('alien');expect(parseCandidates('broken').error).toBeTruthy();expect(parseCandidates('[{"documentId":"9007199254740993123","score":0}]').items[0]).toMatchObject({documentId:'9007199254740993123',score:0})})
 it('paginates only the loaded view',()=>{expect(pageSlice(Array.from({length:25},(_,i)=>i),2)).toEqual([20,21,22,23,24])})
 it('keeps document scope and long IDs exact',()=>{expect(scopedDocuments([{documentId:'9007199254740993123',knowledgeBaseId:'a'},{documentId:'b',knowledgeBaseId:'b'}],'a').map(x=>x.documentId)).toEqual(['9007199254740993123'])})
 it('requires explicit operator and valid scope',()=>{expect(()=>buildRouteWrite('scope',{scopeName:'范围'}, {knowledgeBaseId:'a',scopes:[],topics:[],documents:[]})).toThrow();expect(()=>buildRouteWrite('topic',{topicName:'主题',scopeId:'outside',operatorId:'7'},{knowledgeBaseId:'a',scopes:[],topics:[],documents:[]})).toThrow()})
 it('freezes only selected in-scope batch targets',()=>{const context={knowledgeBaseId:'a',documents:[{documentId:'long'}]};expect(buildRouteWrite('batch',{documentIds:['long','long'],operatorId:'7'},context)).toEqual({documentIds:['long'],operatorId:'7'});expect(()=>buildRouteWrite('batch',{documentIds:['outside'],operatorId:'7'},context)).toThrow();expect(()=>buildRouteWrite('batch',{documentIds:[],operatorId:'7'},context)).toThrow()})
 it('rejects invalid scores and unknown actions',()=>{expect(()=>buildRouteWrite('relation',{operatorId:'7',topicId:'t',documentId:'d',relationScore:'2'},{knowledgeBaseId:'a',topics:[{id:'t'}],documents:[{documentId:'d'}]})).toThrow();expect(()=>buildRouteWrite('all',{},{})).toThrow()})
 it('uses existing endpoints with string numbers',async()=>{let call;const api=createRouteApi({json:async(...args)=>{call=args}});await api.read('traces',{pageNo:2});expect(call[0]).toBe('/manage/knowledge/route/trace/page/query');expect(call[1]).toEqual({pageNo:'2'});expect(()=>api.write('unknown',{})).toThrow()})
})
