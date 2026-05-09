const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/message/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  'const feeds = reactive([',
  "name: '小原圈软糖'",
  "name: '平台审核'",
  "name: '同城约看小助手'",
  "name: '买家小陈'",
  '奶油白连衣裙搭浅色长袜真的很温柔',
  '3 条圈内互动',
  "likes: 128",
  "comments: 18",
  "followed: true"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static community feed marker found: ${marker}`)
}

const requiredMarkers = [
  "import { listCommunityPosts, type CommunityPostResponse } from '../../../api/modules/community'",
  'const feeds = ref<CommunityPostResponse[]>([])',
  'await listCommunityPosts(20)',
  '社区内容暂时不可用，未展示本地帖子样例',
  '点赞接口暂未接通后端，未执行任何点赞变更',
  '关注接口暂未接通后端，未执行任何关注变更',
  'function openPost(item: CommunityPostResponse)',
  'postId=${item.postId}'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing real-data/fail-closed community feed marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community tab feed uses backend posts and avoids static interaction/trust samples')
