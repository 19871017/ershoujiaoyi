<template>
  <view class="page-shell ranking-page">
    <view class="hero ds-card" :class="heroThemeClass">
      <view class="hero-artwork" :style="{ backgroundImage: heroArtwork }" />
      <view class="hero-overlay" />
      <view class="hero-glow" />
      <view class="hero-top">
        <view>
          <view class="hero-title">{{ pageTitle }}</view>
        </view>
        <view class="crown">{{ activeGender === 'goddess' ? '👑' : '💎' }}</view>
      </view>
      <view class="hero-stats">
        <view v-for="item in stats" :key="item.label" class="stat-card">
          <view class="stat-value">{{ item.value }}</view>
          <view class="stat-label">{{ item.label }}</view>
        </view>
      </view>
    </view>

    <view class="period-tabs ds-card">
      <view
        v-for="item in periodTabs"
        :key="item.value"
        class="period-tab tapable"
        :class="{ active: activePeriod === item.value }"
        @click="switchPeriod(item.value)"
      >
        {{ item.label }}
      </view>
    </view>

    <view class="podium ds-card">
      <view class="podium-title-row">
        <view class="section-title">{{ currentTitle }}</view>
        <view class="city-chip">{{ currentPeriodLabel }}</view>
      </view>
      <view class="podium-row">
        <view
          v-for="item in podiumList"
          :key="item.id"
          class="podium-item tapable"
          :class="[`rank-${item.rank}`, item.gender]"
          @click="openProfile(item)"
        >
          <view class="rank-medal">{{ medalFor(item.rank) }}</view>
          <view class="podium-avatar">{{ item.avatar }}</view>
          <view class="podium-name">{{ item.name }}</view>
          <view class="podium-score">{{ scoreText(item) }}</view>
        </view>
      </view>
    </view>

    <view class="filter-card ds-card">
      <view class="filter-head">
        <view class="section-title">{{ pageTitle }} Top 100</view>
        <view class="city-chip">{{ metricChip }}</view>
      </view>
    </view>

    <view v-if="loadError" class="empty ds-card">
      <view class="empty-icon">📊</view>
      <view class="section-title">榜单暂时不可用</view>
      <view class="section-desc">{{ loadError }}</view>
    </view>

    <view v-else class="rank-list">
      <view v-for="item in filteredRankings" :key="item.id" class="user-card ds-card">
        <view class="rank-no" :class="{ top: item.rank <= 3 }">{{ item.rank }}</view>
        <view class="avatar-wrap tapable" @click="openProfile(item)">
          <view class="avatar" :class="item.gender">{{ item.avatar }}</view>
          <view v-if="item.rank <= 3" class="avatar-badge">{{ medalFor(item.rank) }}</view>
        </view>
        <view class="user-main">
          <view class="name-row">
            <view class="user-name">{{ item.name }}</view>
          </view>
          <view class="metric-row">
            <text>{{ rankMetricLabel }} {{ item.giftScore }}</text>
          </view>
        </view>
        <view class="user-actions">
          <button class="follow-btn" :disabled="followingIds.has(item.id)" @click="toggleFollow(item)">{{ followingIds.has(item.id) ? '处理中' : (item.viewerFollows ? '已关注' : '关注') }}</button>
          <button class="chat-btn" @click="chat(item)">私信</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listUserRankings, type RankingGender, type RankingPeriod, type UserRankingResponse } from '../../api/modules/ranking'
import { followPublicProfile, unfollowPublicProfile } from '../../api/modules/user'
import rankingGoddessCard from '../../assets/ranking/ranking-goddess-card.png'
import rankingGodCard from '../../assets/ranking/ranking-god-card.png'

type Gender = RankingGender
type Period = RankingPeriod

interface RankingUser {
  id: number
  rank: number
  gender: Gender
  avatar: string
  name: string
  bio: string
  city: string
  giftScore: number
  viewerFollows: boolean
}

const activeGender = ref<Gender>('goddess')
const activePeriod = ref<Period>('week')
const loadError = ref('')
const rankings = ref<RankingUser[]>([])
const followingIds = ref<Set<number>>(new Set())
const periodTabs = [
  { value: 'day' as const, label: '日榜' },
  { value: 'week' as const, label: '周榜' },
  { value: 'all' as const, label: '总榜' }
]

const isGoddess = computed(() => activeGender.value === 'goddess')
const pageTitle = computed(() => isGoddess.value ? '女神榜' : '男神榜')
const heroThemeClass = computed(() => isGoddess.value ? 'hero-goddess' : 'hero-god')
const heroArtwork = computed(() => `url(${isGoddess.value ? rankingGoddessCard : rankingGodCard})`)
const currentPeriodLabel = computed(() => periodTabs.find((item) => item.value === activePeriod.value)?.label ?? '周榜')
const rankMetricLabel = computed(() => isGoddess.value ? '收到礼物' : '消费金额')
const metricChip = computed(() => isGoddess.value ? '按收礼排行' : '按消费排行')
const currentTitle = computed(() => `${currentPeriodLabel.value}${pageTitle.value} TOP 3`)
const stats = computed(() => [
  { value: `${filteredRankings.value.length}`, label: '上榜人数' },
  { value: `${totalGiftScore.value}`, label: isGoddess.value ? '收礼总额' : '消费总额' },
  { value: currentPeriodLabel.value, label: '当前榜单' }
])
const totalGiftScore = computed(() => filteredRankings.value.reduce((sum, item) => sum + item.giftScore, 0))
const filteredRankings = computed(() => rankings.value
  .sort((a, b) => b.giftScore - a.giftScore || a.id - b.id)
  .slice(0, 100)
  .map((item, index) => ({ ...item, rank: index + 1 })))
const podiumList = computed(() => {
  const top = filteredRankings.value.slice(0, 3)
  return top.length === 3 ? [top[1]!, top[0]!, top[2]!] : top
})

function medalFor(rank: number) {
  return rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : `#${rank}`
}

function scoreText(item: RankingUser) {
  return isGoddess.value ? `收礼 ${item.giftScore}` : `消费 ${item.giftScore}`
}

function toRankingUser(item: UserRankingResponse): RankingUser {
  return {
    id: item.userId,
    rank: item.rank,
    gender: item.gender === 'god' ? 'god' : 'goddess',
    avatar: (item.nickname || '圈').slice(0, 1),
    name: item.nickname || '平台用户',
    bio: item.bio || '这个用户还没有填写个人介绍',
    city: item.city || '全部',
    giftScore: item.giftScore ?? item.popularityScore,
    viewerFollows: item.followedByMe
  }
}

function switchPeriod(period: Period) {
  if (activePeriod.value === period) return
  activePeriod.value = period
  loadRankings()
}

async function toggleFollow(item: RankingUser) {
  if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少平台用户编号，无法提交关注请求', icon: 'none' })
  if (followingIds.value.has(item.id)) return
  followingIds.value = new Set([...followingIds.value, item.id])
  try {
    const updated = item.viewerFollows ? await unfollowPublicProfile(item.id) : await followPublicProfile(item.id)
    rankings.value = rankings.value.map((row) => row.id === item.id ? { ...row, viewerFollows: !!updated.followedByMe } : row)
    uni.showToast({ title: updated.followedByMe ? '已关注' : '已取消关注', icon: 'none' })
  } catch {
    uni.showToast({ title: '关注状态未更新，请稍后重试', icon: 'none' })
  } finally {
    const next = new Set(followingIds.value)
    next.delete(item.id)
    followingIds.value = next
  }
}

function chat(item: RankingUser) {
  if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少平台用户编号，未进入会话', icon: 'none' })
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${item.id}` })
}

function openProfile(item: RankingUser) {
  if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少平台用户编号，未打开主页', icon: 'none' })
  uni.navigateTo({ url: `/pages/user/public-profile/index?userId=${item.id}` })
}

async function loadRankings() {
  try {
    loadError.value = ''
    const rows = await listUserRankings(activeGender.value, activePeriod.value, 100)
    rankings.value = rows.map(toRankingUser)
    if (rankings.value.length === 0) {
      loadError.value = isGoddess.value ? '当前榜单还没有女神收礼数据' : '当前榜单还没有男神消费数据'
    }
  } catch {
    rankings.value = []
    loadError.value = '榜单加载失败，请稍后重试'
  }
}

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const tab = current?.options?.tab || hashParams?.get('tab') || ''
  const period = current?.options?.period || hashParams?.get('period') || ''
  if (tab === 'god' || tab === 'goddess') activeGender.value = tab
  if (period === 'day' || period === 'week' || period === 'all') activePeriod.value = period
}

onMounted(() => {
  readQuery()
  loadRankings()
})
</script>

<style scoped>
.ranking-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff7ed 100%); }
.hero { position:relative; overflow:hidden; padding:26rpx; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7 62%,#fff4e7); }
.hero-goddess { background:linear-gradient(135deg,#fff7fb 0%,#fff0f4 58%,#fff7ed 100%); }
.hero-god { background:linear-gradient(135deg,#f7f9ff 0%,#eef4ff 58%,#f8f5ff 100%); }
.hero-artwork { position:absolute; inset:0; background-repeat:no-repeat; background-size:cover; background-position:center top; transform:scale(1.02); opacity:.98; }
.hero-overlay { position:absolute; inset:0; background:linear-gradient(90deg,rgba(255,252,249,.96) 0%,rgba(255,249,245,.8) 36%,rgba(255,244,236,.34) 62%,rgba(255,243,235,.08) 100%); }
.hero-goddess .hero-overlay { background:linear-gradient(90deg,rgba(255,250,252,.97) 0%,rgba(255,245,248,.84) 38%,rgba(255,236,242,.34) 64%,rgba(255,241,236,.1) 100%); }
.hero-god .hero-overlay { background:linear-gradient(90deg,rgba(248,250,255,.97) 0%,rgba(241,245,255,.84) 38%,rgba(232,240,255,.34) 64%,rgba(237,243,255,.1) 100%); }
.hero-glow { position:absolute; right:-80rpx; top:-80rpx; width:230rpx; height:230rpx; border-radius:50%; background:rgba(255,122,69,.14); filter:blur(2rpx); }
.hero-top { position:relative; z-index:1; display:flex; justify-content:space-between; gap:20rpx; }
.hero-title { color:#3a2a1f; font-size:40rpx; font-weight:950; }
.crown { width:92rpx; height:92rpx; border-radius:32rpx; display:flex; align-items:center; justify-content:center; background:#fff; font-size:48rpx; box-shadow:0 12rpx 26rpx rgba(255,122,69,.16); }
.hero-stats { position:relative; z-index:1; margin-top:22rpx; display:grid; grid-template-columns:repeat(3, minmax(0,1fr)); gap:12rpx; }
.stat-card { padding:16rpx 10rpx; border-radius:24rpx; background:rgba(255,255,255,.82); text-align:center; border:1rpx solid rgba(255,217,189,.9); }
.stat-value { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.stat-label { margin-top:4rpx; color:#9b7560; font-size:18rpx; font-weight:800; }
.period-tabs { margin-top:20rpx; padding:8rpx; display:grid; grid-template-columns:repeat(3,1fr); gap:10rpx; border-color:#ffd9bd; }
.period-tab { min-height:70rpx; border-radius:24rpx; display:flex; align-items:center; justify-content:center; color:#9b7560; font-size:24rpx; font-weight:900; background:#fffaf6; }
.period-tab.active { background:#ff7a45; color:#fff; box-shadow:0 10rpx 20rpx rgba(255,122,69,.22); }
.podium,.filter-card { margin-top:20rpx; padding:22rpx; border-color:#ffd9bd; }
.podium-title-row,.filter-head { display:flex; justify-content:space-between; gap:16rpx; align-items:flex-start; }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.section-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; line-height:1.45; }
.city-chip { flex:none; padding:10rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
.podium-row { margin-top:24rpx; display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12rpx; align-items:end; }
.podium-item { position:relative; min-height:190rpx; padding:18rpx 10rpx 16rpx; border-radius:34rpx; display:flex; flex-direction:column; align-items:center; justify-content:flex-end; border:1rpx solid #ffd9bd; background:linear-gradient(180deg,#fff,#fffaf6); }
.podium-item.rank-1 { min-height:226rpx; background:linear-gradient(180deg,#fff7d6,#fff3e7); }
.podium-item.god { background:linear-gradient(180deg,#eef4ff,#f6f8ff); }
.podium-item.goddess { background:linear-gradient(180deg,#fff3f7,#fffaf6); }
.rank-medal { position:absolute; top:12rpx; right:12rpx; font-size:28rpx; }
.podium-avatar { width:72rpx; height:72rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:30rpx; font-weight:950; box-shadow:0 10rpx 22rpx rgba(255,122,69,.18); }
.podium-item.god .podium-avatar { background:linear-gradient(135deg,#8b7cf6,#60a5fa); }
.podium-name { margin-top:12rpx; max-width:100%; color:#3a2a1f; font-size:21rpx; font-weight:950; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.podium-score { margin-top:5rpx; color:#9b7560; font-size:18rpx; font-weight:800; }
.empty { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; text-align:center; }
.empty-icon { font-size:52rpx; }
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
.metric-row { margin-top:8rpx; display:flex; gap:12rpx; color:#b9856a; font-size:18rpx; font-weight:800; }
.user-actions { flex:none; display:flex; flex-direction:column; gap:8rpx; }
.follow-btn,.chat-btn { width:82rpx; height:42rpx; line-height:42rpx; border-radius:999rpx; font-size:19rpx; font-weight:900; }
.follow-btn { background:#ff7a45; color:#fff; }
.chat-btn { background:#fff; color:#7b5542; border:1rpx solid #ffd9bd; }
</style>
