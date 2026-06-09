<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { listMembers } from '../api/user'
import { createTodo, getTodoDetail, updateTodo } from '../api/todo'
import TodoForm from '../components/TodoForm.vue'
import { markTodoRefreshRequired } from '../utils/todoRefresh'

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const members = ref([])
const todo = ref({})
const isEdit = computed(() => Boolean(route.params.id))
const isNextCycle = computed(() => route.query.nextCycle === '1')

onMounted(async () => {
  await Promise.all([loadMembers(), loadTodo()])
})

async function loadMembers() {
  try {
    const res = await listMembers()
    members.value = res.data || []
  } catch (error) {
    members.value = []
  }
}

async function loadTodo() {
  if (!isEdit.value) {
    const query = route.query || {}
    todo.value = {
      title: query.title || '',
      category: query.category || 'OTHER',
      relatedType: query.relatedType || '',
      relatedId: query.relatedId || '',
      dueTime: query.dueTime || '',
      notifySite: true,
      notifyEmail: false,
    }
    return
  }
  try {
    const res = await getTodoDetail(route.params.id)
    const detail = res.data || {}
    todo.value = isNextCycle.value && detail.category !== 'BIRTHDAY'
      ? { ...detail, dueTime: '' }
      : detail
  } catch (error) {
    showFailToast(error.message || '待办加载失败')
  }
}

async function submit(payload) {
  if (isNextCycle.value && payload.category !== 'BIRTHDAY' && !payload.dueTime) {
    showFailToast('请填写下一个到期时间')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateTodo(payload)
      markTodoRefreshRequired()
      showSuccessToast(isNextCycle.value ? '已进入下一个周期' : '已保存')
      router.replace(`/todo/${route.params.id}`)
    } else {
      const res = await createTodo(payload)
      markTodoRefreshRequired()
      showSuccessToast('已创建')
      router.replace(`/todo/${res.data.id}`)
    }
  } catch (error) {
    showFailToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="editor-page">
    <header>
      <button type="button" @click="router.back()"><van-icon name="arrow-left" /></button>
      <div>
        <p>家庭待办</p>
        <h1>{{ isNextCycle ? '下一个周期' : (isEdit ? '编辑待办' : '新增待办') }}</h1>
      </div>
    </header>
    <div v-if="isNextCycle" class="cycle-tip">
      请填写下一个到期时间，保存后这条待办会继续进入新的提醒周期。
    </div>
    <div class="form-card">
      <TodoForm :model-value="todo" :members="members" :saving="saving" @submit="submit" @cancel="router.back()" />
    </div>
  </section>
</template>

<style scoped>
.editor-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

header {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

header button {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: #fff7e8;
  color: var(--app-primary);
}

header p,
header h1 {
  margin: 0;
}

header p {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 900;
}

header h1 {
  color: var(--app-text);
  font-size: 24px;
}

.cycle-tip {
  padding: 11px 12px;
  border-radius: 16px;
  border: 1px solid #fed7aa;
  background: #fff7e8;
  color: #8a4b20;
  font-size: 13px;
  line-height: 1.45;
}

.form-card {
  padding: 14px;
  border-radius: 20px;
  background: #fffaf2;
  border: 1px solid var(--app-border);
}
</style>
