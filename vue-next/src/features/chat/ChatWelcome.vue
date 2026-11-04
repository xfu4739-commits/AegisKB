<template>
  <section class="chat-next-welcome" aria-labelledby="welcome-heading">
    <div class="welcome-heading-row">
      <img class="welcome-logo" src="/aegis-mark.svg" alt="" width="40" height="40" />
      <h1 id="welcome-heading">欢迎回来，随时开始吧</h1>
    </div>
  </section>
</template>

<style scoped>
.chat-next-welcome { width: 100%; max-width: 780px; margin: 0 auto; padding: 0 0 24px; }
.welcome-heading-row { display: flex; align-items: center; justify-content: center; gap: 12px; }
.welcome-logo { display: block; flex-shrink: 0; }
h1 { min-width: 0; margin: 0; color: var(--text); font-size: calc(var(--text-heading) * 1.125); font-weight: 650; line-height: 1.35; letter-spacing: -0.6px; text-wrap: balance; }
@media (max-width: 600px) {
  .chat-next-welcome { padding-bottom: 20px; }
  .welcome-heading-row { gap: 10px; }
  .welcome-logo { width: 32px; height: 32px; }
  h1 { font-size: var(--text-heading); letter-spacing: -0.5px; }
}
</style>
