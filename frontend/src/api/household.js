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

export function createHouseholdInvite(payload) {
  return post('/household/invite/create', payload)
}

export function listHouseholdInvites() {
  return post('/household/invite/list')
}

export function revokeHouseholdInvite(id) {
  return post('/household/invite/revoke', { id })
}

export function previewHouseholdInvite(inviteCode) {
  return post('/household/invite/preview', { inviteCode })
}

export function joinHousehold(inviteCode) {
  return post('/household/join', { inviteCode })
}
