const pairs={INTEGER:['NUMBER'],LONG:['DURATION','NUMBER'],DECIMAL:['NUMBER','PERCENTAGE'],BOOLEAN:['CHECKBOX'],STRING:['TEXTAREA']}
export const editable=item=>!!pairs[item?.valueType]?.includes(item.controlType)&&!item.readOnly&&!item.masked
export function version(value){if(typeof value!=='number'||!Number.isInteger(value)||value<0||value>2147483647)throw new Error('配置版本无效，请重新读取。');return value}
export function historyIdentity(value){if(typeof value==='string'&&/^\d+$/.test(value))return value;if(typeof value==='number'&&Number.isSafeInteger(value)&&value>0)return String(value);throw new Error('历史记录身份无法安全读取。')}
function decimalText(value){
 const text=String(value??'').trim();if(!text||!/^[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[-+]?\d+)?$/i.test(text)||!Number.isFinite(Number(text)))throw new Error('请输入有效数字。')
 const [mantissa,power='0']=text.toLowerCase().split('e');if(Math.abs(Number(power))>324)throw new Error('数字精度超出允许范围。');const sign=mantissa.startsWith('-')?'-':'',unsigned=mantissa.replace(/^[-+]/,''),[whole,fraction='']=unsigned.split('.'),point=whole.length+Number(power),digits=whole+fraction
 const expanded=point<=0?'0.'+'0'.repeat(-point)+digits:point>=digits.length?digits+'0'.repeat(point-digits.length):digits.slice(0,point)+'.'+digits.slice(point)
 return sign+expanded
}
function scale(value,shift){const text=decimalText(value),sign=text.startsWith('-')?'-':'',[whole,fraction='']=text.replace(/^-/,'').split('.'),digits=whole+fraction,point=whole.length+shift;return sign+(point<=0?'0.'+'0'.repeat(-point)+digits:point>=digits.length?digits+'0'.repeat(point-digits.length):digits.slice(0,point)+'.'+digits.slice(point))}
function exponent(item){const value=item.displayScale??100;if(!Number.isInteger(value)||value<1||!Number.isInteger(Math.log10(value)))throw new Error('不支持该显示比例，请使用原版核对。');return Math.log10(value)}
export function controlValue(item){if(item?.valueType==='BOOLEAN')return item.value;if(item?.valueType==='STRING')return item.value??'';if(item.value==null)return '';return item.controlType==='PERCENTAGE'?String(Number(scale(item.value,exponent(item)))):String(item.value)}
export function storedValue(item,input){
 if(!editable(item))throw new Error('此配置类型仅支持查看。')
 if(item.valueType==='BOOLEAN'){if(typeof input!=='boolean')throw new Error('请选择布尔值。');return input}
 if(item.valueType==='STRING'){if(typeof input!=='string'||!input.trim())throw new Error('请输入配置文本。');if(item.maxLength!=null&&input.length>item.maxLength)throw new Error(`最多 ${item.maxLength} 个字符。`);return input}
 const number=Number(item.controlType==='PERCENTAGE'?scale(input,-exponent(item)):decimalText(input))
 if(['INTEGER','LONG'].includes(item.valueType)&&!Number.isSafeInteger(number))throw new Error('请输入浏览器可精确表示的整数。')
 if(item.valueType==='INTEGER'&&(number<-2147483648||number>2147483647))throw new Error('整数超出允许范围。')
 if((item.minValue!=null&&number<Number(item.minValue))||(item.maxValue!=null&&number>Number(item.maxValue)))throw new Error('数值超出允许范围。')
 return number
}
export function formatValue(item,value=item?.value){
 if(item?.masked)return '已脱敏'
 if(value==null)return '—'
 if(item?.valueType==='BOOLEAN')return value===true?'已启用':value===false?'已停用':'无法识别'
 if(item?.valueType==='STRING')return String(value).trim()?`已配置 · ${String(value).length} 字符`:'未配置'
 try{if(item?.controlType==='PERCENTAGE')return `${Number(scale(value,exponent(item)))}%`}catch{return '无法安全显示'}
 return typeof value==='object'?'结构化内容':`${value}${item?.unit?' '+item.unit:''}`
}
export function configGroups(categories=[],keyword=''){
 const groups=new Map(),query=keyword.trim().toLocaleLowerCase()
 for(const category of categories||[]){const items=(category.items||[]).filter(item=>!query||[item.label,item.description,item.configKey,category.categoryLabel,category.groupLabel].some(value=>String(value??'').toLocaleLowerCase().includes(query)));if(!items.length)continue;const key=category.groupKey||'unclassified';if(!groups.has(key))groups.set(key,{key,label:category.groupLabel||'未分类',categories:[]});groups.get(key).categories.push({...category,items})}
 return [...groups.values()]
}
export const findItem=(current,key)=>current?.categories?.flatMap(category=>category.items||[]).find(item=>item.configKey===key)
