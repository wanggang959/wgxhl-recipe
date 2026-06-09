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
  COOK: ['fire-o', '做饭'],
  GROCERY: ['cart-o', '采购'],
  LIFE: ['notes-o', '生活'],
  BIRTHDAY: ['birthday-cake-o', '生日'],
  ANNIVERSARY: ['like-o', '纪念日'],
  SERVER: ['desktop-o', '服务器'],
  DOMAIN: ['cluster-o', '域名'],
  SSL: ['shield-o', '证书'],
  PAYMENT: ['balance-pay', '缴费'],
  OTHER: ['todo-list-o', '其他'],
}

function categoryInfo(category) {
  return categoryMap[category] || categoryMap.OTHER
}

function descriptionLines(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
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
    <div class="todo-icon">
      <van-icon :name="categoryInfo(todo.category)[0]" />
    </div>
    <div class="todo-main">
      <div class="todo-title-line">
        <h3>{{ todo.title }}</h3>
        <span>{{ categoryInfo(todo.category)[1] }}</span>
      </div>
      <div class="todo-meta">
        <span v-if="todo.ownerName"><van-icon name="contact-o" />{{ todo.ownerName }}</span>
        <span v-if="todo.birthdayDisplayText"><van-icon name="birthday-cake-o" />{{ todo.birthdayDisplayText }}</span>
        <span v-else-if="todo.dueLabel"><van-icon name="clock-o" />{{ todo.dueLabel }}</span>
        <span v-if="todo.remainText">{{ todo.remainText }}</span>
        <span v-if="todo.nextNotifyLabel"><van-icon name="bell" />提醒 {{ todo.nextNotifyLabel }}</span>
      </div>
      <div v-if="todo.description && !compact" class="todo-description">
        <span v-for="line in descriptionLines(todo.description)" :key="line">{{ line }}</span>
      </div>
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
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff 0%, #fffdf9 100%);
  border: 1px solid rgba(245, 223, 199, 0.96);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.07);
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
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
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: linear-gradient(180deg, #fff7e8, #fff);
  border: 1px solid #ffedd5;
  color: var(--app-primary);
  display: grid;
  place-items: center;
  font-size: 21px;
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
  font-size: 17px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.todo-title-line span {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--app-primary-soft);
  color: #c2410c;
  font-size: 12px;
  font-weight: 800;
}

.todo-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 6px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.45;
}

.todo-meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  max-width: 100%;
  padding: 2px 0;
  overflow-wrap: anywhere;
}

.todo-description {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.todo-description span {
  max-width: 100%;
  padding: 5px 8px;
  border-radius: 999px;
  background: #fff7e8;
  color: #7c5c46;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.todo-actions {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.todo-actions button {
  height: 32px;
  min-width: 0;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: #fff;
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
  box-shadow: 0 8px 16px rgba(249, 115, 22, 0.18);
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
