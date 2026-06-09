<script setup>
import TodoCard from './TodoCard.vue'

defineProps({
  todo: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['open'])
</script>

<template>
  <TodoCard :todo="todo" compact @open="emit('open', todo)" />
</template>
