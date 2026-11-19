import request, { post } from './request'

export const listVehicles = () => post('/vehicle/list')
export const saveVehicle = value => post('/vehicle/save', value)
export const deleteVehicle = id => post('/vehicle/delete', { id })
export const listFuel = id => post('/vehicle/fuel/list', { id })
export const saveFuel = value => post('/vehicle/fuel/save', value)
export const deleteFuel = id => post('/vehicle/fuel/delete', { id })
export const listVehicleExpenses = id => post('/vehicle/expense/list', { id })
export const saveVehicleExpense = value => post('/vehicle/expense/save', value)
export const deleteVehicleExpense = id => post('/vehicle/expense/delete', { id })
export const listVehicleExpenseTypes = () => post('/vehicle/expense-types/list')
export const saveVehicleExpenseType = value => post('/vehicle/expense-types/save', value)
export const vehicleSummary = id => post('/vehicle/summary', { id })
export function importVehicleFile(file, sheet, vehicleId, digest) {
  const data = new FormData()
  data.append('file', file)
  if (sheet) data.append('sheet', sheet)
  if (vehicleId) data.append('vehicleId', vehicleId)
  if (digest) data.append('digest', digest)
  return request.post(digest ? '/vehicle/import/confirm' : '/vehicle/import/preview', data, { timeout: 60000 })
}
