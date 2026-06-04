<script setup>
import { computed } from 'vue'
import ActionIcon from './ActionIcon.vue'
import { getImageUrl, getRecipeImage } from '../utils/imageUrl'
import { formatRecipeVersionLabel, normalizeRecipeVersion } from '../utils/recipeVersion'

const props = defineProps({
  detail: {
    type: Object,
    required: true,
  },
  favorite: {
    type: Boolean,
    default: false,
  },
  canEdit: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['back', 'favorite', 'edit'])

const recipe = computed(() => props.detail.recipe || {})
const cover = computed(() => getRecipeImage(recipe.value.coverImage))
const ingredients = computed(() => props.detail.ingredientList || [])
const seasonings = computed(() => props.detail.seasoningList || [])
const steps = computed(() => props.detail.recipeStepList || [])
const tips = computed(() => recipe.value.remark || recipe.value.recipeDesc || '')
</script>

<template>
  <article class="recipe-detail">
    <div class="hero-image">
      <img :src="cover" :alt="recipe.recipeName" />
      <div class="hero-actions">
        <button type="button" @click="emit('back')">
          <van-icon name="arrow-left" size="20" />
        </button>
        <button class="favorite-action" :class="{ active: favorite }" type="button" @click="emit('favorite')">
          <ActionIcon name="heart" :filled="favorite" :size="21" />
        </button>
      </div>
      <div class="hero-meta-tags">
        <span v-if="recipe.ownerName" class="detail-tag owner">{{ recipe.ownerName }}</span>
        <span class="detail-tag version">{{ formatRecipeVersionLabel(normalizeRecipeVersion(recipe.recipeVersion)) }}</span>
      </div>
    </div>

    <section class="summary">
      <div class="title-line">
        <h1>{{ recipe.recipeName }}</h1>
        <van-button v-if="canEdit" round size="small" type="warning" icon="edit" @click="emit('edit')">
          编辑
        </van-button>
      </div>
      <p v-if="recipe.recipeDesc">{{ recipe.recipeDesc }}</p>
      <div class="meta-grid">
        <div>
          <span>用时</span>
          <strong>{{ recipe.cookingTime || '-' }}</strong>
        </div>
        <div>
          <span>难度</span>
          <strong>{{ recipe.difficulty || '普通' }}</strong>
        </div>
        <div>
          <span>口味</span>
          <strong>{{ recipe.taste || '家常' }}</strong>
        </div>
        <div>
          <span>分类</span>
          <strong>{{ recipe.categoryName || '未分类' }}</strong>
        </div>
      </div>
    </section>

    <section class="section">
      <h2>食材</h2>
      <div v-if="ingredients.length === 0" class="simple-empty">暂无食材</div>
      <div v-else class="ingredient-list">
        <div v-for="item in ingredients" :key="item.id || item.ingredientId" class="ingredient-item">
          <img
            v-if="item.ingredientImage"
            :src="getImageUrl(item.ingredientImage)"
            :alt="item.ingredientName"
          />
          <div v-else class="ingredient-placeholder">
            <van-icon name="photo-o" />
          </div>
          <div>
            <strong>{{ item.ingredientName }}</strong>
            <span>{{ item.amount || '-' }} {{ item.unit || '' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="section">
      <h2>调料</h2>
      <div v-if="seasonings.length === 0" class="simple-empty">暂无调料</div>
      <div v-else class="ingredient-list">
        <div v-for="item in seasonings" :key="item.id || item.seasoningId" class="ingredient-item">
          <img
            v-if="item.seasoningImage"
            :src="getImageUrl(item.seasoningImage)"
            :alt="item.seasoningName"
          />
          <div v-else class="ingredient-placeholder">
            <van-icon name="photo-o" />
          </div>
          <div>
            <strong>{{ item.seasoningName }}</strong>
            <span>{{ item.amount || '-' }} {{ item.unit || '' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="section">
      <h2>制作步骤</h2>
      <van-empty v-if="steps.length === 0" description="暂无步骤" />
      <div v-else class="step-list">
        <article v-for="item in steps" :key="item.id || item.stepNo" class="step-card">
          <div class="step-no">{{ item.stepNo }}</div>
          <div class="step-content">
            <h3>{{ item.stepTitle || `步骤 ${item.stepNo}` }}</h3>
            <p>{{ item.stepDesc || '暂无说明' }}</p>
            <img
              v-if="item.stepImage"
              :src="getImageUrl(item.stepImage)"
              :alt="item.stepTitle || `步骤 ${item.stepNo}`"
            />
          </div>
        </article>
      </div>
    </section>

    <section v-if="tips" class="tips">
      <h2>小贴士</h2>
      <p>{{ tips }}</p>
    </section>
  </article>
</template>

<style scoped>
.recipe-detail {
  padding-bottom: 18px;
}

.hero-image {
  position: relative;
  margin: -10px -12px 0;
  aspect-ratio: 1 / 0.82;
  max-height: 430px;
  overflow: hidden;
  background: var(--app-primary-soft);
}

.hero-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hero-actions {
  position: absolute;
  top: calc(12px + var(--safe-area-top));
  left: 12px;
  right: 12px;
  display: flex;
  justify-content: space-between;
}

.hero-actions button {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  background: rgba(47, 38, 31, 0.38);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  overflow: hidden;
  backdrop-filter: blur(10px);
}

.favorite-action.active {
  color: #ef4444;
}

.hero-actions button :deep(.action-svg-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.hero-meta-tags {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  max-width: 58%;
}

.summary {
  position: relative;
  margin-top: -28px;
  padding: 18px 14px 14px;
  border-radius: 22px 22px 0 0;
  background: var(--app-surface);
}

.title-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

h1 {
  margin: 0;
  min-width: 0;
  color: var(--app-text);
  font-size: 26px;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.summary p {
  margin: 8px 0 0;
  color: var(--app-muted);
  font-size: 14px;
  line-height: 1.6;
}

.detail-tag {
  box-sizing: border-box;
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #f8efe6;
  color: #7c5c46;
  font-size: 12px;
  line-height: 1;
}

.detail-tag.owner {
  background: #fff4e8;
  color: #9a3412;
  border: 1px solid rgba(249, 115, 22, 0.22);
}

.detail-tag.version {
  background: #f3f4f6;
  color: #4b5563;
  border: 1px solid rgba(107, 114, 128, 0.18);
}

.meta-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.meta-grid div {
  min-width: 0;
  padding: 10px 6px;
  border-radius: 14px;
  background: #fff;
  text-align: center;
  border: 1px solid var(--app-border);
}

.meta-grid span {
  display: block;
  color: var(--app-muted);
  font-size: 11px;
}

.meta-grid strong {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.section,
.tips {
  margin-top: 12px;
  padding: 0 2px;
}

.simple-empty {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.48);
  color: #b79b83;
  font-size: 13px;
  line-height: 1;
}

h2 {
  margin: 0 0 10px;
  color: var(--app-text);
  font-size: 18px;
}

.ingredient-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ingredient-item {
  min-width: 0;
  padding: 8px;
  border-radius: 15px;
  background: #fff;
  border: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: 46px 1fr;
  align-items: center;
  gap: 9px;
}

.ingredient-item img,
.ingredient-placeholder {
  width: 46px;
  height: 46px;
  border-radius: 12px;
}

.ingredient-item img {
  object-fit: cover;
}

.ingredient-placeholder {
  background: var(--app-primary-soft);
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

.ingredient-item strong,
.ingredient-item span {
  display: block;
  min-width: 0;
  overflow-wrap: anywhere;
}

.ingredient-item strong {
  color: var(--app-text);
  font-size: 14px;
}

.ingredient-item span {
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 12px;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-card {
  padding: 12px;
  border-radius: 17px;
  background: #fff;
  border: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 10px;
}

.step-no {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--app-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.step-content {
  min-width: 0;
}

.step-content h3 {
  margin: 4px 0 0;
  font-size: 15px;
  color: var(--app-text);
}

.step-content p {
  margin: 7px 0 0;
  color: #6f5a49;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.step-content img {
  width: 100%;
  margin-top: 10px;
  border-radius: 14px;
  object-fit: cover;
}

.tips {
  padding: 14px;
  border-radius: 17px;
  background: #fff7e8;
  border: 1px solid #fed7aa;
}

.tips p {
  margin: 0;
  color: #7c5c46;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
