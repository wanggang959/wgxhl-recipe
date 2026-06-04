<script setup>
defineProps({
  name: {
    type: String,
    required: true,
  },
  filled: {
    type: Boolean,
    default: false,
  },
  size: {
    type: [Number, String],
    default: 20,
  },
})
</script>

<template>
  <svg
    v-if="name === 'heart'"
    class="action-svg-icon action-svg-icon--heart"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78Z"
      :fill="filled ? 'currentColor' : 'none'"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
  <svg
    v-else-if="name === 'cart'"
    class="action-svg-icon action-svg-icon--cart"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <circle cx="8" cy="21" r="1.35" fill="currentColor" />
    <circle cx="19" cy="21" r="1.35" fill="currentColor" />
    <path
      d="M2.5 3h2.25l2.52 12.1a2 2 0 0 0 1.96 1.6h8.9a2 2 0 0 0 1.94-1.52l1.2-5.03H6.16"
      fill="none"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<style scoped>
.action-svg-icon {
  display: block;
  flex: 0 0 auto;
  overflow: visible;
  transform-origin: center;
}

.action-svg-icon--heart {
  transform: translateY(-0.5px);
}

.action-svg-icon--cart {
  transform: translate(-0.7px, -1.15px);
}
</style>
