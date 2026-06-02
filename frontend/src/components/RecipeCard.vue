<script setup>
import { computed } from 'vue'
import { getRecipeImage } from '../utils/imageUrl'

const props = defineProps({
  recipe: {
    type: Object,
    required: true,
  },
  favorite: {
    type: Boolean,
    default: false,
  },
  showActions: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['open', 'favorite', 'edit', 'delete'])

const cover = computed(() => getRecipeImage(props.recipe.coverImage))
const taste = computed(() => props.recipe.taste || '家常')
const category = computed(() => props.recipe.categoryName || '未分类')
const cookingTime = computed(() => props.recipe.cookingTime || '未记录')
const difficulty = computed(() => props.recipe.difficulty || '普通')
</script>

<template>
  <article class="recipe-card" @click="emit('open', recipe)">
    <div class="cover-wrap">
      <img class="cover" :src="cover" :alt="recipe.recipeName" loading="lazy" />
      <button class="favorite" type="button" @click.stop="emit('favorite', recipe)">
        <van-icon :name="favorite ? 'like' : 'like-o'" :color="favorite ? '#ef4444' : '#ffffff'" size="20" />
      </button>
    </div>
    <div class="body">
      <div class="title-row">
        <h3>{{ recipe.recipeName }}</h3>
        <span>{{ cookingTime }}</span>
      </div>
      <div class="tags">
        <span class="tag primary">{{ category }}</span>
        <span class="tag">{{ taste }}</span>
        <span class="tag">{{ difficulty }}</span>
      </div>
      <div v-if="showActions" class="actions" @click.stop>
        <van-button size="mini" plain type="warning" @click="emit('edit', recipe)">编辑</van-button>
        <van-button size="mini" plain type="danger" @click="emit('delete', recipe)">删除</van-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.recipe-card {
  overflow: hidden;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(245, 223, 199, 0.9);
  box-shadow: 0 12px 28px rgba(154, 52, 18, 0.1);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--app-primary-soft);
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.favorite {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: rgba(47, 38, 31, 0.34);
  display: grid;
  place-items: center;
  backdrop-filter: blur(10px);
}

.body {
  padding: 12px;
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

h3 {
  margin: 0;
  min-width: 0;
  font-size: 17px;
  line-height: 1.35;
  color: var(--app-text);
  overflow-wrap: anywhere;
}

.title-row span {
  flex: 0 0 auto;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
}

.tags {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f8efe6;
  color: #7c5c46;
  font-size: 12px;
  line-height: 1;
}

.tag.primary {
  background: var(--app-primary-soft);
  color: #c2410c;
}

.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
