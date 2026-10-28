import { post } from './request'

export const securityStatus = () => post('/user/security/status')
export const requestPasswordReset = (email) => post('/user/security/requestReset', { email })
export const checkResetLink = (token) => post('/user/security/resetInfo', { token })
export const confirmPasswordReset = (token, newPassword) => post('/user/security/confirmReset', { token, newPassword })
export const sendEmailVerification = (payload) => post('/user/security/email/send', payload)
export const confirmEmailVerification = (payload) => post('/user/security/email/confirm', payload)
export const memberResetOptions = (userId) => post('/user/security/member/options', { userId })
export const requestMemberReset = (userId) => post('/user/security/member/requestReset', { userId })
export const resetManagedMember = (payload) => post('/user/security/member/resetManaged', payload)
