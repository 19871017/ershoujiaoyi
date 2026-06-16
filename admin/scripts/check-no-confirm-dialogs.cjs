const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const scanRoots = ['src']
const failures = []

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full)
      continue
    }
    if (!/\.(vue|ts|js)$/.test(entry.name)) continue
    const rel = path.relative(root, full)
    const source = fs.readFileSync(full, 'utf8')
    const lines = source.split(/\r?\n/)
    lines.forEach((line, index) => {
      if (/\bwindow\.confirm\s*\(/.test(line) || /\bconfirm\s*\(/.test(line)) {
        failures.push(`${rel}:${index + 1}: 后台不再使用二次确认弹窗: ${line.trim()}`)
      }
    })
  }
}

for (const scanRoot of scanRoots) {
  walk(path.join(root, scanRoot))
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('admin no confirm dialog guard passed')
