const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const sourcePath = path.join(root, 'src/pages/ranking/index.vue')
const source = fs.readFileSync(sourcePath, 'utf8')
const failures = []

if (!source.includes('item.popularityScore')) {
  failures.push('ranking page must render popularity from backend popularityScore, not locally inferred followerCount')
}
if (!source.includes('item.safetyScore')) {
  failures.push('ranking page must render safety from backend safetyScore, not followerCount substitution')
}
if (!source.includes('item.guardianScore')) {
  failures.push('ranking page must render guardian from backend guardianScore, not followerCount substitution')
}
if (/guardian:\s*item\.followerCount/.test(source) || /popularity:\s*item\.followerCount/.test(source)) {
  failures.push('ranking page must not map multiple ranking metrics to followerCount')
}
if (source.includes("{ value: 'deal' as const, label: '安全榜' }") && !source.includes('safetyScore')) {
  failures.push('safety leaderboard tab must have an explicit backend metric field or stay unavailable')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}
console.log('ranking backend metric guard passed')
