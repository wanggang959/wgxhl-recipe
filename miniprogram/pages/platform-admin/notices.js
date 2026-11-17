const api=require('../../services/api')
const channels={site:'站内',email:'邮件',push:'App',coalesced:'规则合并',none:'未开启'}
const statuses={SUCCESS:'发送成功',FAIL:'发送失败',SKIP:'已跳过',PENDING:'补发处理中'}
const time=value=>value?String(value).replace('T',' ').slice(0,19):'暂无记录'
const decorate=item=>({...item,_key:item.id||[item.household_id,item.todo_id,item.rule_id,item.recipient_key,item.notify_type,item.target_time].join('|'),channelLabel:channels[item.notify_type]||item.notify_type||'未知',statusLabel:statuses[item.send_status]||item.send_status||'未知',sendLabel:time(item.send_time),targetLabel:time(item.target_time),updateLabel:time(item.update_time)})

module.exports={
  async loadNotices(reset=false){
    if(this.data.loading&&!reset)return
    const tab=this.data.tab
    if(!['notices','uncertain'].includes(tab))return
    const version=(this.requestVersion||0)+1;this.requestVersion=version
    const uncertain=tab==='uncertain',page=reset?1:(uncertain?this.data.uncertainPage:this.data.noticePage)+1
    const filters=reset?{...this.data.noticeFilters,keyword:this.data.noticeKeyword.trim()}:this.data.appliedNoticeFilters
    this.setData({loading:true,error:'',...(reset?(uncertain?{uncertainItems:[],uncertainPage:0,uncertainTotal:0}:{noticeItems:[],noticePage:0,noticeTotal:0,appliedNoticeFilters:filters}):{})})
    try{
      const result=await api.get(uncertain?'/platform-admin/notices/uncertain':'/platform-admin/notices',uncertain?{page}:{page,...filters})
      if(version!==this.requestVersion||tab!==this.data.tab)return
      const items=(result?.items||[]).map(decorate)
      if(uncertain)this.setData({uncertainItems:reset?items:this.data.uncertainItems.concat(items),uncertainPage:Number(result?.page||page),uncertainTotal:Number(result?.total||0),gaps:(result?.gaps||[]).map(g=>({...g,startLabel:time(g.last_gap_start),endLabel:time(g.last_gap_end)}))})
      else this.setData({noticeItems:reset?items:this.data.noticeItems.concat(items),noticePage:Number(result?.page||page),noticeTotal:Number(result?.total||0)})
    }catch(error){if(version===this.requestVersion)this.setData({error:error.message||'加载失败'})}
    finally{if(version===this.requestVersion)this.setData({loading:false})}
  },
  noticeKeywordInput(e){this.setData({noticeKeyword:e.detail.value})},
  noticeFilter(e){const kind=e.currentTarget.dataset.kind,index=Number(e.detail.value),options=kind==='status'?this.data.noticeStatuses:kind==='channel'?this.data.noticeChannels:null;if(!options||!options[index])return;this.setData({[`noticeFilters.${kind}`]:options[index].value,[`${kind}Index`]:index})},
  noticeDate(e){const kind=e.currentTarget.dataset.kind;if(kind==='from'||kind==='to')this.setData({[`noticeFilters.${kind}`]:e.detail.value})},
  clearNoticeDates(){this.setData({'noticeFilters.from':'','noticeFilters.to':''})},
  searchNotices(){if(this.data.noticeFilters.from&&this.data.noticeFilters.to&&this.data.noticeFilters.from>this.data.noticeFilters.to){this.setData({error:'结束日期不能早于开始日期'});return}this.loadNotices(true)},
  recoverNotice(e){
    if(!this.data.authorized||this.data.busy||this.data.tab!=='uncertain')return
    const item=this.data.uncertainItems[Number(e.currentTarget.dataset.index)],action=e.currentTarget.dataset.action
    if(!item||!['RETRY','CLOSE'].includes(action)||(action==='RETRY'&&!item.todo_title))return
    const content=action==='RETRY'?'请先核对接收记录。原通知可能已经送达，补发可能产生重复通知。确认仅补发这位接收人的此渠道提醒吗？':'确认已核实并关闭这条记录？关闭后不会补发，也不会完成或删除待办。'
    wx.showModal({title:action==='RETRY'?'确认补发':'核实关闭',content,confirmText:action==='RETRY'?'确认补发':'确认关闭',confirmColor:action==='RETRY'?'#d86c1a':'#76543c',success:async({confirm})=>{
      if(!confirm||this.data.busy)return
      this.setData({busy:true})
      let failure=''
      try{await api.post('/platform-admin/notices/recover',{householdId:item.household_id,todoId:item.todo_id,ruleId:item.rule_id,recipientId:item.recipient_key,channel:item.notify_type,targetTime:item.target_time,expectedUpdateTime:item.update_time,action,confirmed:true},{timeout:90000})}
      catch(_){failure='处理结果未确认，请先刷新核查，不要直接重复补发'}
      finally{this.setData({busy:false});if(this.data.tab==='uncertain')await this.loadNotices(true);if(failure)wx.showModal({title:'请核查处理结果',content:failure,showCancel:false});else wx.showToast({title:'已处理',icon:'none'})}
    }})
  }
}
