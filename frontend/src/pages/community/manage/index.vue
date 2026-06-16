<template>
  <view class="page-shell manage-page">
    <view class="manage-hero">
      <view>
        <view class="kicker">社区管理</view>
        <view class="page-title">我的帖子</view>
        <view class="page-desc">编辑自己已发布的动态，或删除后从公开社区隐藏；后台仍保留追溯记录。</view>
      </view>
      <view class="hero-action tapable" @click="openComposer">＋</view>
    </view>

    <view class="manage-toolbar">
      <view class="toolbar-copy">{{ listSummary }}</view>
      <view class="toolbar-action tapable" :class="{ loading }" @click="loadMyPosts">{{ loading ? '读取中' : '刷新' }}</view>
    </view>

    <view v-if="loadError" class="state-line error">
      <view>{{ loadError }}</view>
      <view class="state-action tapable" @click="loadMyPosts">重试</view>
    </view>
    <view v-else-if="loading" class="state-line">正在读取我的社区动态</view>

    <view v-for="item in posts" :key="item.postId" class="post-row">
      <view class="post-main tapable" @click="openPost(item)">
        <view class="post-top">
          <view class="post-topic">{{ item.topic }}</view>
          <view class="post-status" :class="{ deleted: item.status !== 'PUBLISHED' }">{{ statusText(item.status) }}</view>
        </view>
        <view class="post-title">{{ item.title }}</view>
        <view class="post-content">{{ item.content }}</view>
        <view class="post-meta">{{ formatTime(item.createdAt) }} · {{ item.likeCount }} 赞 · {{ item.commentCount }} 评论</view>
      </view>
      <view class="post-actions">
        <view class="post-action tapable" :class="{ disabled: item.status !== 'PUBLISHED' }" @click.stop="editPost(item)">编辑</view>
        <view class="post-action danger tapable" :class="{ disabled: deletingPostId === item.postId || item.status !== 'PUBLISHED' }" @click.stop="askDeletePost(item)">
          {{ deletingPostId === item.postId ? '删除中' : '删除' }}
        </view>
      </view>
    </view>

    <view v-if="!loading && !loadError && !posts.length" class="empty-panel">
      <view class="empty-title">还没有发过动态</view>
      <view class="empty-copy">发一条真实社区内容，后续可以在这里修改或删除。</view>
      <view class="empty-action tapable" @click="openComposer">发布动态</view>
    </view>

    <view v-if="deleteDialog.visible" class="delete-overlay">
      <view class="delete-panel" @click.stop>
        <view class="delete-mark">!</view>
        <view class="delete-title">删除这条动态？</view>
        <view class="delete-copy">删除后不会再出现在社区公开列表，但后台仍会保留追溯记录。</view>
        <view class="delete-post-title">{{ deleteDialog.title }}</view>
        <view class="delete-actions">
          <button class="delete-btn ghost" @click="closeDeleteDialog">先保留</button>
          <button class="delete-btn danger" :disabled="deletingPostId !== null" @click="confirmDeletePost">确认删除</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { deleteCommunityPost, listMyCommunityPosts, type CommunityPostResponse } from '../../../api/modules/community'

const posts = ref<CommunityPostResponse[]>([])
const loading = ref(false)
const loadError = ref('')
const deletingPostId = ref<number | null>(null)
const deleteDialog = ref<{ visible: boolean; postId: number; title: string }>({ visible: false, postId: 0, title: '' })
const listSummary = computed(() => {
  if (loading.value) return '正在同步平台记录'
  if (!posts.value.length) return '暂无动态'
  const visibleCount = posts.value.filter((item) => item.status === 'PUBLISHED').length
  return `${posts.value.length} 条记录 · ${visibleCount} 条公开`
})

onMounted(() => {
  void loadMyPosts()
})

async function loadMyPosts(): Promise<void> {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    const rows = await listMyCommunityPosts(50)
    posts.value = validMyPostList(rows)
  } catch (error) {
    console.warn('community manage my posts load failed', error)
    posts.value = []
    loadError.value = error instanceof Error ? error.message : '我的帖子加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function validMyPostList(value: unknown): CommunityPostResponse[] {
  if (!Array.isArray(value)) throw new Error('我的帖子响应异常')
  return value.filter((item): item is CommunityPostResponse => {
    if (!item || typeof item !== 'object') return false
    const post = item as CommunityPostResponse
    return Number.isSafeInteger(post.postId) &&
      post.postId > 0 &&
      typeof post.title === 'string' &&
      typeof post.topic === 'string' &&
      typeof post.content === 'string' &&
      typeof post.status === 'string'
  })
}

function openComposer(): void {
  uni.navigateTo({ url: '/pages/community/compose/index' })
}

function openPost(item: CommunityPostResponse): void {
  if (item.status !== 'PUBLISHED') {
    uni.showToast({ title: '已删除动态不再公开展示', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/community/detail/index?postId=${encodeURIComponent(String(item.postId))}` })
}

function editPost(item: CommunityPostResponse): void {
  if (item.status !== 'PUBLISHED') {
    uni.showToast({ title: '这条动态当前不可编辑', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/community/compose/index?mode=edit&postId=${encodeURIComponent(String(item.postId))}` })
}

function askDeletePost(item: CommunityPostResponse): void {
  if (item.status !== 'PUBLISHED' || deletingPostId.value) return
  deleteDialog.value = { visible: true, postId: item.postId, title: item.title }
}

function closeDeleteDialog(): void {
  if (deletingPostId.value) return
  deleteDialog.value = { visible: false, postId: 0, title: '' }
}

async function confirmDeletePost(): Promise<void> {
  const postId = deleteDialog.value.postId
  if (!postId || deletingPostId.value) return
  deletingPostId.value = postId
  try {
    const deleted = await deleteCommunityPost(postId)
    posts.value = posts.value.map((item) => item.postId === postId ? { ...item, status: deleted.status } : item)
    deleteDialog.value = { visible: false, postId: 0, title: '' }
    uni.showToast({ title: '动态已删除', icon: 'none' })
  } catch (error) {
    console.warn('community manage delete post failed', { postId, error })
    uni.showToast({ title: error instanceof Error ? error.message : '删除失败，请稍后重试', icon: 'none' })
  } finally {
    deletingPostId.value = null
  }
}

function statusText(status: string): string {
  if (status === 'PUBLISHED') return '公开'
  if (status === 'DELETED') return '已删除'
  if (status === 'BLOCKED') return '已屏蔽'
  return status || '未知'
}

function formatTime(value: string): string {
  return value ? value.slice(0, 16).replace('T', ' ') : '刚刚'
}
</script>

<style scoped lang="scss" src="./style.scss"></style>
