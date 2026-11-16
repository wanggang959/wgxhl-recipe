Component({
  data:{selected:'todos',hidden:false,items:[
    {key:'home',label:'首页',icon:'van-icon-wap-home-o',url:'/pages/home/index'},
    {key:'today',label:'今天吃什么',icon:'van-icon-fire-o'},
    {key:'favorites',label:'收藏',icon:'van-icon-star-o'},
    {key:'wanted',label:'想吃',icon:'van-icon-cart-o'},
    {key:'todos',label:'待办',icon:'van-icon-todo-list-o',url:'/pages/todos/index'},
    {key:'profile',label:'我的',icon:'van-icon-contact-o',url:'/pages/profile/index'}
  ]},
  methods:{
    navigate(e){
      const item=this.data.items.find(entry=>entry.key===e.currentTarget.dataset.key)
      if(!item)return
      if(!item.url){wx.showToast({title:'功能开发中',icon:'none'});return}
      if(item.key!==this.data.selected)wx.switchTab({url:item.url})
    }
  }
})

