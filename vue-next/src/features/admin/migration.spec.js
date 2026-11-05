import {it,expect} from 'vitest'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {resolve} from 'node:path'
import router from '../../router/index.js'
import {adminPages,adminEndpoints} from '../../../e2e/fixtures/admin-inventory.js'
import {createCatalogApi} from './catalog/catalogApi.js'
import {createDocumentApi} from './document/documentApi.js'
import {createRouteApi} from './knowledge-route/routeApi.js'
import {createObservationApi} from './observability/observabilityApi.js'
import {createOverviewApi} from './dashboard/overviewApi.js'
import {createConfigurationApi} from './configuration/configurationApi.js'
const root=resolve(import.meta.dirname,'../../..'),read=path=>readFileSync(resolve(root,path),'utf8')
const owners={A1:{consumer:'src/shared/auth/adminAuth.js',tests:['e2e/admin-auth.spec.js']},A2:{consumer:'src/features/admin/dashboard/overviewApi.js',tests:['e2e/admin-overview.spec.js']},A3:{consumer:'src/features/admin/catalog/catalogApi.js',tests:['e2e/admin-catalog.spec.js']},A4:{consumer:'src/features/admin/document/documentApi.js',tests:['e2e/admin-document.spec.js','e2e/admin-document-navigation.spec.js']},A5:{consumer:'src/features/admin/knowledge-route/routeApi.js',tests:['e2e/admin-route-workflow.spec.js','e2e/admin-route.spec.js']},A6:{consumer:'src/features/admin/observability/observabilityApi.js',tests:['e2e/admin-observation.spec.js','e2e/admin-observation-visual.spec.js']},A7:{consumer:'src/features/admin/configuration/configurationApi.js',tests:['e2e/admin-configuration.spec.js']}}
const unused=['queryKnowledgeBaseDetail','updateKnowledgeBaseConfig']
it('all A0 pages resolve inside the new administrator tree',()=>{
 for(const page of adminPages){const path=page.path.replace(':documentId','9007199254740993123').replace(':conversationId','session-long').replace(':exchangeId','exchange-long'),resolved=router.resolve(path);expect(resolved.matched.length,page.path).toBeGreaterThan(0);expect(resolved.name,page.path).not.toBe('AdminNotFound');expect(resolved.matched.some(r=>r.path==='/admin')).toBe(true)}
})
it('each A0 endpoint has an exercised new transport or an explicit unused-old-wrapper disposition',async()=>{
 const calls=[],client={json:async(path,body,options)=>{calls.push({path,body,method:options?.method||'POST'});return {}},multipart:async(path)=>{calls.push({path,method:'POST'})},download:async(path)=>{calls.push({path,method:'POST'})}},payload={documentId:'9007199254740993123',pageNo:1,pageSize:20}
 const catalog=createCatalogApi(client);await catalog.bases();await catalog.saveBase({});await catalog.deleteBase('long');await catalog.documents({keyword:'',page:1,size:20});await catalog.deleteDocument('long');await catalog.upload(new FormData())
 const document=createDocumentApi(client);for(const kind of ['detail','plan','parse','build','logs','artifacts','content','chunks','chunk','snapshot','diagnostic','pages','page','nodes','node','relations','graph','table'])await document.read(kind,payload);for(const kind of ['confirm','index'])await document.write(kind,payload);await document.download(payload)
 const route=createRouteApi(client);for(const kind of ['bases','scopes','topics','profile','relations','traces'])await route.read(kind,payload);for(const kind of ['scope','deleteScope','topic','deleteTopic','profile','batch','relation','removeRelation'])await route.write(kind,payload)
 const observation=createObservationApi(client);for(const kind of ['list','session','exchange','retrieval','channels','benchmarks'])await observation.read(kind,payload);await observation.rebuild('session-long')
 const overview=createOverviewApi(client);await overview.documents();await overview.routes();await overview.quality('0')
 const config=createConfigurationApi(client);await config.current();await config.history({pageNo:1,pageSize:10});await config.detail('9007199254740993123');await config.update({value:false,expectedVersion:6});await config.restore({historyId:'9007199254740993123',expectedVersion:6})
 const authSource=read(owners.A1.consumer);for(const endpoint of adminEndpoints.filter(e=>e.owner==='A1')){expect(authSource).toContain(`'${endpoint.path}'`);calls.push({path:endpoint.path,method:endpoint.method})}
 const paths=new Set(calls.map(x=>x.path)),expected=adminEndpoints.filter(e=>!unused.includes(e.name));expect([...paths].sort()).toEqual(expected.map(e=>e.path).sort())
 for(const endpoint of expected)expect(calls.some(c=>c.path===endpoint.path&&c.method===endpoint.method),endpoint.name).toBe(true)
 const endpoints=adminEndpoints.map(e=>({...e,...owners[e.owner],disposition:unused.includes(e.name)?'OLD_WRAPPER_WITHOUT_PAGE_CONSUMER':'IMPLEMENTED_TRANSPORT_AND_STAGE_BEHAVIOR_TESTS'}))
 for(const entry of endpoints){expect(read(entry.consumer).length).toBeGreaterThan(0);for(const file of entry.tests)expect(read(file)).toContain('test(')}
 const out=resolve(root,process.env.AEGIS_MIGRATION_EVIDENCE_DIR||'../doc/重构前端/evidence/admin/A8');mkdirSync(out,{recursive:true});writeFileSync(resolve(out,'路由与接口迁移覆盖.json'),JSON.stringify({note:'Transport exercise proves endpoint wiring; named stage behavior suites prove interactions, not screenshots alone.',pages:adminPages.map(p=>({...p,newRoute:router.resolve(p.path.replace(':documentId','document-long').replace(':conversationId','session-long').replace(':exchangeId','exchange-long')).matched.map(r=>r.path)})),endpoints},null,2))
})
