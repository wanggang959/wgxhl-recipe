<script setup>
const props = defineProps({
  todo: {
    type: Object,
    required: true,
  },
  compact: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['open', 'complete', 'completeBlocked', 'edit', 'delete'])

const categoryMap = {
  COOK: ['🍲', '做饭'],
  GROCERY: ['🛒', '采购'],
  LIFE: ['📦', '生活'],
  BIRTHDAY: ['🎂', '生日'],
  ANNIVERSARY: ['💐', '纪念日'],
  SERVER: ['💻', '服务器'],
  DOMAIN: ['🌐', '域名'],
  SSL: ['🔐', '证书'],
  PAYMENT: ['💳', '缴费'],
  OTHER: ['📝', '其他'],
}

function categoryInfo(category) {
  return categoryMap[category] || categoryMap.OTHER
}

function handleComplete() {
  if (props.todo.canComplete === false) {
    emit('completeBlocked', props.todo)
    return
  }
  emit('complete', props.todo)
}
</script>

<template>
  <article class="todo-card" :class="{ compact, done: todo.status === 'DONE' }" @click="emit('open', todo)">
    <div class="todo-icon">{{ categoryInfo(todo.category)[0] }}</div>
    <div class="todo-main">
      <div class="todo-title-line">
        <h3>{{ todo.title }}</h3>
        <span>{{ categoryInfo(todo.category)[1] }}</span>
      </div>
      <div class="todo-meta">
        <span v-if="todo.ownerName">负责人：{{ todo.ownerName }}</span>
        <span v-if="todo.birthdayDisplayText">生日：{{ todo.birthdayDisplayText }}</span>
        <span v-else-if="todo.dueLabel">时间：{{ todo.dueLabel }}</span>
        <span v-if="todo.remainText">{{ todo.remainText }}</span>
        <span v-if="todo.nextNotifyLabel">下次通知时间：{{ todo.nextNotifyLabel }}</span>
      </div>
      <p v-if="todo.description && !compact">{{ todo.description }}</p>
      <div v-if="!compact" class="todo-actions" @click.stop>
        <button
          v-if="todo.status !== 'DONE'"
          type="button"
          class="primary"
          :class="{ disabled: todo.canComplete === false }"
          @click="handleComplete"
        >
          <van-icon name="success" />
          完成
        </button>
        <button type="button" @click="emit('edit', todo)">
          <van-icon name="edit" />
          编辑
        </button>
        <button type="button" class="danger" @click="emit('delete', todo)">
          <van-icon name="delete-o" />
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.todo-card {
  padding: 12px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 10px 24px rgba(154, 52, 18, 0.07);
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 10px;
}

.todo-card.compact {
  padding: 10px;
  border-radius: 15px;
  box-shadow: none;
}

.todo-card.done {
  opacity: 0.72;
  background: #fafafa;
}

.todo-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: #fff7e8;
  display: grid;
  place-items: center;
  font-size: 22px;
}

.todo-main {
  min-width: 0;
}

.todo-title-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.todo-title-line h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.todo-title-line span {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--app-primary-soft);
  color: #c2410c;
  font-size: 12px;
  font-weight: 800;
}

.todo-meta {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.45;
}

.todo-meta span {
  display: block;
  width: 100%;
  overflow-wrap: anywhere;
}

.todo-main p {
  margin: 8px 0 0;
  color: #6f5a49;
  font-size: 13px;
  line-height: 1.5;
}

.todo-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.todo-actions button {
  height: 34px;
  min-width: 0;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 11px;
  background: #fffaf2;
  color: #7c5c46;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-weight: 800;
}

.todo-actions .primary {
  background: var(--app-primary);
  border-color: var(--app-primary);
  color: #fff;
}

.todo-actions .primary.disabled {
  background: #f8dcc4;
  border-color: #f8dcc4;
  color: #9a6b4f;
}

.todo-actions .danger {
  width: 34px;
  padding: 0;
  color: #dc2626;
  background: #fff1f2;
}
</style>
