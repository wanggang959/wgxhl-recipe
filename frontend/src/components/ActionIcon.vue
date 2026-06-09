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
  <svg
    v-else-if="name === 'home'"
    class="action-svg-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="M3.5 11.2 12 4l8.5 7.2v8.2a1.6 1.6 0 0 1-1.6 1.6h-4.2v-6.1H9.3V21H5.1a1.6 1.6 0 0 1-1.6-1.6v-8.2Z"
      fill="none"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
  <svg
    v-else-if="name === 'fire'"
    class="action-svg-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="M12.6 21c3.8-.4 6.4-3 6.4-6.7 0-2.6-1.2-4.8-3.5-6.7-.3 1.8-1.1 3-2.2 3.7.2-3.1-1.3-5.8-4.4-8.1.1 3.6-1.2 5.4-2.6 7.2-1 1.3-1.8 2.5-1.8 4.1 0 3.8 3 6.5 6.8 6.5"
      fill="none"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M12 21c1.6-.4 2.5-1.5 2.5-3 0-1.2-.7-2.1-1.8-3.1-.2 1-.7 1.7-1.5 2.1 0-1.5-.7-2.7-2-3.8 0 2-.8 3-1.4 4-.3.5-.5 1-.5 1.5 0 1.4 1.2 2.3 2.7 2.3"
      fill="currentColor"
      opacity="0.2"
    />
  </svg>
  <svg
    v-else-if="name === 'star'"
    class="action-svg-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="m12 3.6 2.55 5.17 5.7.83-4.12 4.02.97 5.68L12 16.62 6.9 19.3l.97-5.68L3.75 9.6l5.7-.83L12 3.6Z"
      fill="none"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linejoin="round"
    />
  </svg>
  <svg
    v-else-if="name === 'todo'"
    class="action-svg-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <rect x="5" y="3.8" width="14" height="16.4" rx="2.2" fill="none" stroke="currentColor" stroke-width="1.9" />
    <path d="M9 8.4h6M9 12h6M9 15.6h4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" />
  </svg>
  <svg
    v-else-if="name === 'user'"
    class="action-svg-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <circle cx="12" cy="8.5" r="3.6" fill="none" stroke="currentColor" stroke-width="1.9" />
    <path
      d="M5.4 20.2c.8-3.3 3.1-5.1 6.6-5.1s5.8 1.8 6.6 5.1"
      fill="none"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
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
