<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { completeTodo, deleteTodo, getTodoDetail } from '../api/todo'
import TodoDetail from '../components/TodoDetail.vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const todo = ref(null)

onMounted(loadTodo)

async function loadTodo() {
  loading.value = true
  try {
    const res = await getTodoDetail(route.params.id)
    todo.value = res.data
  } catch (error) {
    showFailToast(error.message || '待办加载失败')
  } finally {
    loading.value = false
  }
}

async function finish(item) {
  try {
    if (item.canComplete === false) {
      showFailToast(item.completeDisabledReason || '暂时不能完成')
      return
    }
    if (item.category === 'BIRTHDAY') {
      await showConfirmDialog({
        title: '完成生日提醒',
        message: `确认完成「${item.title}」吗？生日提醒只能当天完成。`,
      })
      await completeTodo(route.params.id)
      closeToast()
      showSuccessToast('已完成')
      await loadTodo()
      return
    }
    await showConfirmDialog({
      title: '进入下一个周期',
      message: `确认处理「${item.title}」吗？接下来需要填写下一个到期时间。`,
    })
    router.push(`/todo/${route.params.id}/edit?nextCycle=1`)
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

function completeBlocked(item) {
  showFailToast(item.completeDisabledReason || '暂时不能完成')
}

async function remove() {
  try {
    await showConfirmDialog({
      title: '删除待办',
      message: `确认删除「${todo.value?.title || ''}」吗？`,
    })
    await deleteTodo(route.params.id)
    showSuccessToast('已删除')
    router.replace('/todo')
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}
</script>

<template>
  <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
  <TodoDetail
    v-else-if="todo"
    :todo="todo"
    :readonly="userStore.isGuest"
    @back="router.back()"
    @edit="router.push(`/todo/${route.params.id}/edit`)"
    @complete="finish"
    @complete-blocked="completeBlocked"
    @delete="remove"
  />
</template>

<style scoped>
.loading {
  margin-top: 40px;
}
</style>
