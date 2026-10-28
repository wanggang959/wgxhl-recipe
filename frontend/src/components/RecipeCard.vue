<script setup>
import { computed } from 'vue'
import ActionIcon from './ActionIcon.vue'
import { getRecipeImage } from '../utils/imageUrl'
import { formatRecipeVersionLabel, normalizeRecipeVersion } from '../utils/recipeVersion'

const props = defineProps({
  recipe: {
    type: Object,
    required: true,
  },
  favorite: {
    type: Boolean,
    default: false,
  },
  wanted: {
    type: Boolean,
    default: false,
  },
  showWantButton: {
    type: Boolean,
    default: true,
  },
  showFavoriteButton: {
    type: Boolean,
    default: true,
  },
  showActions: {
    type: Boolean,
    default: false,
  },
  liked: {
    type: Boolean,
    default: false,
  },
  likeCount: {
    type: Number,
    default: 0,
  },
  showLikeButton: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['open', 'favorite', 'want', 'edit', 'delete', 'like'])

const cover = computed(() => getRecipeImage(props.recipe.coverImage))
const taste = computed(() => props.recipe.taste || '家常')
const category = computed(() => props.recipe.categoryName || '未分类')
const cookingTime = computed(() => props.recipe.cookingTime || '未记录')
const difficulty = computed(() => props.recipe.difficulty || '普通')
const ownerName = computed(() => props.recipe.ownerName || '')
const versionLabel = computed(() => formatRecipeVersionLabel(normalizeRecipeVersion(props.recipe.recipeVersion)))
</script>

<template>
  <article class="recipe-card" @click="emit('open', recipe)">
    <div class="cover-wrap">
      <img class="cover" :src="cover" :alt="recipe.recipeName" loading="lazy" />
      <span v-if="recipe.status === '下架'" class="offline-badge">已下架</span>
      <button
        v-if="showFavoriteButton"
        class="favorite"
        :class="{ active: favorite, 'has-count': recipe.favoriteCount > 0 }"
        type="button"
        @click.stop="emit('favorite', recipe)"
      >
        <ActionIcon name="heart" :filled="favorite" :size="20" />
        <span v-if="recipe.favoriteCount > 0">{{ recipe.favoriteCount }}</span>
      </button>
      <span v-else-if="recipe.favoriteCount > 0" class="favorite has-count">
        <ActionIcon name="heart" :size="20" />
        <span>{{ recipe.favoriteCount }}</span>
      </span>
      <button
        v-if="showWantButton"
        class="want-cart"
        :class="{ active: wanted }"
        type="button"
        @click.stop="emit('want', recipe)"
      >
        <ActionIcon name="cart" :size="18" />
      </button>
      <button
        v-if="showLikeButton"
        class="like-pill"
        :class="{ active: liked, 'is-icon-only': !liked && likeCount === 0 }"
        type="button"
        @click.stop="emit('like', recipe)"
      >
        <van-icon :name="liked ? 'good-job' : 'good-job-o'" size="14" />
        <span v-if="liked || likeCount > 0">{{ likeCount }}</span>
      </button>
      <span v-else-if="likeCount > 0" class="like-pill is-static">
        <van-icon :name="liked ? 'good-job' : 'good-job-o'" size="14" />
        <span>{{ likeCount }}</span>
      </span>
    </div>
    <div class="body">
      <div class="title-row">
        <h3>{{ recipe.recipeName }}</h3>
        <span>{{ cookingTime }}</span>
      </div>
      <div class="tags-row">
        <span class="tag primary">{{ category }}</span>
        <span class="tag">{{ taste }}</span>
        <span class="tag">{{ difficulty }}</span>
        <div class="tags-meta">
          <span v-if="ownerName" class="tag owner">{{ ownerName }}</span>
          <span class="tag version">{{ versionLabel }}</span>
        </div>
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

.offline-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.72);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
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
  box-sizing: border-box;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(47, 38, 31, 0.34);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  overflow: hidden;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  appearance: none;
  -webkit-appearance: none;
  z-index: 2;
}

.favorite.active {
  color: #ef4444;
}

.favorite.has-count {
  width: auto;
  min-width: 34px;
  padding: 0 9px;
  gap: 4px;
  border-radius: 17px;
  font-size: 12px;
  font-weight: 600;
}

.want-cart {
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 34px;
  height: 34px;
  box-sizing: border-box;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(47, 38, 31, 0.34);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  overflow: hidden;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  appearance: none;
  -webkit-appearance: none;
  z-index: 2;
}

.favorite :deep(.action-svg-icon),
.want-cart :deep(.action-svg-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.want-cart.active {
  background: rgba(255, 247, 237, 0.92);
  color: #ea580c;
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

.tags-row {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.tags-meta {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.tag {
  box-sizing: border-box;
  max-width: 100%;
  padding: 4px 8px;
  border: 1px solid transparent;
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

.tag.owner {
  background: #fff4e8;
  color: #9a3412;
  border: 1px solid rgba(249, 115, 22, 0.22);
}

.tag.like {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: #fff1e6;
  color: #c2410c;
}

.like-pill {
  position: absolute;
  left: 10px;
  bottom: 10px;
  height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.55);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  backdrop-filter: blur(6px);
}

.like-pill.active {
  background: #fff;
  color: #ea580c;
}

.like-pill.is-icon-only {
  width: 28px;
  padding: 0;
  justify-content: center;
}

.like-pill.is-static {
  pointer-events: none;
}

.tag.version {
  background: #f3f4f6;
  color: #4b5563;
  border: 1px solid rgba(107, 114, 128, 0.18);
}

.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
