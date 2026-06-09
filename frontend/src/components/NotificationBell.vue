<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import {
  deleteNotification,
  deleteReadNotifications,
  markAllNotificationRead,
  markNotificationRead,
  pageNotification,
  unreadNotificationCount,
} from '../api/notification'
import NotificationList from './NotificationList.vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const visible = ref(false)
const unread = ref(0)
const items = ref([])
const loading = ref(false)

const NOTICE_DIALOG_OPTIONS = {
  teleport: 'body',
  overlayClass: 'notice-dialog-overlay',
  className: 'notice-dialog-panel',
}

const noticeSummary = computed(() => (unread.value > 0 ? `${unread.value} 条未读` : '暂无未读'))
const hasRead = computed(() => items.value.some((item) => item.isRead))

let bodyScrollLockCount = 0
let lockedScrollY = 0

onMounted(() => {
  refreshUnread()
})

onUnmounted(() => {
  unlockBodyScroll()
})

watch(visible, (open) => {
  if (open) {
    lockBodyScroll()
  } else {
    unlockBodyScroll()
  }
})

function lockBodyScroll() {
  if (bodyScrollLockCount === 0) {
    lockedScrollY = window.scrollY || document.documentElement.scrollTop || 0
    document.body.style.position = 'fixed'
    document.body.style.top = `-${lockedScrollY}px`
    document.body.style.left = '0'
    document.body.style.right = '0'
    document.body.style.overflow = 'hidden'
    document.documentElement.style.overflow = 'hidden'
  }
  bodyScrollLockCount += 1
}

function unlockBodyScroll() {
  if (bodyScrollLockCount === 0) return
  bodyScrollLockCount -= 1
  if (bodyScrollLockCount > 0) return
  document.body.style.position = ''
  document.body.style.top = ''
  document.body.style.left = ''
  document.body.style.right = ''
  document.body.style.overflow = ''
  document.documentElement.style.overflow = ''
  window.scrollTo(0, lockedScrollY)
}

function showNoticeConfirm(options) {
  return showConfirmDialog({
    ...NOTICE_DIALOG_OPTIONS,
    ...options,
  })
}

async function refreshUnread() {
  try {
    const res = await unreadNotificationCount()
    unread.value = Number(res.data || 0)
  } catch (error) {
    unread.value = 0
  }
}

async function openList() {
  visible.value = true
  await loadList()
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageNotification({ current: 1, size: 30 })
    items.value = res.data.records || []
    await refreshUnread()
  } catch (error) {
    showFailToast(error.message || '通知加载失败')
  } finally {
    loading.value = false
  }
}

async function markRead(item) {
  await markNotificationRead(item.id)
  await loadList()
}

async function markAll() {
  if (!unread.value) return
  await markAllNotificationRead()
  await loadList()
}

async function removeRead() {
  if (!hasRead.value) return
  try {
    await showNoticeConfirm({
      title: '删除已读',
      message: '确认删除所有已读消息吗？未读消息会保留。',
    })
    await deleteReadNotifications()
    items.value = items.value.filter((item) => !item.isRead)
    showSuccessToast({ message: '已删除已读消息', duration: 1400 })
  } catch (error) {
    if (error?.message) showFailToast(error.message || '删除失败')
  }
}

async function removeOne(item) {
  try {
    await showNoticeConfirm({
      title: '删除消息',
      message: `确认删除「${item.title || '这条消息'}」吗？`,
    })
    await deleteNotification(item.id)
    items.value = items.value.filter((notice) => notice.id !== item.id)
    if (!item.isRead && unread.value > 0) {
      unread.value -= 1
    }
    showSuccessToast({ message: '已删除', duration: 1200 })
  } catch (error) {
    if (error?.message) showFailToast(error.message || '删除失败')
  }
}

async function openNotice(item) {
  if (userStore.canMutate && !item.isRead) {
    await markRead(item)
  }
  if (item.relatedId) {
    visible.value = false
    window.location.hash = `#/todo/${item.relatedId}`
  }
}
</script>

<template>
  <button type="button" class="bell-button" @click="openList">
    <van-badge :content="unread || ''" max="99">
      <van-icon name="bell" size="20" />
    </van-badge>
  </button>

  <Teleport to="body">
    <div v-if="visible" class="notice-layer" @click.self="visible = false">
      <section class="notice-panel" @click.stop>
        <header class="notice-header">
          <div class="notice-title">
            <span>家庭通知</span>
            <h2>提醒中心</h2>
            <p>{{ noticeSummary }}</p>
          </div>
          <button type="button" class="close-button" @click="visible = false">
            <van-icon name="cross" size="18" />
          </button>
        </header>

        <div class="notice-toolbar">
          <div>
            <strong>{{ items.length }}</strong>
            <span>条消息</span>
          </div>
          <div v-if="userStore.canMutate" class="toolbar-actions">
            <button type="button" class="ghost" :disabled="!hasRead" @click="removeRead">
              <van-icon name="delete-o" size="15" />
              删除已读
            </button>
            <button type="button" :disabled="!unread" @click="markAll">
              <van-icon name="passed" size="15" />
              全部已读
            </button>
          </div>
        </div>

        <div class="notice-content">
          <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
          <NotificationList
            v-else
            :items="items"
            :readonly="!userStore.canMutate"
            @open="openNotice"
            @read="markRead"
            @delete="removeOne"
          />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.bell-button {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border: 1px solid var(--app-border);
  border-radius: 50%;
  padding: 0;
  background: #fff;
  color: var(--app-primary);
  display: inline-grid;
  place-items: center;
  line-height: 1;
  vertical-align: middle;
  appearance: none;
  -webkit-appearance: none;
}

.notice-layer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  padding-top: var(--safe-area-top);
  background: rgba(47, 38, 31, 0.24);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  overflow: hidden;
  touch-action: none;
}

.notice-panel {
  position: relative;
  width: min(100vw, var(--app-shell-width));
  height: min(78dvh, 620px);
  max-height: calc(100dvh - 28px - var(--safe-area-top));
  min-height: 420px;
  padding: 16px 14px calc(16px + var(--app-nav-safe-bottom));
  border: 1px solid rgba(245, 223, 199, 0.9);
  border-bottom: 0;
  border-radius: 22px 22px 0 0;
  background:
    linear-gradient(180deg, rgba(255, 237, 213, 0.8), rgba(255, 250, 242, 0) 132px),
    #fffaf2;
  box-shadow: 0 -18px 42px rgba(154, 52, 18, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  touch-action: pan-y;
}

.notice-header {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-right: 52px;
}

.notice-title span,
.notice-title h2,
.notice-title p {
  margin: 0;
}

.notice-title span {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 900;
}

.notice-title h2 {
  margin-top: 2px;
  color: var(--app-text);
  font-size: 24px;
  line-height: 1.15;
}

.notice-title p {
  margin-top: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.close-button {
  position: absolute;
  top: 18px;
  right: 14px;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(249, 115, 22, 0.16);
  border-radius: 50%;
  padding: 0;
  background: #fff;
  color: var(--app-primary);
  display: inline-grid;
  place-items: center;
  line-height: 1;
  appearance: none;
  -webkit-appearance: none;
  box-shadow: 0 10px 20px rgba(154, 52, 18, 0.08);
}

.close-button :deep(.van-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.notice-toolbar {
  flex: 0 0 auto;
  margin: 16px 0 12px;
  padding: 11px 12px;
  border: 1px solid rgba(245, 223, 199, 0.86);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.84);
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 10px 24px rgba(154, 52, 18, 0.06);
}

.notice-toolbar div {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--app-muted);
  font-size: 13px;
}

.notice-toolbar strong {
  color: var(--app-text);
  font-size: 20px;
  line-height: 1;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notice-toolbar button {
  height: 34px;
  border: 0;
  border-radius: 999px;
  padding: 0 12px;
  background: var(--app-primary);
  color: #fff;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.notice-toolbar button.ghost {
  background: #fff;
  color: var(--app-primary);
  border: 1px solid #fed7aa;
}

.notice-toolbar button:disabled {
  background: #f8dcc4;
  color: #a78b77;
}

.notice-toolbar button.ghost:disabled {
  background: #fff;
  color: #c4b5a8;
  border-color: #f1e7dc;
}

.notice-content {
  min-height: 0;
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-y;
  padding: 2px 0 4px;
}

.loading {
  margin-top: 42px;
}
</style>

<style>
.notice-dialog-overlay {
  z-index: 4000 !important;
}

.notice-dialog-panel {
  z-index: 4001 !important;
}
</style>
