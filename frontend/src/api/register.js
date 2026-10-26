import { post } from './request'

export function sendRegisterCode(email) {
  return post('/user/register/sendCode', { email })
}

export function getRegisterInviteInfo(inviteCode) {
  return post('/user/register/inviteInfo', { inviteCode })
}

export function registerByEmail(payload) {
  return post('/user/register/email', payload)
}

export function registerByInvite(payload) {
  return post('/user/register/invite', payload)
}
