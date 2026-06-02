<script setup>
import { ref, onMounted } from 'vue'
import { showToast } from 'vant'
import { checkHealth } from '../api/health'

const status = ref('检查中...')
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await checkHealth()
    status.value = res.data.status
    showToast('后端连接成功')
  } catch (e) {
    status.value = '连接失败'
    showToast(e.message || '后端连接失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <van-nav-bar title="wgxhl-recipe" fixed placeholder />
    <div class="content">
      <van-cell-group inset title="开发环境">
        <van-cell title="前端" value="Vue3 + Vite + Vant" />
        <van-cell title="后端" value="Spring Boot + MyBatis Plus" />
        <van-cell title="数据库" value="MySQL 8.4" />
        <van-cell title="图片存储" value="腾讯云 COS" />
      </van-cell-group>
      <van-cell-group inset title="服务状态">
        <van-cell title="后端健康检查">
          <template #value>
            <van-loading v-if="loading" size="16" />
            <span v-else>{{ status }}</span>
          </template>
        </van-cell>
      </van-cell-group>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.content {
  padding: 12px 0 24px;
}
</style>
