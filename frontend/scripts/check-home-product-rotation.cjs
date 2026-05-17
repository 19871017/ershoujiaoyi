const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const homeFile = path.join(root, 'src/pages/tabbar/home/index.vue')
const source = fs.readFileSync(homeFile, 'utf8')

const failures = []

const requiredMarkers = [
  'scroll-y',
  ':scroll-top="productScrollTop"',
  '@scroll="handleProductScroll"',
  '@touchstart="handleUserInteract"',
  '@touchmove="handleUserInteract"',
  '@touchend="handleUserInteractEnd"',
  '@touchcancel="handleUserInteractEnd"',
  'const isUserInteracting = ref(false)',
  'const isTouchingProducts = ref(false)',
  'const lastManualScrollAt = ref(0)',
  'function scheduleResumeRoll()',
  'function handleUserInteract()',
  'function handleUserInteractEnd()',
  'function handleProductScroll',
  'if (!shouldRollProducts.value || isUserInteracting.value || isTouchingProducts.value) return',
  'Date.now() - lastManualScrollAt.value < 6000',
  'productScrollTop.value = nextTop >= distance ? 0 : nextTop',
  'onBeforeUnmount(() => {'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`missing homepage product rotation marker: ${marker}`)
}

if (source.includes('animation-play-state') && !source.includes('isUserInteracting')) {
  failures.push('css animation product rotation must pause while the user is interacting')
}

if (source.includes('setInterval(() => {') && !source.includes('if (autoScrolling) return')) {
  failures.push('auto scroll updates must not be treated as manual scroll input')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('home product rotation check passed')
