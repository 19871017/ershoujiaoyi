<template>
  <view class="page-shell ranking-page">
    <view class="hero ds-card">
      <view class="hero-glow" />
      <view class="hero-top">
        <view>
          <view class="kicker">♡ 圈内人气榜</view>
          <view class="page-title">男神女神榜</view>
          <view class="page-desc">按穿搭互动、安全提醒和守护助力综合计算，展示圈内高人气预览用户。</view>
        </view>
        <view class="crown">👑</view>
      </view>
      <view class="hero-stats">
        <view v-for="item in stats" :key="item.label" class="stat-card">
          <view class="stat-value">{{ item.value }}</view>
          <view class="stat-label">{{ item.label }}</view>
        </view>
      </view>
    </view>

    <view class="tabs ds-card">
      <view v-for="item in genderTabs" :key="item.value" class="tab tapable" :class="{ active: activeGender === item.value }" @click="activeGender = item.value">
        <text class="tab-icon">{{ item.icon }}</text>
        <text>{{ item.label }}</text>
      </view>
    </view>

    <view class="sub-tabs">
      <view v-for="item in rankTypes" :key="item.value" class="sub-tab tapable" :class="{ active: activeType === item.value }" @click="activeType = item.value">
        {{ item.label }}
      </view>
    </view>

    <view class="podium ds-card">
      <view class="podium-title-row">
        <view>
          <view class="section-title">{{ currentTitle }}</view>
          <view class="section-desc">{{ currentDesc }}</view>
        </view>
        <view class="rule-chip tapable" @click="showRule">规则</view>
      </view>
      <view class="podium-row">
        <view v-for="item in podiumList" :key="item.id" class="podium-item tapable" :class="[`rank-${item.rank}`, item.gender]" @click="openProfile(item)">
          <view class="rank-medal">{{ medalFor(item.rank) }}</view>
          <view class="podium-avatar">{{ item.avatar }}</view>
          <view class="podium-name">{{ item.name }}</view>
          <view class="podium-score">{{ scoreText(item) }}</view>
        </view>
      </view>
    </view>

    <view class="filter-card ds-card">
      <view class="filter-head">
        <view>
          <view class="section-title">完整榜单</view>
          <view class="section-desc">榜单为开发预览数据，实名、信用、成交等信任指标不在本页展示。</view>
        </view>
        <view class="city-chip">{{ cityFilter }}</view>
      </view>
      <view class="city-row">
        <view v-for="city in cities" :key="city" class="city tapable" :class="{ active: cityFilter === city }" @click="cityFilter = city">{{ city }}</view>
      </view>
    </view>

    <view class="rank-list">
      <view v-for="item in filteredRankings" :key="item.id" class="user-card ds-card">
        <view class="rank-no" :class="{ top: item.rank <= 3 }">{{ item.rank }}</view>
        <view class="avatar-wrap tapable" @click="openProfile(item)">
          <view class="avatar" :class="item.gender">{{ item.avatar }}</view>
          <view v-if="item.rank <= 3" class="avatar-badge">{{ medalFor(item.rank) }}</view>
        </view>
        <view class="user-main">
          <view class="name-row">
            <view class="user-name">{{ item.name }}</view>
            <view class="verify">预览用户</view>
          </view>
          <view class="user-desc">{{ item.bio }}</view>
          <view class="tag-row">
            <text v-for="tag in item.tags" :key="tag" class="tag">{{ tag }}</text>
          </view>
          <view class="metric-row">
            <text>人气 {{ item.popularity }}</text>
            <text>守护 {{ item.guardian }}</text>
            <text>预览数据</text>
          </view>
        </view>
        <view class="user-actions">
          <button class="follow-btn" @click="toggleFollow">关注</button>
          <button class="chat-btn" @click="chat(item)">私信</button>
        </view>
      </view>
    </view>

    <view class="safe-note ds-card">
      <view class="safe-icon">🛡️</view>
      <view>
        <view class="safe-title">榜单安全说明</view>
        <view class="safe-desc">榜单只展示开发预览互动热度，不展示静态实名、信用或成交指标；交易请以平台订单、支付和售后状态为准，拒绝私下转账。</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

type Gender = 'goddess' | 'god'
type RankType = 'popular' | 'deal' | 'guardian'
interface RankingUser {
  id: number
  rank: number
  gender: Gender
  avatar: string
  name: string
  bio: string
  city: string
  tags: string[]
  popularity: number
  guardian: number
}

const activeGender = ref<Gender>('goddess')
const activeType = ref<RankType>('popular')
const cityFilter = ref('全部')
const genderTabs = [
  { value: 'goddess' as const, label: '女神榜', icon: '👑' },
  { value: 'god' as const, label: '男神榜', icon: '✨' }
]
const rankTypes = [
  { value: 'popular' as const, label: '人气榜' },
  { value: 'deal' as const, label: '安全榜' },
  { value: 'guardian' as const, label: '守护榜' }
]
const cities = ['全部', '深圳', '广州', '杭州', '成都']
const rankings = reactive<RankingUser[]>([
  { id: 101, rank: 1, gender: 'goddess', avatar: '糖', name: '小原圈软糖', bio: '法式连衣裙、蝴蝶结长袜爱好者', city: '深圳', tags: ['穿搭达人', '预览用户'], popularity: 1280, guardian: 268 },
  { id: 102, rank: 2, gender: 'goddess', avatar: '梨', name: '梨涡裙摆', bio: '温柔通勤小原圈，细节图回复很快', city: '广州', tags: ['回复快', '成色真实'], popularity: 1160, guardian: 221 },
  { id: 103, rank: 3, gender: 'goddess', avatar: '莓', name: '莓莓袜铺', bio: '袜子、小包和原妹小物整理中', city: '杭州', tags: ['小用品', '包邮多', '好沟通'], popularity: 980, guardian: 196 },
  { id: 104, rank: 4, gender: 'goddess', avatar: '桃', name: '桃桃旧物', bio: '闲置不压箱底，喜欢就私信看细节', city: '成都', tags: ['连衣裙', '可议价'], popularity: 820, guardian: 142 },
  { id: 105, rank: 5, gender: 'goddess', avatar: '月', name: '月光鞋柜', bio: '玛丽珍鞋、通勤鞋慢慢出', city: '深圳', tags: ['鞋袜', '同城'], popularity: 760, guardian: 137 },
  { id: 201, rank: 1, gender: 'god', avatar: '温', name: '温柔收纳家', bio: '帮女友整理小原圈，交易沟通很细致', city: '深圳', tags: ['守护达人', '回复快'], popularity: 960, guardian: 310 },
  { id: 202, rank: 2, gender: 'god', avatar: '星', name: '星河寄件员', bio: '安全交易提醒，建议大家只看平台记录', city: '广州', tags: ['安全提醒', '预览用户'], popularity: 880, guardian: 284 },
  { id: 203, rank: 3, gender: 'god', avatar: '森', name: '森系买手', bio: '喜欢干净衣物和真实描述', city: '杭州', tags: ['买家沟通', '礼貌沟通'], popularity: 735, guardian: 210 },
  { id: 204, rank: 4, gender: 'god', avatar: '南', name: '南风同城', bio: '同城面交只约公共场所', city: '成都', tags: ['同城', '风控意识'], popularity: 620, guardian: 168 },
  { id: 205, rank: 5, gender: 'god', avatar: '白', name: '白茶收藏夹', bio: '收藏可爱小用品，也会分享避坑', city: '深圳', tags: ['圈内活跃', '避坑分享'], popularity: 588, guardian: 155 }
])
const stats = computed(() => [
  { value: `${rankings.filter((item) => item.gender === activeGender.value).length}`, label: '上榜用户' },
  { value: `${filteredRankings.value.reduce((sum, item) => sum + item.popularity, 0)}`, label: '本周热度' },
  { value: `${filteredRankings.value.reduce((sum, item) => sum + item.guardian, 0)}`, label: '守护热度' }
])
const currentTitle = computed(() => `${activeGender.value === 'goddess' ? '女神' : '男神'} · ${rankTypes.find((item) => item.value === activeType.value)?.label}`)
const currentDesc = computed(() => {
  if (activeType.value === 'popular') return '按点赞、关注、圈内互动综合排序'
  if (activeType.value === 'deal') return '按安全提醒、避坑互动和圈内帮助排序，不展示静态成交或信用分'
  return '按安全提醒、私信守护和圈内帮助排序'
})
const filteredRankings = computed(() => {
  const list = rankings.filter((item) => item.gender === activeGender.value && (cityFilter.value === '全部' || item.city === cityFilter.value))
  const key = activeType.value === 'popular' ? 'popularity' : 'guardian'
  return [...list].sort((a, b) => b[key] - a[key]).map((item, index) => ({ ...item, rank: index + 1 }))
})
const podiumList = computed(() => {
  const top = filteredRankings.value.slice(0, 3)
  return top.length === 3 ? [top[1]!, top[0]!, top[2]!] : top
})
function medalFor(rank: number) { return rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : `#${rank}` }
function scoreText(item: RankingUser) {
  if (activeType.value === 'popular') return `${item.popularity} 热度`
  if (activeType.value === 'deal') return `${item.guardian} 安全热度`
  return `${item.guardian} 守护`
}
function toggleFollow() { uni.showToast({ title: '关注接口暂未接通后端，未执行任何关注变更', icon: 'none' }) }
function chat(_item: RankingUser) { uni.showToast({ title: '榜单预览用户不能作为真实私信对象，未进入会话', icon: 'none' }) }
function openProfile(_item: RankingUser) { uni.showToast({ title: '榜单预览用户不能作为真实主页对象，未打开主页', icon: 'none' }) }
function showRule() { uni.showModal({ title: '榜单规则', content: '人气榜看互动热度；安全榜和守护榜只展示预览互动热度。实名、信用分、成交数必须由后端审计数据提供，当前不在本页展示。', showCancel: false }) }
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const tab = current?.options?.tab || hashParams?.get('tab') || ''
  if (tab === 'god' || tab === 'goddess') activeGender.value = tab
}
onMounted(readQuery)
</script>

<style scoped>
.ranking-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff7ed 100%); }
.hero { position:relative; overflow:hidden; padding:26rpx; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7 62%,#fff4e7); }
.hero-glow { position:absolute; right:-80rpx; top:-80rpx; width:230rpx; height:230rpx; border-radius:50%; background:rgba(255,122,69,.14); filter:blur(2rpx); }
.hero-top { position:relative; display:flex; justify-content:space-between; gap:20rpx; }
.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }
.crown { width:92rpx; height:92rpx; border-radius:32rpx; display:flex; align-items:center; justify-content:center; background:#fff; font-size:48rpx; box-shadow:0 12rpx 26rpx rgba(255,122,69,.16); }
.hero-stats { position:relative; margin-top:22rpx; display:grid; grid-template-columns:repeat(3, minmax(0,1fr)); gap:12rpx; }
.stat-card { padding:16rpx 10rpx; border-radius:24rpx; background:rgba(255,255,255,.78); text-align:center; border:1rpx solid rgba(255,217,189,.9); }
.stat-value { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.stat-label { margin-top:4rpx; color:#9b7560; font-size:18rpx; font-weight:800; }
.tabs { margin-top:20rpx; padding:8rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:8rpx; border-color:#ffd9bd; }
.tab { min-height:72rpx; border-radius:28rpx; display:flex; align-items:center; justify-content:center; gap:8rpx; color:#9b7560; font-size:25rpx; font-weight:950; }
.tab.active { background:#ff7a45; color:#fff; box-shadow:0 10rpx 20rpx rgba(255,122,69,.22); }
.tab-icon { font-size:30rpx; }
.sub-tabs { margin-top:18rpx; display:flex; gap:12rpx; overflow:hidden; }
.sub-tab { flex:1; min-height:62rpx; border-radius:999rpx; background:#fff3e7; color:#9b7560; display:flex; align-items:center; justify-content:center; font-size:22rpx; font-weight:900; }
.sub-tab.active { background:#3a2a1f; color:#fff; }
.podium,.filter-card,.safe-note { margin-top:20rpx; padding:22rpx; border-color:#ffd9bd; }
.podium-title-row,.filter-head { display:flex; justify-content:space-between; gap:16rpx; align-items:flex-start; }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.section-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; line-height:1.45; }
.rule-chip,.city-chip { flex:none; padding:10rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
.podium-row { margin-top:24rpx; display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12rpx; align-items:end; }
.podium-item { position:relative; min-height:190rpx; padding:18rpx 10rpx 16rpx; border-radius:34rpx; display:flex; flex-direction:column; align-items:center; justify-content:flex-end; border:1rpx solid #ffd9bd; background:linear-gradient(180deg,#fff,#fffaf6); }
.podium-item.rank-1 { min-height:226rpx; background:linear-gradient(180deg,#fff7d6,#fff3e7); }
.podium-item.god { background:linear-gradient(180deg,#f2edff,#eef7ff); }
.podium-item.goddess { background:linear-gradient(180deg,#fff3e7,#fffaf6); }
.rank-medal { position:absolute; top:12rpx; right:12rpx; font-size:28rpx; }
.podium-avatar { width:72rpx; height:72rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:30rpx; font-weight:950; box-shadow:0 10rpx 22rpx rgba(255,122,69,.18); }
.podium-item.god .podium-avatar { background:linear-gradient(135deg,#8b7cf6,#60a5fa); }
.podium-name { margin-top:12rpx; max-width:100%; color:#3a2a1f; font-size:21rpx; font-weight:950; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.podium-score { margin-top:5rpx; color:#9b7560; font-size:18rpx; font-weight:800; }
.city-row { margin-top:16rpx; display:flex; gap:10rpx; overflow:hidden; }
.city { flex:none; padding:10rpx 18rpx; border-radius:999rpx; background:#fffaf6; color:#9b7560; font-size:21rpx; font-weight:900; border:1rpx solid #ffd9bd; }
.city.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.rank-list { margin-top:16rpx; display:flex; flex-direction:column; gap:14rpx; }
.user-card { padding:18rpx; display:flex; align-items:center; gap:14rpx; border-color:#ffd9bd; }
.rank-no { width:42rpx; color:#b9856a; font-size:24rpx; font-weight:950; text-align:center; }
.rank-no.top { color:#ff7a45; }
.avatar-wrap { position:relative; flex:none; }
.avatar { width:76rpx; height:76rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; color:#fff; font-size:30rpx; font-weight:950; background:linear-gradient(135deg,#ff7a45,#ffb08a); }
.avatar.god { background:linear-gradient(135deg,#8b7cf6,#60a5fa); }
.avatar-badge { position:absolute; right:-8rpx; bottom:-8rpx; width:34rpx; height:34rpx; border-radius:50%; background:#fff; display:flex; align-items:center; justify-content:center; font-size:18rpx; border:1rpx solid #ffd9bd; }
.user-main { flex:1; min-width:0; }
.name-row { display:flex; align-items:center; gap:8rpx; min-width:0; }
.user-name { color:#3a2a1f; font-size:26rpx; font-weight:950; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.verify { flex:none; padding:4rpx 9rpx; border-radius:999rpx; background:#f0fdf4; color:#15803d; font-size:17rpx; font-weight:900; }
.user-desc { margin-top:6rpx; color:#7b5542; font-size:21rpx; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.tag-row { margin-top:8rpx; display:flex; gap:6rpx; flex-wrap:wrap; max-height:38rpx; overflow:hidden; }
.tag { padding:4rpx 8rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:17rpx; font-weight:850; }
.metric-row { margin-top:8rpx; display:flex; gap:12rpx; color:#b9856a; font-size:18rpx; font-weight:800; }
.user-actions { flex:none; display:flex; flex-direction:column; gap:8rpx; }
.follow-btn,.chat-btn { width:82rpx; height:42rpx; line-height:42rpx; border-radius:999rpx; font-size:19rpx; font-weight:900; }
.follow-btn { background:#ff7a45; color:#fff; }
.follow-btn.active { background:#fff3e7; color:#ff7a45; }
.chat-btn { background:#fff; color:#7b5542; border:1rpx solid #ffd9bd; }
.safe-note { display:flex; gap:16rpx; align-items:flex-start; background:linear-gradient(135deg,#fff,#fffaf6); }
.safe-icon { width:58rpx; height:58rpx; border-radius:22rpx; display:flex; align-items:center; justify-content:center; background:#fff3e7; font-size:30rpx; }
.safe-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.safe-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; line-height:1.55; }
</style>
