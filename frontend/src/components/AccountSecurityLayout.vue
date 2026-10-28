<script setup>
defineProps({ title: { type: String, required: true }, back: { type: String, default: '/login' } })
</script>

<template>
  <main class="security-page">
    <header class="security-header">
      <router-link :to="back" class="security-back" aria-label="返回" title="返回"><van-icon name="arrow-left" /></router-link>
      <span>家庭生活助手</span>
      <img src="/pwa-icon-192.png" alt="" width="32" height="32" />
    </header>
    <section class="security-content">
      <div class="security-heading"><van-icon name="shield-o" /><h1>{{ title }}</h1></div>
      <slot />
    </section>
  </main>
</template>

<style>
.security-page { width: 100%; max-width: 430px; min-height: 100dvh; margin: 0 auto; background: #fff9f2; color: #342b24; padding-bottom: max(24px, env(safe-area-inset-bottom)); }
.security-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 68px; padding: calc(12px + var(--safe-area-top, 0px)) 20px 12px; border-bottom: 1px solid #f4dfcc; background: #fffdf9; font-weight: 600; }
.security-header img { border-radius: 8px; object-fit: cover; }
.security-back { display: grid; place-items: center; width: 32px; height: 36px; color: #b2612c; }
.security-content { padding: 28px 20px; }
.security-heading { display: flex; align-items: center; gap: 10px; margin-bottom: 24px; }
.security-heading > .van-icon { color: #f97713; font-size: 24px; }
.security-heading h1 { margin: 0; font-size: 23px; line-height: 1.4; }
.security-form { display: flex; flex-direction: column; gap: 20px; padding: 22px 18px; background: white; border: 1px solid #f3ddc8; border-radius: 8px; }
.security-field { display: flex; flex-direction: column; gap: 8px; font-size: 14px; font-weight: 500; }
.security-field input { box-sizing: border-box; min-width: 0; width: 100%; height: 46px; border: 1px solid #eacfb9; border-radius: 8px; padding: 0 12px; background: #fffdfb; font: inherit; color: #342b24; }
.security-field input:focus { outline: 2px solid #ffa755; outline-offset: 1px; }
.security-field input:disabled { color: #796d62; background: #f8f5f1; }
.security-page .security-primary { display: flex; align-items: center; justify-content: center; gap: 8px; width: 100%; min-height: 46px; border: 0; border-radius: 8px; background: #ff7913; color: #fff; padding: 10px 14px; font: inherit; font-weight: 600; cursor: pointer; }
.security-primary:disabled { opacity: .55; cursor: default; }
.security-page .security-secondary { display: block; margin: 20px auto 0; border: none; background: none; color: #a75b28; font: inherit; font-size: 14px; text-align: center; cursor: pointer; }
.security-note { margin: 0; font-size: 13px; line-height: 1.8; color: #817161; overflow-wrap: anywhere; }
.security-error { margin: 0; padding: 10px 12px; border-radius: 6px; background: #fff0ee; color: #b42332; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.security-result { display: flex; flex-direction: column; gap: 16px; padding: 24px 0; }
.security-result > .van-icon { color: #3e8a69; font-size: 40px; }
.security-result h2 { margin: 0; font-size: 18px; }
.security-email-status { margin-bottom: 22px; border-bottom: 1px solid #f0ddcb; padding-bottom: 18px; }
.security-email-status strong { display: block; margin: 6px 0; font-size: 16px; overflow-wrap: anywhere; }
</style>
