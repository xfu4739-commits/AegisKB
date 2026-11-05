export const observationStatusColor=status=>({RUNNING:'processing',COMPLETED:'success',FAILED:'error',STOPPED:'warning'}[status]||'default')
export const observationStatusStyle=status=>({color:status==='COMPLETED'?'var(--admin-positive)':status==='FAILED'?'var(--admin-danger)':status==='RUNNING'?'var(--primary)':'var(--admin-muted)'})
export function observationTime(value){
 if(value==null||value==='')return '—'
 const date=new Date(typeof value==='string'&&/^\d+$/.test(value)?Number(value):value)
 return Number.isNaN(date.getTime())?String(value):new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(date).replaceAll('/','-')
}
export function observationDuration(value){if(value==null||value==='')return '—';const number=Number(value);return Number.isFinite(number)?number>=1000?`${(number/1000).toFixed(1).replace(/\.0$/,'')} s`:`${number} ms`:String(value)}
// Group only explicit headings in the original text. No inferred summary or reordered facts.
export function observationSummarySections(value){
 const sections=[];let title='摘要',lines=[],fence=''
 function flush(){const body=lines.join('\n').trim();if(body||title!=='摘要')sections.push({key:String(sections.length),title,body});lines=[]}
 for(const line of String(value??'').replace(/\r\n?/g,'\n').split('\n')){
  const marker=line.trim().match(/^(```|~~~)/)?.[1]
  if(marker){if(!fence)fence=marker;else if(fence===marker)fence='';lines.push(line);continue}
  const heading=!fence&&(line.match(/^\s*【([^】]+)】\s*(.*)$/)||line.match(/^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/))
  if(heading){flush();title=heading[1].trim();if(heading[2])lines.push(heading[2])}else lines.push(line)
 }
 flush();return sections
}
