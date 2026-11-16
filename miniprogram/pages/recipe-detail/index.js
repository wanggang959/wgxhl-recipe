const api=require('../../services/api')
const session=require('../../services/session')
const {presentDetail}=require('../../utils/recipe')
Page({
  data:{loading:true,error:'',recipe:null,ingredients:[],seasonings:[],steps:[]},
  onLoad(options){if(!session.get()?.token){wx.reLaunch({url:'/pages/login/index'});return}this.id=options.id||'';this.load()},
  async load(){if(!this.id){this.setData({loading:false,error:'菜谱不存在'});return}this.setData({loading:true,error:''});try{const detail=await api.post('/recipe/getById',{id:this.id});if(!detail?.recipe)throw new Error('菜谱不存在或无权查看');this.setData(presentDetail(detail))}catch(error){this.setData({error:error.message||'菜谱加载失败'})}finally{this.setData({loading:false})}},
  retry(){this.load()}
})
