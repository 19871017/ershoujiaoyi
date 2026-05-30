<template>
  <view class="page-shell ranking-page">
    <view class="hero ds-card" :class="heroThemeClass">
      <view class="hero-artwork" :style="{ backgroundImage: heroArtwork }" />
      <view class="hero-overlay" />
      <view class="hero-glow" />
      <view class="hero-top">
        <view class="hero-title">{{ pageTitle }}</view>
        <view class="hero-badge" :class="heroBadgeClass">
          <view class="hero-badge-text">{{ heroBadgeText }}</view>
        </view>
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
          <view class="rank-label">TOP {{ item.rank }}</view>
          <view class="podium-avatar" :class="{ image: !!item.avatarUrl }">
            <image v-if="item.avatarUrl" class="ranking-avatar-image" :src="item.avatarUrl" mode="aspectFill" />
            <text v-else>{{ item.avatar }}</text>
          </view>
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

    <view v-if="loading" class="empty ds-card">
      <view class="section-title">榜单加载中...</view>
    </view>

    <view v-else-if="loadError" class="empty ds-card">
      <view class="section-title">榜单暂时不可用</view>
      <view class="section-desc">{{ loadError }}</view>
    </view>

    <view v-else class="rank-list">
      <view v-for="item in filteredRankings" :key="item.id" class="user-card ds-card">
        <view class="rank-no" :class="{ top: item.rank <= 3 }">{{ item.rank }}</view>
        <view class="avatar-wrap tapable" @click="openProfile(item)">
          <view class="avatar" :class="[item.gender, { image: !!item.avatarUrl }]">
            <image v-if="item.avatarUrl" class="ranking-avatar-image" :src="item.avatarUrl" mode="aspectFill" />
            <text v-else>{{ item.avatar }}</text>
          </view>
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
import { listUserRankings } from '../../api/modules/ranking'
import { followPublicProfile, unfollowPublicProfile } from '../../api/modules/user'
import rankingGoddessCard from '../../assets/ranking/ranking-goddess-card.png'
import rankingGodCard from '../../assets/ranking/ranking-god-card.png'
import { isRankingPeriod, isRankingTab, periodTabs, toRankingUser, type Gender, type Period, type RankingUser } from './ranking-data'

const activeGender = ref<Gender>('goddess')
const activePeriod = ref<Period>('week')
const loading = ref(false)
const loadError = ref('')
const rankings = ref<RankingUser[]>([])
const followingIds = ref<Set<number>>(new Set())

const isGoddess = computed(() => activeGender.value === 'goddess')
const pageTitle = computed(() => isGoddess.value ? '女神榜' : '男神榜')
const heroThemeClass = computed(() => isGoddess.value ? 'hero-goddess' : 'hero-god')
const heroBadgeClass = computed(() => isGoddess.value ? 'hero-badge-goddess' : 'hero-badge-god')
const heroBadgeText = computed(() => isGoddess.value ? '魅力焦点' : '锋芒焦点')
const heroArtwork = computed(() => `url(${isGoddess.value ? rankingGoddessCard : rankingGodCard})`)
const currentPeriodLabel = computed(() => periodTabs.find((item) => item.value === activePeriod.value)?.label ?? '周榜')
const rankMetricLabel = computed(() => isGoddess.value ? '魅力值' : '实力值')
const metricChip = computed(() => isGoddess.value ? '按魅力值排行' : '按实力值排行')
const currentTitle = computed(() => `${currentPeriodLabel.value}${pageTitle.value} TOP 3`)
const stats = computed(() => [
  { value: `${filteredRankings.value.length}`, label: '上榜人数' },
  { value: `${totalGiftScore.value}`, label: isGoddess.value ? '魅力总分' : '实力总分' },
  { value: currentPeriodLabel.value, label: '当前榜单' }
])
const totalGiftScore = computed(() => filteredRankings.value.reduce((sum, item) => sum + item.giftScore, 0))
const filteredRankings = computed(() => [...rankings.value]
  .sort((a, b) => b.giftScore - a.giftScore || a.id - b.id)
  .slice(0, 100)
  .map((item, index) => ({ ...item, rank: index + 1 })))
const podiumList = computed(() => {
  const top = filteredRankings.value.slice(0, 3)
  return top.length === 3 ? [top[1]!, top[0]!, top[2]!] : top
})

function scoreText(item: RankingUser) {
  return `${isGoddess.value ? '魅力值' : '实力值'} ${item.giftScore}`
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
  loading.value = true
  loadError.value = ''
  try {
    const rows = await listUserRankings(activeGender.value, activePeriod.value, 100)
    const users = rows.map(toRankingUser)
    rankings.value = users
    if (users.length === 0) {
      loadError.value = isGoddess.value ? '当前榜单还没有女神魅力值数据' : '当前榜单还没有男神实力值数据'
    }
  } catch {
    rankings.value = []
    loadError.value = '榜单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const tab = current?.options?.tab || hashParams?.get('tab') || ''
  const period = current?.options?.period || hashParams?.get('period') || ''
  if (isRankingTab(tab)) activeGender.value = tab
  if (isRankingPeriod(period)) activePeriod.value = period
}

onMounted(() => {
  readQuery()
  loadRankings()
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
