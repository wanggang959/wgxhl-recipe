<script setup>
import RecipeForm from '../components/RecipeForm.vue'
</script>

<template>
  <div class="app-shell editor-shell">
    <main class="page-wrap">
      <RecipeForm />
    </main>
  </div>
</template>

<style scoped>
.editor-shell {
  min-height: 100vh;
}
</style>
