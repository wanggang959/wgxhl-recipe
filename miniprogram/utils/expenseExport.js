const WIDTH=720,MAX_HEIGHT=8000
function totalCents(items){return items.reduce((sum,item)=>{const amount=Number(item.actualAmount??0);return sum+(Number.isFinite(amount)?Math.round((amount+Number.EPSILON)*100):0)},0)}
function money(cents){const sign=cents<0?'-':'';const absolute=Math.abs(cents);return sign+Math.floor(absolute/100)+'.'+String(absolute%100).padStart(2,'0')}
function wrap(ctx,value,width){const text=String(value||'—').replace(/\s+/g,' ').trim()||'—',lines=[];let line='';for(const char of text){if(line&&ctx.measureText(line+char).width>width){lines.push(line);line=char}else line+=char}if(line)lines.push(line);return lines}
function layout(ctx,name,items){
 if(!items.length)throw Error('当前筛选条件下没有开支项')
 ctx.font='bold 34px sans-serif';const title=wrap(ctx,name,360)
 ctx.font='bold 28px sans-serif';const total=wrap(ctx,'¥'+money(totalCents(items)),245)
 ctx.font='26px sans-serif';const rows=items.map(item=>({name:wrap(ctx,item.itemName,300),quantity:wrap(ctx,item.quantity,105),amount:wrap(ctx,'¥'+money(totalCents([item])),175)}))
 const start=85+Math.max(title.length,total.length)*46+72,height=start+rows.reduce((sum,row)=>sum+Math.max(row.name.length,row.quantity.length,row.amount.length)*38+26,0)+48
 if(height>MAX_HEIGHT)throw Error('清单过长，请选择分类或状态后再导出')
 return {title,total,rows,start,height}
}
function draw(ctx,data){
 ctx.fillStyle='#fffaf5';ctx.fillRect(0,0,WIDTH,data.height)
 ctx.fillStyle='#ff7912';ctx.font='bold 21px sans-serif';ctx.textAlign='left';ctx.fillText('专项开支清单',44,46)
 ctx.textAlign='right';ctx.fillStyle='#8b6e5b';ctx.font='21px sans-serif';ctx.fillText('总花费',676,46)
 ctx.fillStyle='#da650e';ctx.font='bold 28px sans-serif';data.total.forEach((line,i)=>ctx.fillText(line,676,91+i*46))
 ctx.textAlign='left';ctx.fillStyle='#302720';ctx.font='bold 34px sans-serif';data.title.forEach((line,i)=>ctx.fillText(line,44,91+i*46))
 const header=data.start-47;ctx.fillStyle='#fff0df';ctx.fillRect(32,header-30,656,50);ctx.fillStyle='#805b43';ctx.font='bold 23px sans-serif';ctx.fillText('开支项目',44,header+3);ctx.fillText('数量',370,header+3);ctx.textAlign='right';ctx.fillText('实际金额',676,header+3)
 ctx.font='26px sans-serif';let y=data.start+12
 for(const row of data.rows){const count=Math.max(row.name.length,row.quantity.length,row.amount.length);ctx.fillStyle='#342d29';ctx.textAlign='left';row.name.forEach((line,i)=>ctx.fillText(line,44,y+i*38));ctx.fillStyle='#7b6b60';row.quantity.forEach((line,i)=>ctx.fillText(line,370,y+i*38));ctx.fillStyle='#da650e';ctx.textAlign='right';row.amount.forEach((line,i)=>ctx.fillText(line,676,y+i*38));y+=count*38+26;ctx.strokeStyle='#f0e2d6';ctx.beginPath();ctx.moveTo(44,y-23);ctx.lineTo(676,y-23);ctx.stroke()}
}
async function loadEntries(post,filters){let page=1,records=[],total=null;const ids=new Set();do{const data=await post('/expense/item/page',{...filters,current:page,size:50}),batch=data?.records||[],count=Number(data?.total||0);if(total!==null&&count!==total)throw Error('清单发生变化，请重新导出');total=count;if(total>121)throw Error('清单过长，请选择分类或状态后再导出');for(const item of batch){if(!item.id||ids.has(item.id))throw Error('清单发生变化，请重新导出');ids.add(item.id)}records=records.concat(batch);page++;if(!batch.length){if(records.length<total)throw Error('清单读取不完整，请重新导出');break}}while(records.length<total);return records.map((item,index)=>({item,index})).sort((a,b)=>Number(a.item.status==='DONE')-Number(b.item.status==='DONE')||a.index-b.index).map(entry=>entry.item)}
module.exports={WIDTH,MAX_HEIGHT,totalCents,money,wrap,layout,draw,loadEntries}
