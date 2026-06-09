import { post } from './request'

export function pageNotification(params) {
  return post('/notification/page', params)
}

export function unreadNotificationCount() {
  return post('/notification/unreadCount')
}

export function markNotificationRead(id) {
  return post('/notification/markRead', { id })
}

export function markAllNotificationRead() {
  return post('/notification/markAllRead')
}

export function deleteReadNotifications() {
  return post('/notification/deleteRead')
}

export function deleteNotification(id) {
  return post('/notification/delete', { id })
}
