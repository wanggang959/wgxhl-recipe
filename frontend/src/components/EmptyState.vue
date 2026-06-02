<script setup>
defineProps({
  text: {
    type: String,
    default: '还没有菜谱，先添加一道拿手菜吧',
  },
  buttonText: {
    type: String,
    default: '添加菜谱',
  },
  showButton: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['action'])
</script>

<template>
  <div class="empty-state">
    <div class="plate">
      <van-icon name="shop-o" size="34" />
    </div>
    <p>{{ text }}</p>
    <van-button v-if="showButton" round type="warning" size="small" @click="emit('action')">
      {{ buttonText }}
    </van-button>
  </div>
</template>

<style scoped>
.empty-state {
  min-height: 220px;
  padding: 36px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px dashed #f3c89b;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--app-muted);
  text-align: center;
}

.plate {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

p {
  margin: 0;
  font-size: 15px;
}
</style>
