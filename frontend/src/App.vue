<template>
  <view />
</template>

<style lang="scss">
:root {
  --c-bg: #fff7ed;
  --c-surface: #ffffff;
  --c-surface-soft: #fff3e7;
  --c-text: #3a2a1f;
  --c-muted: #9b7560;
  --c-line: #ffd9bd;
  --c-primary: #ff7a45;
  --c-primary-dark: #e85d2a;
  --c-danger: #ef4444;
  --c-success: #10b981;
  --c-warn: #f59e0b;
  --c-peach: #ffc08a;
  --c-lavender: #b99cff;
  --c-ring: #ffb36b;
  --radius-sm: 18rpx;
  --radius-md: 28rpx;
  --radius-lg: 38rpx;
  --shadow-card: 0 14rpx 34rpx rgba(255, 122, 69, .14);
}

page {
  min-height: 100%;
  background: var(--c-bg);
  color: var(--c-text);
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
}

view, text, button, input, textarea { box-sizing: border-box; }
button { margin: 0; padding: 0; border: 0; background: none; line-height: 1.2; }

.page-shell {
  min-height: 100vh;
  padding: 24rpx 24rpx calc(150rpx + env(safe-area-inset-bottom));
  background: var(--c-bg);
}

.page-title {
  font-size: 42rpx;
  line-height: 1.2;
  font-weight: 900;
  color: var(--c-text);
}

.page-desc {
  margin-top: 10rpx;
  color: var(--c-muted);
  font-size: 25rpx;
  line-height: 1.55;
}

.ds-card {
  background: var(--c-surface);
  border: 1rpx solid var(--c-line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.tapable { cursor: pointer; transition: transform .15s ease, opacity .15s ease; }
.tapable:active { transform: scale(.98); opacity: .82; }

.primary-btn {
  min-height: 88rpx;
  padding: 0 30rpx;
  border-radius: 999rpx;
  background: var(--c-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 900;
}

.secondary-btn {
  min-height: 76rpx;
  padding: 0 26rpx;
  border-radius: 999rpx;
  background: var(--c-surface-soft);
  color: var(--c-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 800;
}

/* 紧凑版个性导航：只加轻量 logo/颜色，不接管 uni-app 原生 fixed 定位，避免导航栏消失。 */
uni-tabbar .uni-tabbar {
  z-index: 999 !important;
  min-height: calc(66rpx + env(safe-area-inset-bottom)) !important;
  padding: 2rpx 10rpx calc(4rpx + env(safe-area-inset-bottom)) !important;
  background: rgba(255, 251, 246, .98) !important;
  border-top: 1rpx solid rgba(255, 217, 189, .88) !important;
  box-shadow: 0 -8rpx 22rpx rgba(255, 122, 69, .11) !important;
  backdrop-filter: blur(14rpx);
}

uni-tabbar .uni-tabbar__item,
uni-tabbar .uni-tabbar__label,
uni-tabbar .uni-tabbar__bd,
uni-tabbar .uni-tabbar__icon {
  transition: transform .14s ease, color .14s ease, opacity .14s ease, background .14s ease;
}

uni-tabbar .uni-tabbar__item {
  position: relative;
  border-radius: 20rpx;
}

uni-tabbar .uni-tabbar__item .uni-tabbar__bd {
  gap: 2rpx !important;
}

uni-tabbar .uni-tabbar__label {
  font-size: 18rpx !important;
  font-weight: 900 !important;
  line-height: 1 !important;
}

uni-tabbar .uni-tabbar__label::before {
  display: block;
  margin: 0 auto 1rpx;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  line-height: 28rpx;
  text-align: center;
  font-size: 18rpx;
  background: #fff3e7;
  color: #ff7a45;
  box-shadow: inset 0 0 0 2rpx rgba(255,122,69,.08);
}

uni-tabbar .uni-tabbar__item:nth-of-type(2) .uni-tabbar__label::before { content: '🏠'; }
uni-tabbar .uni-tabbar__item:nth-of-type(3) .uni-tabbar__label::before { content: '🎀'; }
uni-tabbar .uni-tabbar__item:nth-of-type(4) .uni-tabbar__label::before { content: '+'; background: linear-gradient(135deg,#ff7a45,#ffc08a); color: #fff; box-shadow: 0 8rpx 18rpx rgba(255,122,69,.24); }
uni-tabbar .uni-tabbar__item:nth-of-type(5) .uni-tabbar__label::before { content: '💬'; }
uni-tabbar .uni-tabbar__item:nth-of-type(6) .uni-tabbar__label::before { content: '👤'; }

uni-tabbar .uni-tabbar__item:active {
  transform: translateY(-2rpx) scale(.97);
}
</style>
