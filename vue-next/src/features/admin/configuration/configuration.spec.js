import {it,expect,vi} from 'vitest'
import {controlValue,storedValue,configGroups,formatValue,editable,version,historyIdentity} from './configurationModel.js'
import {createConfigurationApi} from './configurationApi.js'
const percent={valueType:'DECIMAL',controlType:'PERCENTAGE',displayScale:100,minValue:0,maxValue:1,value:0.29}
it('preserves percentage precision and rejects empty numeric input instead of sending zero',()=>{
 expect(controlValue(percent)).toBe('29');expect(storedValue(percent,'29')).toBe(0.29)
 expect(storedValue(percent,'12.3456789')).toBe(0.123456789)
 expect(()=>storedValue(percent,'')).toThrow();expect(()=>storedValue(percent,'101')).toThrow()
 expect(()=>storedValue(percent,'0e999999999')).toThrow()
})
it('preserves JSON types, whitespace, zero and strict booleans',()=>{
 expect(storedValue({valueType:'BOOLEAN',controlType:'CHECKBOX'},false)).toBe(false)
 expect(()=>storedValue({valueType:'BOOLEAN',controlType:'CHECKBOX'},'false')).toThrow()
 expect(storedValue({valueType:'STRING',controlType:'TEXTAREA',maxLength:20},' a\nb ')).toBe(' a\nb ')
 expect(()=>storedValue({valueType:'STRING',controlType:'TEXTAREA',maxLength:2},'abc')).toThrow()
 expect(storedValue({valueType:'INTEGER',controlType:'NUMBER'},'0')).toBe(0)
 expect(()=>storedValue({valueType:'INTEGER',controlType:'NUMBER'},'1.2')).toThrow()
 expect(()=>storedValue({valueType:'LONG',controlType:'DURATION'},'9007199254740993')).toThrow()
})
it('unknown types and unsafe versions cannot write, identities stay exact',()=>{
 expect(editable({valueType:'UNKNOWN',controlType:'NUMBER'})).toBe(false)
 expect(()=>version(null)).toThrow();expect(()=>version(2147483648)).toThrow();expect(version(0)).toBe(0)
 expect(historyIdentity('9007199254740993123')).toBe('9007199254740993123')
 expect(()=>historyIdentity(9007199254740993)).toThrow()
})
it('groups in server order, searches metadata without exposing string values',()=>{
 const categories=[{groupKey:'g',groupLabel:'组',categoryKey:'a',items:[{configKey:'prompt',label:'提示',value:'private-text',valueType:'STRING',controlType:'TEXTAREA'}]},{groupKey:'g',categoryKey:'b',items:[{configKey:'zero',valueType:'INTEGER',value:0}]}]
 expect(configGroups(categories,'')[0].categories).toHaveLength(2)
 expect(configGroups(categories,'private-text')).toEqual([]);expect(configGroups(categories,'prompt')[0].categories[0].items).toHaveLength(1)
 expect(formatValue(percent)).toBe('29%');expect(formatValue({valueType:'INTEGER',value:null})).toBe('—')
 expect(formatValue({valueType:'BOOLEAN',value:false})).toBe('已停用')
})
it('configuration transport keeps raw JSON numeric versions and booleans, opaque history ID',async()=>{
 const json=vi.fn().mockResolvedValue({});const api=createConfigurationApi({json});const payload={configKey:'enabled',value:false,expectedVersion:6,changeNote:'说明'}
 await api.update(payload);await api.restore({historyId:'9007199254740993123',expectedVersion:7,changeNote:''})
 expect(json.mock.calls[0][1]).toEqual(payload);expect(json.mock.calls[1][1].historyId).toBe('9007199254740993123')
 expect(json.mock.calls[0][0]).toBe('/manage/config/item/update')
})
