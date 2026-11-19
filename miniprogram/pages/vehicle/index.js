const api = require('../../services/api')
const session = require('../../services/session')
const { chooseImage, uploadImage } = require('../../utils/imageUpload')
const { imageUrl } = require('../../utils/recipe')
const { VEHICLE_RANGES, readingPoints, nearestPlotIndex, ringSliceAt } = require('../../utils/vehicleCharts')
const { fuelOverview, spendOverview, expenseSummary } = require('../../utils/vehicleOverview')
const { fuelRecordYears, fuelRecordList, fuelTripReport, expenseRecordList } = require('../../utils/vehicleRecords')
const pad = n => String(n).padStart(2, '0')
function now() { const d = new Date(); return { date: `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`, time: `${pad(d.getHours())}:${pad(d.getMinutes())}` } }
const price = n => n == null ? '暂无' : Number(n).toFixed(2)
const typeColors = ['#FA6356', '#F8BF19', '#DC358A', '#24BF63', '#88978F', '#2B83CF', '#826AC1', '#287D78']
const clearCharts = () => ({trendIndex: -1, fuelDetail: null, spendDetail: null, compositionDetail: null})
const tapXY = e => [e.changedTouches?.[0]?.clientX ?? e.detail?.x, e.changedTouches?.[0]?.clientY ?? e.detail?.y]
const trendPoints = (trend, range) => readingPoints(trend, range).map(point => ({...point, label:price(point.value)}))
const cleanRecordFilters = () => ({query:'',start:'',end:'',category:''})
const cleanRecords = () => ({recordYear:'',recordYears:[],recordYearLabels:[],recordYearIndex:-1,recordExpanded:{},
  recordCollapsed:{},recordFilters:cleanRecordFilters(),fuelList:null,expenseList:null,recordsAllExpanded:false})
Page({
  updateRecordLists() {
    const { tab, fuels, expenses, stats, recordYear, recordFilters, expenseTypes, recordExpanded } = this.data
    if (tab === 'records') {
      const recordYears = fuelRecordYears(fuels)
      const year = recordYears.includes(recordYear) ? recordYear : recordYears[0] || String(new Date().getFullYear())
      const fuelList = fuelRecordList(fuels, stats, year)
      fuelList.entries = fuelList.entries.map(({record, ...entry}) => ({...entry, station:record.station, remark:record.remark}))
      this.setData({recordYears, recordYearLabels:recordYears.map(y => y + '年'), recordYear:year,
        recordYearIndex:recordYears.indexOf(year), fuelList,
        recordsAllExpanded:fuelList.entries.length > 0 && fuelList.entries.every(row => recordExpanded[row.id])})
    } else if (tab === 'expenseRecords') {
      const expenseList = expenseRecordList(expenses, recordFilters, expenseTypes)
      expenseList.groups = expenseList.groups.map(group => ({...group, entries:group.entries.map(({record, ...entry}) => entry)}))
      const recordCategoryIndex = expenseList.categories.indexOf(recordFilters.category) + 1
      this.setData({expenseList, recordCategoryLabels:['全部类型', ...expenseList.categories], recordCategoryIndex,
        hasRecordFilters:Object.values(recordFilters).some(Boolean)})
    }
  },
  selectRecordYear(e) { this.changeRecordYear(Number(e.detail.value)) },
  stepRecordYear(e) { this.changeRecordYear(this.data.recordYearIndex + Number(e.currentTarget.dataset.step)) },
  changeRecordYear(index) {
    const recordYear = this.data.recordYears[index]
    if (!recordYear || recordYear === this.data.recordYear) return
    this.setData({recordYear, recordExpanded:{}})
    this.updateRecordLists()
  },
  toggleRecord(e) {
    const id = e.currentTarget.dataset.id
    const recordExpanded = {...this.data.recordExpanded, [id]:!this.data.recordExpanded[id]}
    const entries = this.data.fuelList?.entries
    this.setData({recordExpanded, recordsAllExpanded:!!entries?.length && entries.every(row => recordExpanded[row.id])})
  },
  toggleAllRecords() {
    const entries = this.data.fuelList?.entries
    if (!entries?.length) return
    const recordsAllExpanded = !this.data.recordsAllExpanded
    this.setData({recordsAllExpanded, recordExpanded:recordsAllExpanded ? Object.fromEntries(entries.map(row => [row.id, true])) : {}})
  },
  toggleExpenseMonth(e) {
    const month = e.currentTarget.dataset.key
    this.setData({recordCollapsed:{...this.data.recordCollapsed, [month]:!this.data.recordCollapsed[month]}})
  },
  changeRecordFilter(e) {
    const key = e.currentTarget.dataset.key
    if (!['query','start','end'].includes(key)) return
    this.setData({[`recordFilters.${key}`]:e.detail.value})
    this.updateRecordLists()
  },
  chooseRecordCategory(e) {
    const index = Number(e.detail.value)
    this.setData({'recordFilters.category':this.data.expenseList?.categories[index - 1] || ''})
    this.updateRecordLists()
  },
  clearRecordFilters() {
    this.setData({recordFilters:cleanRecordFilters(),recordCollapsed:{}})
    this.updateRecordLists()
  },
  closeRecordList() { this.switchTab(this.data.tab === 'records' ? 'fuel' : 'expense') },
  async loadTypes() {
    if (this.data.typesLoading) return
    this.setData({typesLoading: true, typesError: ''})
    try { this.updateTypes(await api.post('/vehicle/expense-types/list') || []) }
    catch (e) { this.setData({typesError: e.message || '费用类型加载失败'}) }
    finally { this.setData({typesLoading: false}) }
  },
  updateTypes(types) {
    this.setData({expenseTypes: types})
    if (this.data.formType === 'expense') this.updateExpenseOptions()
    this.updateDashboard()
  },
  openTypes() { this.setData({typesShown: true, typeEditing:false}); if (!this.data.expenseTypes.length) this.loadTypes() },
  toggleTypeEditing() { if (!this.data.typeSaving) this.setData({typeEditing: !this.data.typeEditing}) },
  closeTypes() {
    if (this.data.typeSaving) return
    if (this.data.typeDraft) this.setData({typeDraft: null, typeError: ''})
    else this.setData({typesShown: false, typeError: ''}, () => this.drawTrend())
  },
  editType(e) {
    if (this.data.typeSaving || this.data.typesLoading) return
    const existing = this.data.expenseTypes.find(type => type.key === e.currentTarget.dataset.key)
    this.setData({typeDraft: existing ? {...existing, color: existing.color.toUpperCase()} : {name:'', color:typeColors[5], enabled:true}, typeError:''})
  },
  typeName(e) { this.setData({'typeDraft.name': e.detail.value}) },
  typeColor(e) { this.setData({'typeDraft.color': e.currentTarget.dataset.color}) },
  toggleType(e) {
    const type = this.data.expenseTypes.find(row => row.key === e.currentTarget.dataset.key)
    if (type) return this.persistType({...type, enabled: !type.enabled})
  },
  saveType() { if (this.data.typeDraft) return this.persistType(this.data.typeDraft) },
  async persistType(type) {
    if (this.data.typeSaving) return
    this.setData({typeSaving: true, typeError: ''})
    try {
      const types = await api.post('/vehicle/expense-types/save', type)
      this.updateTypes(types)
      this.setData({typeDraft: null})
    } catch (e) { this.setData({typeError: e.message || '保存失败'}) }
    finally { this.setData({typeSaving: false}) }
  },
  updateExpenseOptions() {
    const { form, expenseTypes } = this.data
    const choices = expenseTypes.filter(type => type.enabled)
    let key = form.categoryKey
    if (form.legacyCategory && !choices.some(type => type.name === form.legacyCategory)) {
      choices.unshift({ key:'__legacy', name:form.legacyCategory, label:form.legacyCategory + '（原类型）' })
    }
    if (!choices.some(type => type.key === key)) {
      key = choices.find(type => type.name === form.legacyCategory)?.key || ''
    }
    this.setData({expenseChoices: choices.map(type => ({...type, label:type.label || type.name})), 'form.categoryKey': key,
      'form.category': choices.find(type => type.key === key)?.name || ''})
  },
  chooseExpenseType(e) {
    const type = this.data.expenseChoices[Number(e.detail.value)]
    if (type) this.setData({'form.categoryKey': type.key, 'form.category': type.name})
  },
  data: { vehicles: [], currentId: '', current: null, showVehiclePicker: false, fuels: [], expenses: [], stats: {}, tab: 'fuel', trendRange: '6m', fuelRange: 'rolling', ranges: VEHICLE_RANGES, rangeLabels: VEHICLE_RANGES.map(r => r.label), rangeIndexes: {trendRange:0,fuelRange:2,spendRange:1,compositionRange:2}, ...clearCharts(), ...cleanRecords(), recordCategoryLabels:[], recordCategoryIndex:0, hasRecordFilters:false, chartPoints: [], spendRange: 'year', compositionRange: 'rolling', overview: {}, spending: {}, composition: {}, fuelMetrics: [], expenseMetrics: [], latestLabel: '暂无', ownedDays: null, form: {}, formType: '', expenseTypes: [], expenseChoices: [], typesShown: false, typesLoading: false, typesError: '', typeDraft: null, typeColors, typeSaving: false, typeError: '', photoFile: '', photoPreview: '', photoChoosing: false, members: [], user: null, loading: false, saving: false, error: '', showImport: false, importFile: '', importName: '', importSheets: [], importSheet: '', importVehicle: '', importVehicleName: '', importData: null, importBusy: false, importErrors: 0, importDuplicates: 0 },
  async onLoad() { const user = session.get(); if (!user?.token) return wx.reLaunch({ url: '/pages/login/index' }); if (user.id === 'guest' || user.memberRole === 'guest') { wx.showToast({ title: '游客不能查看车辆', icon: 'none' }); return wx.navigateBack() } this.setData({user}); this.loadTypes(); try { const members = (await api.post('/user/members') || []).map(m=>({...m,displayName:m.nickname||m.username})); this.setData({members}) } catch (_) { wx.showToast({title:'家庭成员加载失败',icon:'none'}) } this.loadVehicles() },
  async loadVehicles() { try { const vehicles = (await api.post('/vehicle/list') || []).map(vehicle => ({...vehicle, photoUrl: imageUrl(vehicle.imagePath)})); const currentId = vehicles.some(v => v.id === this.data.currentId) ? this.data.currentId : vehicles[0]?.id || ''; this.setData({ vehicles, currentId, current: vehicles.find(v => v.id === currentId) || null }); await this.load() } catch (e) { this.setData({ error: e.message || '加载失败' }) } },
  async load() {
    const id = this.data.currentId, version = (this.version || 0) + 1
    this.version = version
    if (!id) { this.setData({ fuels: [], expenses: [], stats: {}, chartPoints: [], loading: false }); return }
    this.setData({loading: true, error: ''})
    try {
      const [fuels, expenses, stats] = await Promise.all([api.post('/vehicle/fuel/list', { id }), api.post('/vehicle/expense/list', { id }), api.post('/vehicle/summary', { id })])
      if (version !== this.version) return
      const display = row => ({ ...row, dateLabel: String(row.occurredAt).replace('T', ' ').slice(0, 16), amountLabel: price(row.paidAmount ?? row.amount) })
      this.setData({ fuels: fuels.map(display), expenses: expenses.map(display), stats, chartPoints: trendPoints(stats.trend, this.data.trendRange), ...clearCharts() })
      this.updateDashboard()
    } catch (e) {
      if (version === this.version) this.setData({error: e.message || '加载失败'})
    } finally {
      if (version === this.version) this.setData({loading: false})
    }
  },
  updateDashboard() {
    const { fuels, expenses, stats, fuelRange, spendRange, compositionRange, current, expenseTypes, compositionDetail } = this.data
    const date = new Date()
    const overview = fuelOverview(fuels, date, fuelRange)
    const totals = expenseSummary(overview, expenses, current?.ownedSince)
    const items = rows => rows.map(([name, value, unit]) => ({name, value: value == null ? '—' : price(value), unit}))
    const spending = spendOverview(fuels, expenses, spendRange, date, expenseTypes)
    const composition = spendOverview(fuels, expenses, compositionRange, date, expenseTypes)
    const donut = this.donutColors(composition, compositionDetail)
    this.setData({
      overview, spending, composition, donut, hasMonthlyFuel: overview.months.some(month => month.amount > 0),
      latestLabel: price(stats.latest ?? stats.trend?.[stats.trend.length - 1]?.value),
      ownedDays: totals.ownedDays,
      fuelMetrics: items([
        ['平均油耗',stats.average,'升/百公里'], ['平均行程',overview.dailyMileage,'公里/天'],
        ['平均油费',overview.costPerKm,'元/公里'], ['统计里程',overview.distance,'公里'],
        ['累计油费',overview.total,'元'], ['累计优惠',overview.discount,'元'],
      ]),
      expenseMetrics: items([
        ['总支出',totals.total,'元'], ['油费总计',overview.total,'元'], ['费用总计',totals.extra,'元'],
        ['支出/公里',totals.perKm,'元'], ['油费/公里',overview.costPerKm,'元'], ['成本/天',totals.perDay,'元'],
      ]),
      compositionTotal: price(composition.total),
      compositionCategories: composition.categories.map(item => ({...item, label:price(item.amount)})),
    }, () => this.drawTrend())
    this.updateRecordLists()
  },
  openVehiclePicker() { this.setData({showVehiclePicker: true}) },
  closeVehiclePicker() { this.setData({showVehiclePicker: false}, () => this.drawTrend()) },
  selectVehicle(e) {
    const current = this.data.vehicles.find(vehicle => vehicle.id === e.currentTarget.dataset.id)
    if (!current || current.id === this.data.currentId) return this.closeVehiclePicker()
    this.setData({showVehiclePicker: false, currentId: current.id, current, fuels: [], expenses: [], stats: {}, chartPoints: [], ...clearCharts(), ...cleanRecords()})
    this.updateDashboard()
    return this.load()
  },
  manageVehiclesFromPicker() { this.switchTab('vehicles') },
  setTab(e) { this.switchTab(e.currentTarget.dataset.value) },
  switchTab(tab) {
    const isRecords = this.data.current && (tab === 'records' || tab === 'expenseRecords')
    this.setData({tab, showVehiclePicker: false, ...clearCharts(), ...(tab !== this.data.tab ? cleanRecords() : {})})
    this.updateDashboard()
    wx.setNavigationBarTitle?.({title:isRecords ? '' : '车辆管理'})
    wx.setNavigationBarColor?.({frontColor:'#000000',backgroundColor:isRecords ? '#e6f7eb' : tab === 'expense' ? '#ade4bd' : '#269ebf'})
    if (typeof wx.pageScrollTo === 'function') wx.pageScrollTo({scrollTop: 0, duration: 0})
  },
  openDetails() { this.setTab({currentTarget:{dataset:{value: this.data.tab.startsWith('expense') ? 'expenseRecords' : 'records'}}}) },
  setChartRange(e) {
    const key = e.currentTarget.dataset.field
    const index = Number(e.detail.value), range = VEHICLE_RANGES[index]
    const detail = {trendRange:'trendIndex', fuelRange:'fuelDetail', spendRange:'spendDetail', compositionRange:'compositionDetail'}[key]
    if (!detail || !range) return
    const changes = {[key]: range.value, [`rangeIndexes.${key}`]:index, [detail]: key === 'trendRange' ? -1 : null}
    if (key === 'trendRange') changes.chartPoints = trendPoints(this.data.stats.trend, range.value)
    this.setData(changes)
    this.updateDashboard()
  },
  tapTrend(e) {
    const [x] = tapXY(e)
    wx.createSelectorQuery().select('#vehicle-trend').boundingClientRect(rect => {
      if (!rect) return
      const index = nearestPlotIndex(x - rect.left, 24, rect.width - 18, this.data.chartPoints.length)
      this.setData({trendIndex: this.data.trendIndex === index ? -1 : index}, () => this.drawTrend())
    }).exec()
  },
  tapBar(e) {
    const { kind, key } = e.currentTarget.dataset
    const field = kind === 'fuel' ? 'fuelDetail' : 'spendDetail'
    const bars = kind === 'fuel' ? this.data.overview.months : this.data.spending.bars
    const item = bars.find(bar => bar.key === key)
    if (item) this.setData({[field]: this.data[field]?.key === key ? null : {...item, label:price(item.amount),
      segments:(item.segments || []).map(row => ({...row, label:price(row.amount)}))}})
  },
  donutColors(composition, selected) {
    return composition.slices.map(slice => `${selected && selected.name !== slice.name ? '#dbe7df' : slice.color} ${slice.start}% ${slice.end}%`).join(', ')
  },
  selectComposition(name) {
    const { composition, compositionDetail } = this.data
    const item = name !== compositionDetail?.name && composition.categories.find(row => row.name === name)
    const detail = item ? {...item, label:price(item.amount), percent:price(item.amount / composition.total * 100)} : null
    this.setData({compositionDetail: detail, donut:this.donutColors(composition, detail)})
  },
  tapLegend(e) { this.selectComposition(e.currentTarget.dataset.name) },
  tapDonut(e) {
    const [x,y] = tapXY(e)
    wx.createSelectorQuery().select('#expense-donut').boundingClientRect(rect => {
      if (!rect) return
      const slice = ringSliceAt(x - rect.left, y - rect.top, rect.width, rect.height, this.data.composition.slices)
      this.selectComposition(slice?.name)
    }).exec()
  },
  drawTrend() {
    const points=this.data.chartPoints
    if(this.data.tab!=='fuel'||!points.length||this.data.tripReport||this.data.formType||this.data.showImport||this.data.showVehiclePicker||this.data.typesShown||typeof wx.createSelectorQuery!=='function'||typeof wx.createCanvasContext!=='function')return
    wx.createSelectorQuery().select('#vehicle-trend').boundingClientRect(rect=>{
      if(!rect||!rect.width)return
      const width=rect.width,height=150,left=24,right=width-18,top=20,bottom=height-30,values=points.map(p=>p.value),min=Math.min(...values),max=Math.max(...values),range=max-min
      const pos=points.map((p,i)=>({x:points.length===1?(left+right)/2:left+i*(right-left)/(points.length-1),y:range===0?(top+bottom)/2:bottom-(p.value-min)*(bottom-top)/range,p}))
      const ctx=wx.createCanvasContext('vehicle-trend',this);ctx.setStrokeStyle('#d9e5df');ctx.setLineWidth(1);ctx.beginPath();ctx.moveTo(left,bottom);ctx.lineTo(right,bottom);ctx.stroke()
      if(pos.length>1){ctx.setFillStyle('#cff0dd');ctx.beginPath();ctx.moveTo(pos[0].x,bottom);pos.forEach(v=>ctx.lineTo(v.x,v.y));ctx.lineTo(pos[pos.length-1].x,bottom);ctx.fill()}
      const meanY=pos.reduce((sum,p)=>sum+p.y,0)/pos.length
      if(typeof ctx.setLineDash==='function'){ctx.setLineDash([3,3]);ctx.setStrokeStyle('#cc6a75');ctx.beginPath();ctx.moveTo(left,meanY);ctx.lineTo(right,meanY);ctx.stroke();ctx.setLineDash([])}
      ctx.setStrokeStyle('#13a66b');ctx.setLineWidth(2);ctx.beginPath();pos.forEach((v,i)=>i?ctx.lineTo(v.x,v.y):ctx.moveTo(v.x,v.y));ctx.stroke()
      const selected=pos[this.data.trendIndex], step=Math.max(1,Math.ceil(pos.length/6))
      if(selected){ctx.setStrokeStyle('#087f4b');ctx.beginPath();ctx.moveTo(selected.x,top);ctx.lineTo(selected.x,bottom);ctx.stroke()}
      pos.forEach((v,i)=>{
        ctx.beginPath();ctx.setFillStyle(v===selected?'#087f4b':'#fff');ctx.setStrokeStyle('#13a66b');ctx.arc(v.x,v.y,v===selected?5:3,0,2*Math.PI);ctx.fill();ctx.stroke()
        if(i%step===0||i===pos.length-1){
          ctx.setFillStyle('#718079');ctx.setFontSize(9);ctx.setTextAlign('center')
          ctx.fillText((v.p.day || v.p.month).slice(0,7),v.x,height-8)
        }
      });ctx.draw()
    }).exec()
  },
  backToAssistant() { wx.switchTab({url:'/pages/profile/index'}) },
  chooseRecord() { if(!this.data.currentId){wx.showToast({title:'请先添加车辆',icon:'none'});return} wx.showActionSheet({itemList:['记加油','记费用'],success:({tapIndex})=>this.open({currentTarget:{dataset:{type:tapIndex===0?'fuel':'expense'}}})}) },
  setChoice(e) { const key=e.currentTarget.dataset.field, value=e.currentTarget.dataset.value; if(['fullTank','warningLight','missedPrevious'].includes(key)) this.setData({[`form.${key}`]:value===true || value==='true'}) },
  updateDiscount(e) { const value=e.detail.value, shown=Number(this.data.form.displayedAmount); if(value!=='' && Number.isFinite(Number(value)) && this.data.form.displayedAmount!==''){ this.paidManually=true; const paid=(shown-Number(value)).toFixed(2); this.setData({'form.paidAmount':paid,'form.discount':value,'form.actualUnit':Number(this.data.form.liters)>0?(Number(paid)/Number(this.data.form.liters)).toFixed(2):''}) } },
  updateActualUnit(e) { const value=e.detail.value, liters=Number(this.data.form.liters); if(value!=='' && Number.isFinite(Number(value)) && liters>0){ this.paidManually=true; const paid=(Number(value)*liters).toFixed(2); this.setData({'form.paidAmount':paid,'form.actualUnit':value,'form.discount':this.data.form.displayedAmount!==''?(Number(this.data.form.displayedAmount)-Number(paid)).toFixed(2):''}) } },
  open(e) { const type = e.currentTarget.dataset.type, id = e.currentTarget.dataset.id; let form; this.paidManually=Boolean(id); if (type === 'vehicle') form = id ? { ...this.data.vehicles.find(vehicle => vehicle.id === id) } : { name: '', vehicleType: 'CAR', model: '' }; else { const row = (type === 'fuel' ? this.data.fuels : this.data.expenses).find(x => x.id === id); const t = now(); form = row ? { ...row, date: String(row.occurredAt).slice(0,10), time: String(row.occurredAt).slice(11,16) } : type === 'fuel' ? { vehicleId: this.data.currentId, ...t, mileage: '', unitPrice: '', liters: '', displayedAmount: '', paidAmount: '', fullTank: true, warningLight: false, missedPrevious: false, fuelGrade: '', station: '', remark: '' } : { vehicleId: this.data.currentId, ...t, category: '', amount: '', remark: '' }; if(!row) { form.includeLedger=false; form.payerId=this.data.user.id; form.payerName=this.data.members.find(m=>m.id===form.payerId)?.displayName || '' } } if(type==='fuel') { form.actualUnit=Number(form.liters)>0 && form.paidAmount!=='' ? (Number(form.paidAmount)/Number(form.liters)).toFixed(2) : ''; form.discount=form.displayedAmount!=='' && form.displayedAmount!=null && form.paidAmount!=='' && form.paidAmount!=null ? (Number(form.displayedAmount)-Number(form.paidAmount)).toFixed(2) : ''; const prev=this.data.fuels.find(x=>x.id!==form.id && x.occurredAt <= `${form.date}T${form.time}:00`); form.previousMileage=prev ? `${prev.dateLabel} · ${prev.mileage} 公里` : '' } if (type === 'expense') { form.legacyCategory = form.category || ''; form.categoryKey = '' } this.setData({ formType: type, form, photoFile: '', photoPreview: type === 'vehicle' ? imageUrl(form.imagePath) : '' }); if (type === 'expense') this.updateExpenseOptions() },
  close() { if (!this.data.saving && !this.data.photoChoosing) this.setData({ formType: '', photoFile: '', photoPreview: '' },()=>this.drawTrend()) },
  async selectPhoto() {
    if (this.data.saving || this.data.photoChoosing) return
    this.setData({photoChoosing: true})
    try {
      const [file] = await chooseImage(1)
      if (file) this.setData({photoFile: file, photoPreview: file})
    } catch (e) {
      wx.showToast({title: e.message, icon: 'none'})
    } finally {
      this.setData({photoChoosing: false})
    }
  },
  removePhoto() {
    if (!this.data.saving && !this.data.photoChoosing) this.setData({photoFile: '', photoPreview: '', 'form.imagePath': ''})
  },
  photoLoadFailed(e) {
    const index = this.data.vehicles.findIndex(vehicle => vehicle.id === e.currentTarget.dataset.id)
    if (index >= 0 && this.data.vehicles[index].photoUrl) this.setData({[`vehicles[${index}].photoUrl`]: ''})
  },
  field(e) { const k = e.currentTarget.dataset.field; const allowed = ['name','model','ownedSince','date','time','mileage','unitPrice','liters','displayedAmount','paidAmount','fuelGrade','station','remark','category','amount']; if (!allowed.includes(k)) return; const value=e.detail.value, patch={ [`form.${k}`]: value }; if(k==='paidAmount') this.paidManually=true; if((k==='unitPrice'||k==='liters')&&!this.data.form.id) { const unit=k==='unitPrice'?value:this.data.form.unitPrice, liters=k==='liters'?value:this.data.form.liters; if(unit!==''&&liters!==''&&Number.isFinite(Number(unit))&&Number.isFinite(Number(liters))) { const amount=(Number(unit)*Number(liters)).toFixed(2); patch['form.displayedAmount']=amount; if(!this.paidManually)patch['form.paidAmount']=amount } } if(this.data.formType==='fuel' && ['unitPrice','liters','displayedAmount','paidAmount'].includes(k)){ const f=this.data.form, shown=k==='displayedAmount'?value:(patch['form.displayedAmount']??f.displayedAmount), paid=k==='paidAmount'?value:(patch['form.paidAmount']??f.paidAmount), liters=k==='liters'?value:f.liters; patch['form.discount']=shown!==''&&shown!=null&&paid!==''&&paid!=null?(Number(shown)-Number(paid)).toFixed(2):''; patch['form.actualUnit']=Number(liters)>0&&paid!==''&&paid!=null?(Number(paid)/Number(liters)).toFixed(2):'' } this.setData(patch) },
  typeChange(e) { this.setData({ 'form.vehicleType': Number(e.detail.value) === 0 ? 'CAR' : 'MOTORCYCLE' }) },
  toggle(e) { const k = e.currentTarget.dataset.field; if (['fullTank','warningLight','missedPrevious','includeLedger'].includes(k)) this.setData({ [`form.${k}`]: e.detail.value }) },
  payerChange(e) { const m=this.data.members[Number(e.detail.value)]; if(m)this.setData({'form.payerId':m.id,'form.payerName':m.displayName}) },
  openTripReport(e) { this.showTripReport(e.currentTarget.dataset.id) },
  showTripReport(id, savedRow) {
    const { fuels, stats, current } = this.data
    const rows = savedRow && !fuels.some(row => row.id === id) ? [savedRow, ...fuels] : fuels
    const report = fuelTripReport(rows, stats, id)
    if (!report || !current) return false
    this.setData({ tripReport: report, tripReportPhoto: current.photoUrl || '' })
    return true
  },
  resetTripReportPhoto() {
    if (this.data.tripReportPhoto) this.setData({ tripReportPhoto: '' })
  },
  closeTripReport() {
    this.setData({ tripReport: null, tripReportPhoto: '' }, () => this.drawTrend())
  },
  async save() {
    if (this.data.saving || this.data.photoChoosing) return
    const type = this.data.formType, form = { ...this.data.form }
    if (type === 'expense' && !form.categoryKey) return wx.showToast({title:'请选择支出类型', icon:'none'})
    if (type === 'expense') {
      if (form.categoryKey === '__legacy') delete form.categoryKey
      delete form.legacyCategory
    }
    if (type === 'vehicle' && !form.name?.trim()) return wx.showToast({title: '请输入车辆名称', icon: 'none'})
    if (type !== 'vehicle') form.occurredAt = `${form.date}T${form.time}:00`
    for (const k of ['unitPrice','displayedAmount']) if (form[k] === '') form[k] = null
    this.setData({ saving: true })
    try {
      if (type === 'vehicle') {
        delete form.photoUrl
        if (this.data.photoFile) {
          form.imagePath = await uploadImage(this.data.photoFile, 'vehicle')
          this.setData({'form.imagePath': form.imagePath, photoFile: '', photoPreview: imageUrl(form.imagePath)})
        }
      }
      const result = await api.post(type === 'vehicle' ? '/vehicle/save' : type === 'fuel' ? '/vehicle/fuel/save' : '/vehicle/expense/save', form)
      if (type === 'vehicle') this.setData({ currentId: result.id })
      const nextTab = type === 'expense' ? 'expenseRecords' : type === 'fuel' && this.data.tab !== 'records' ? 'fuel' : this.data.tab
      this.setData({ formType: '', photoFile: '', photoPreview: '' })
      if (nextTab !== this.data.tab) this.switchTab(nextTab)
      await this.loadVehicles()
      const opened = type === 'fuel' && !form.id && result?.id && form.vehicleId === this.data.currentId
        && this.showTripReport(result.id, { ...form, ...result })
      if (!opened) wx.showToast({ title: '已保存' })
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },
  remove(e) { const { id, type } = e.currentTarget.dataset; wx.showModal({ title: '删除记录', content: '删除后无法恢复，已生成的家庭账单会保留。确定继续吗？', success: async ({ confirm }) => { if (!confirm) return; try { await api.post(type === 'fuel' ? '/vehicle/fuel/delete' : '/vehicle/expense/delete', { id }); wx.showToast({ title: '已删除' }); this.load() } catch (err) { wx.showToast({ title: err.message || '删除失败', icon: 'none' }) } } }) },
  deleteVehicle() { const id=this.data.form.id; wx.showModal({title:'删除车辆',content:'只有没有加油和费用记录的车辆才能删除，确定继续吗？',success:async ({confirm})=>{if(!confirm)return;try{await api.post('/vehicle/delete',{id});this.setData({formType:''});await this.loadVehicles();wx.showToast({title:'已删除'})}catch(e){wx.showToast({title:e.message||'删除失败',icon:'none'})}}}) },
  openImport() { this.setData({ showImport: true, importVehicle: this.data.currentId, importVehicleName: this.data.current?.name || '', importData: null }) },
  closeImport() { if (!this.data.importBusy) this.setData({ showImport: false },()=>this.drawTrend()) },
  chooseFile() { wx.chooseMessageFile({ count: 1, type: 'file', extension: ['xlsx'], success: async ({ tempFiles }) => { const file = tempFiles?.[0]; if (!file) return; this.setData({ importFile: file.path, importName: file.name, importSheets: [], importSheet: '', importData: null, importBusy: true }); try { const data = await api.upload('/vehicle/import/preview', file.path); this.setData({ importSheets: data.sheets || [] }) } catch (e) { wx.showToast({ title: e.message || '读取失败', icon: 'none' }) } finally { this.setData({ importBusy: false }) } } }) },
  importSheetChange(e) { this.setData({ importSheet: this.data.importSheets[Number(e.detail.value)] || '', importData: null }) },
  importVehicleChange(e) { const v = this.data.vehicles[Number(e.detail.value)]; if (v) this.setData({ importVehicle: v.id, importVehicleName: v.name, importData: null }) },
  async previewImport() { const { importFile, importSheet, importVehicle } = this.data; if (!importFile || !importSheet || !importVehicle) return wx.showToast({ title: '请选择文件、工作表和车辆', icon: 'none' }); this.setData({ importBusy: true, importData: null }); try { const data = await api.upload('/vehicle/import/preview', importFile, { sheet: importSheet, vehicleId: importVehicle }); const rows = (data.rows || []).map(r => ({ ...r, timeLabel: r.record ? String(r.record.occurredAt).replace('T',' ').slice(0,16) : '' })); this.setData({ importData: { ...data, rows }, importErrors: rows.filter(r => r.error).length, importDuplicates: rows.filter(r => r.duplicate).length }) } catch (e) { wx.showToast({ title: e.message || '预览失败', icon: 'none' }) } finally { this.setData({ importBusy: false }) } },
  confirmImport() { const { importData, importErrors, importDuplicates, importFile, importSheet, importVehicle } = this.data; if (!importData || importErrors || this.data.importBusy) return; wx.showModal({ title: '确认导入', content: `将导入 ${importData.rows.length - importDuplicates} 条，跳过 ${importDuplicates} 条重复记录，不会自动记账。`, success: async ({ confirm }) => { if (!confirm) return; this.setData({ importBusy: true }); try { const data = await api.upload('/vehicle/import/confirm', importFile, { sheet: importSheet, vehicleId: importVehicle, digest: importData.digest }); this.setData({ showImport: false, importData: null, currentId: importVehicle }); wx.showToast({ title: `已导入${data.imported}条`, icon: 'none' }); await this.loadVehicles() } catch (e) { wx.showToast({ title: e.message || '导入失败', icon: 'none' }) } finally { this.setData({ importBusy: false }) } } }) },
  swallow() {}
})
