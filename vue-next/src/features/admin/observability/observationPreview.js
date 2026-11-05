import {marked} from 'marked'
import DOMPurify from 'dompurify'

// Display-only projection. Archived answers and evidence remain unchanged.
export function observationPreview(source,limit=180){
 const fragment=DOMPurify.sanitize(marked.parse(String(source??''),{async:false}),{RETURN_DOM_FRAGMENT:true,FORBID_TAGS:['img','svg','script','style','iframe','form','input','video','audio']})
 fragment.querySelectorAll('p,li,th,td,pre,h1,h2,h3,h4,h5,h6,br').forEach(node=>{if(node.tagName==='BR')node.replaceWith(document.createTextNode(' '));else node.appendChild(document.createTextNode(' '))})
 return String(fragment.textContent||'').replace(/\s+/g,' ').trim().slice(0,limit)
}
