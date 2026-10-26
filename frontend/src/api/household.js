import { post } from './request'

export function listMyHouseholds() {
  return post('/household/list')
}

export function switchHousehold(id) {
  return post('/household/switch', { id })
}

export function createHousehold(householdName) {
  return post('/household/create', { householdName })
}
