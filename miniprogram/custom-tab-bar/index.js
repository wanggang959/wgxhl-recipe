const session=require('../services/session')
const {isGuest}=require('../utils/profile')
const allItems=[
  {key:'home',label:'首页',icon:'van-icon-wap-home-o',url:'/pages/home/index'},
  {key:'favorites',label:'收藏',icon:'van-icon-star-o',url:'/pages/favorites/index'},
  {key:'wanted',label:'想吃',icon:'van-icon-cart-o',url:'/pages/wanted/index'},
  {key:'todos',label:'待办',icon:'van-icon-todo-list-o',url:'/pages/todos/index'},
  {key:'profile',label:'我的',icon:'van-icon-contact-o',url:'/pages/profile/index'}
]
const guestItems=allItems.filter(item=>['home','profile'].includes(item.key))
Component({
  data:{selected:'todos',hidden:false,items:allItems},
  lifetimes:{
    attached(){this.refreshItems()}
  },
  pageLifetimes:{
    show(){this.refreshItems()}
  },
  methods:{
    refreshItems(){
      const user=session.get()
      const items=isGuest(user||{})?guestItems:allItems
      const selected=items.some(item=>item.key===this.data.selected)?this.data.selected:'home'
      this.setData({items,selected})
    },
    navigate(e){
      const item=this.data.items.find(entry=>entry.key===e.currentTarget.dataset.key)
      if(!item || !item.url)return
      if(item.key!==this.data.selected)wx.switchTab({url:item.url})
    }
  }
})
