const api = require('../../services/api')
const session = require('../../services/session')
const {withNoticeRoute,openNotice} = require('../../utils/noticeRoute')
function formatTime(value) { return String(value || '').replace('T',' ').slice(0,16) }
Component({
  properties:{
    visible:{type:Boolean,value:false,observer(value){ if (value) this.open() }}
  },
  data:{items:[],unread:0,page:0,total:0,loading:false,error:'',busy:false,readonly:false},
  methods:{
    open(){
      const user=session.get() || {}
      this.setData({readonly:String(user.userRole || '').toLowerCase()==='guest' || String(user.memberRole || '').toLowerCase()==='guest'})
      this.load(true)
    },
    close(){this.triggerEvent('close')},
    swallow(){},
    async load(reset){
      if(this.data.loading)return
      this.setData({loading:true,error:''})
      const current=reset?1:this.data.page+1
      try{
        const [page,count]=await Promise.all([api.post('/notification/page',{current,size:30}),api.post('/notification/unreadCount')])
        const records=(page?.records || []).map(item=>withNoticeRoute({...item,displayTime:formatTime(item.createTime),isRead:!!item.isRead}))
        this.setData({items:reset?records:this.data.items.concat(records),page:current,total:Number(page?.total || 0),unread:Math.max(0,Number(count)||0)})
      }catch(error){this.setData({error:error.message || '通知加载失败'})}
      finally{this.setData({loading:false})}
    },
    retry(){this.load(true)},
    loadMore(){if(this.data.items.length<this.data.total)this.load(false)},
    async markRead(e){
      const id=e.currentTarget.dataset.id
      const item=this.data.items.find(notice=>notice.id===id)
      if(this.data.readonly || this.data.busy || !item || item.isRead)return
      this.setData({busy:true})
      try{await api.post('/notification/markRead',{id});await this.load(true);this.triggerEvent('change')}
      catch(error){wx.showToast({title:error.message || '操作失败',icon:'none'})}
      finally{this.setData({busy:false})}
    },
    async markAll(){
      if(this.data.readonly || this.data.busy || !this.data.unread)return
      this.setData({busy:true})
      try{await api.post('/notification/markAllRead');await this.load(true);this.triggerEvent('change')}
      catch(error){wx.showToast({title:error.message || '操作失败',icon:'none'})}
      finally{this.setData({busy:false})}
    },
    remove(e){
      if(this.data.readonly || this.data.busy)return
      const item=this.data.items.find(notice=>notice.id===e.currentTarget.dataset.id)
      if(!item)return
      wx.showModal({title:'删除消息',content:`确认删除「${item.title || '这条消息'}」吗？`,confirmText:'删除',confirmColor:'#d64335',success:async ({confirm})=>{
        if(!confirm || this.data.busy)return
        this.setData({busy:true})
        try{await api.post('/notification/delete',{id:item.id});await this.load(true);this.triggerEvent('change')}
        catch(error){wx.showToast({title:error.message || '删除失败',icon:'none'})}
        finally{this.setData({busy:false})}
      }})
    },
    openTodo(e){
      const item=this.data.items.find(notice=>notice.id===e.currentTarget.dataset.id)
      if(!item || !item.relatedId || item.notificationType!=='TODO')return
      this.close()
      wx.navigateTo({url:`/pages/todo-detail/index?id=${encodeURIComponent(item.relatedId)}`})
    },
    openRelated(e){
      const item=this.data.items.find(notice=>notice.id===e.currentTarget.dataset.id)
      if(!item || !item.routeLabel)return
      this.close()
      openNotice(item)
    }
  }
})

