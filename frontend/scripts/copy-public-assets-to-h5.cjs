const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const source = path.join(root, 'public/assets')
const target = path.join(root, 'dist/build/h5/assets')

if (!fs.existsSync(source)) {
  console.log('no public/assets directory to copy')
  process.exit(0)
}

if (!fs.existsSync(path.join(root, 'dist/build/h5/index.html'))) {
  console.error('H5 dist is missing. Run uni build -p h5 before copying public assets.')
  process.exit(1)
}

fs.cpSync(source, target, { recursive: true, force: true })
console.log(`copied public/assets to ${path.relative(root, target)}`)
