<script setup>
defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  readonly: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['open', 'read', 'delete'])

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="notification-list">
    <div v-if="!items.length" class="empty-notice">
      <van-icon name="bell" size="28" />
      <strong>暂无通知</strong>
      <span>新的家庭提醒会出现在这里</span>
    </div>

    <van-swipe-cell
      v-for="item in items"
      :key="item.id"
      class="notice-swipe"
      :disabled="readonly"
    >
      <button
        type="button"
        class="notice-item"
        :class="{ unread: !item.isRead }"
        @click="emit('open', item)"
      >
        <span class="notice-icon">
          <van-icon name="volume-o" size="18" />
        </span>
        <span class="notice-body">
          <span class="notice-row">
            <strong>{{ item.title }}</strong>
            <em v-if="!item.isRead">未读</em>
          </span>
          <p>{{ item.content }}</p>
          <span class="notice-meta">
            <time>{{ formatTime(item.createTime) }}</time>
            <span v-if="item.relatedId">待办详情</span>
          </span>
        </span>
        <span v-if="!readonly && !item.isRead" class="read-link" @click.stop="emit('read', item)">标为已读</span>
      </button>
      <template v-if="!readonly" #right>
        <button type="button" class="swipe-delete" @click="emit('delete', item)">
          <van-icon name="delete-o" size="18" />
          删除
        </button>
      </template>
    </van-swipe-cell>
  </div>
</template>

<style scoped>
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notice-swipe {
  border-radius: 18px;
  overflow: hidden;
}

.empty-notice {
  min-height: 180px;
  border: 1px dashed rgba(249, 115, 22, 0.26);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--app-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
}

.empty-notice .van-icon {
  color: var(--app-primary);
}

.empty-notice strong {
  color: var(--app-text);
  font-size: 16px;
}

.empty-notice span {
  font-size: 13px;
}

.notice-item {
  position: relative;
  width: 100%;
  min-height: 96px;
  border: 1px solid rgba(245, 223, 199, 0.9);
  border-radius: 18px;
  padding: 13px 12px;
  background: rgba(255, 255, 255, 0.9);
  text-align: left;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
  box-shadow: 0 10px 24px rgba(154, 52, 18, 0.06);
}

.notice-item.unread {
  border-color: rgba(249, 115, 22, 0.34);
  background: #fff7e8;
}

.notice-icon {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  background: #fff3e4;
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

.unread .notice-icon {
  background: var(--app-primary);
  color: #fff;
}

.notice-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.notice-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.notice-row strong {
  min-width: 0;
  flex: 1;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-row em {
  flex: 0 0 auto;
  height: 22px;
  border-radius: 999px;
  padding: 0 8px;
  background: var(--app-primary);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
  line-height: 22px;
}

.notice-item p {
  margin: 0;
  color: #6f5a49;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.notice-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.notice-meta time {
  font: inherit;
}

.read-link {
  grid-column: 2;
  justify-self: start;
  min-height: 28px;
  border-radius: 999px;
  padding: 5px 10px;
  background: #fff;
  color: #c2410c;
  font-size: 12px;
  font-weight: 800;
}

.swipe-delete {
  width: 78px;
  height: 100%;
  min-height: 96px;
  border: 0;
  background: #ef4444;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 800;
}
</style>
