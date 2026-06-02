<script setup>
const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: '',
  },
  categories: {
    type: Array,
    default: () => [],
  },
  presets: {
    type: Array,
    default: () => ['全部', '家常菜', '汤羹', '凉菜', '早餐', '主食', '小吃'],
  },
})

const emit = defineEmits(['update:modelValue', 'change'])

function normalizedItems() {
  const source = props.categories.length
    ? [{ id: '', categoryName: '全部' }, ...props.categories]
    : props.presets.map((name) => ({ id: name === '全部' ? '' : name, categoryName: name }))

  return source.slice(0, 12)
}

function choose(value) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <div class="category-tabs" aria-label="菜谱分类">
    <button
      v-for="item in normalizedItems()"
      :key="item.id || item.categoryName"
      type="button"
      class="tab"
      :class="{ active: modelValue === item.id }"
      @click="choose(item.id)"
    >
      {{ item.categoryName }}
    </button>
  </div>
</template>

<style scoped>
.category-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px 2px 8px;
  scrollbar-width: none;
}

.category-tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  flex: 0 0 auto;
  min-width: 54px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  color: #7c5c46;
  font-size: 13px;
  font-weight: 600;
}

.tab.active {
  border-color: rgba(249, 115, 22, 0.28);
  background: var(--app-primary-soft);
  color: #c2410c;
}
</style>
