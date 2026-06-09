<script setup>
import TodoCard from './TodoCard.vue'

defineProps({
  todo: {
    type: Object,
    required: true,
  },
  readonly: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['back', 'edit', 'complete', 'completeBlocked', 'delete'])
</script>

<template>
  <section class="detail-card">
    <header>
      <button type="button" @click="emit('back')"><van-icon name="arrow-left" /></button>
      <h1>待办详情</h1>
      <button v-if="!readonly" type="button" @click="emit('edit', todo)"><van-icon name="edit" /></button>
      <span v-else class="header-spacer" aria-hidden="true"></span>
    </header>
    <TodoCard
      :todo="todo"
      :readonly="readonly"
      @open="() => {}"
      @complete="emit('complete', todo)"
      @complete-blocked="emit('completeBlocked', todo)"
      @edit="emit('edit', todo)"
      @delete="emit('delete', todo)"
    />
    <div class="info-grid">
      <div><span>重复</span><strong>{{ todo.repeatType || 'NONE' }}</strong></div>
      <div><span>站内</span><strong>{{ todo.notifySite ? '开启' : '关闭' }}</strong></div>
      <div><span>邮箱</span><strong>{{ todo.notifyEmail ? '开启' : '关闭' }}</strong></div>
      <div><span>App</span><strong>{{ todo.notifyPush ? '开启' : '关闭' }}</strong></div>
    </div>
  </section>
</template>

<style scoped>
.detail-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

header {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 38px;
  align-items: center;
  gap: 8px;
}

header h1 {
  margin: 0;
  color: var(--app-text);
  text-align: center;
  font-size: 22px;
}

header button,
.header-spacer {
  width: 36px;
  height: 36px;
}

header button {
  border: 0;
  border-radius: 50%;
  background: #fff7e8;
  color: var(--app-primary);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.info-grid div {
  padding: 12px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid var(--app-border);
}

.info-grid span {
  display: block;
  color: var(--app-muted);
  font-size: 12px;
}

.info-grid strong {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-size: 15px;
}
</style>
