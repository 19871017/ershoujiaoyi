<template>
  <section class="page-shell community-trace-page">
    <div class="page-title">社区追溯</div>
    <div class="page-desc">按帖子编号、作者 ID 或关键词追溯社区帖子、评论、作者和关联商品。</div>

    <form class="lookup-card community-filter" @submit.prevent="loadPosts">
      <label>
        <span>帖子 ID/编号</span>
        <input v-model.trim="postId" :disabled="loadingList || loadingDetail" placeholder="例如 12 或 POST-1-1770000000000" />
      </label>
      <label>
        <span>作者 ID</span>
        <input v-model.trim="authorId" :disabled="loadingList || loadingDetail" placeholder="发帖用户 ID" />
      </label>
      <label>
        <span>关键词</span>
        <input v-model.trim="keyword" :disabled="loadingList || loadingDetail" maxlength="64" placeholder="帖子编号/标题/话题/昵称" />
      </label>
      <label>
        <span>条数</span>
        <input v-model.number="limit" type="number" min="1" max="100" step="1" :disabled="loadingList || loadingDetail" />
      </label>
      <button class="primary-btn" :disabled="loadingList || loadingDetail">{{ loadingList ? '查询中...' : '查询帖子' }}</button>
      <button class="secondary-btn" type="button" :disabled="loadingList || loadingDetail || !postId" @click="loadDetailFromInput">查询详情</button>
    </form>

    <div v-if="error" class="alert">{{ error }}</div>

    <div class="community-grid">
      <aside class="community-list">
        <div class="section-head">
          <strong>帖子列表</strong>
          <span>{{ posts.length }} 条</span>
        </div>
        <div v-if="loadingList" class="empty">社区帖子加载中...</div>
        <div v-else-if="posts.length === 0" class="empty">暂无可追溯帖子。</div>
        <button
          v-for="item in posts"
          :key="item.postId"
          :class="['post-row', { active: selectedPostId === item.postId }]"
          type="button"
          @click="selectPost(item)"
        >
          <div class="post-row-title">
            <strong>{{ item.title }}</strong>
            <span>{{ item.status }}</span>
          </div>
          <div class="post-row-meta">
            <span>#{{ item.postId }}</span>
            <span>{{ item.topic }}</span>
            <span>{{ item.authorName || `用户 ${item.authorId}` }}</span>
          </div>
          <small>{{ item.content }}</small>
        </button>
      </aside>

      <article class="community-detail">
        <div v-if="loadingDetail" class="empty">社区详情加载中...</div>
        <template v-else-if="detail">
          <header class="detail-head">
            <div>
              <strong>{{ detail.title }}</strong>
              <span>{{ detail.postNo }} / ID {{ detail.postId }}</span>
            </div>
            <b :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</b>
          </header>

          <dl class="detail-grid">
            <div><dt>作者</dt><dd>{{ detail.authorName || `用户 ${detail.authorId}` }}（ID {{ detail.authorId }}）</dd></div>
            <div><dt>城市</dt><dd>{{ detail.city || '未公开' }}</dd></div>
            <div><dt>话题</dt><dd>{{ detail.topic }}</dd></div>
            <div><dt>互动</dt><dd>{{ detail.likeCount }} 赞 / {{ detail.commentCount }} 评论</dd></div>
            <div><dt>发布时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
            <div><dt>关联商品</dt><dd>{{ relatedProductText }}</dd></div>
          </dl>

          <section class="trace-block">
            <strong>帖子内容</strong>
            <p>{{ detail.content }}</p>
          </section>

          <section v-if="safeImages.length" class="trace-block">
            <strong>图片凭证</strong>
            <div class="evidence-grid">
              <a v-for="(url, index) in safeImages" :key="url" class="evidence-link" :href="url" target="_blank" rel="noopener noreferrer">
                <span>图片 {{ index + 1 }}</span>
                <small>{{ url }}</small>
              </a>
            </div>
          </section>

          <section class="trace-block">
            <div class="trace-block-head">
              <strong>评论记录</strong>
              <span v-if="matchedCommentCount > 0">命中评论 {{ matchedCommentCount }} 条</span>
            </div>
            <div v-if="detail.comments.length === 0" class="empty inline">暂无评论。</div>
            <div v-else class="comment-list">
              <div
                v-for="comment in detail.comments"
                :key="comment.commentNo"
                :class="['comment-item', { matched: commentMatchesKeyword(comment) }]"
              >
                <div>
                  <strong>{{ comment.authorName || `用户 ${comment.authorId}` }}</strong>
                  <span>{{ comment.commentNo }}</span>
                  <b v-if="commentMatchesKeyword(comment)">命中</b>
                </div>
                <p>{{ comment.content }}</p>
                <small>{{ comment.createdAt || '暂无时间' }}</small>
              </div>
            </div>
          </section>

          <p class="safe-note">社区追溯仅展示平台持久化记录；删帖、封禁、支付或退款处置不在本页执行。</p>
        </template>
        <div v-else class="empty">请选择左侧帖子，或输入帖子 ID 查询详情。</div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAdminCommunityPostDetail,
  getAdminCommunityPosts,
  isValidAdminCommunityTraceId,
  isValidAdminCommunityTraceKeyword,
  isValidAdminUserId,
  type AdminCommunityPostDetailTrace,
  type AdminCommunityCommentTrace,
  type AdminCommunityPostTrace
} from '../../api'
import { isSafeCommunityImageUrl } from './community-trace-media'

const route = useRoute()
const router = useRouter()
const postId = ref('')
const authorId = ref('')
const keyword = ref('')
const limit = ref(20)
const posts = ref<AdminCommunityPostTrace[]>([])
const detail = ref<AdminCommunityPostDetailTrace | null>(null)
const selectedPostId = ref<number | null>(null)
const loadingList = ref(false)
const loadingDetail = ref(false)
const error = ref('')

const safeImages = computed(() => (detail.value?.imageUrls || []).filter(isSafeCommunityImageUrl))
const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const matchedCommentCount = computed(() => (detail.value?.comments || []).filter(commentMatchesKeyword).length)
const relatedProductText = computed(() => {
  const current = detail.value
  if (!current?.relatedProductId) return '暂无关联商品'
  const price = current.relatedProductPrice === null || current.relatedProductPrice === undefined || current.relatedProductPrice === '' ? '价格以平台为准' : `¥${current.relatedProductPrice}`
  return `${current.relatedProductTitle || '平台商品'}（ID ${current.relatedProductId} / ${price}）`
})

function queryForCurrentFilters() {
  return {
    ...(authorId.value.trim() ? { authorId: authorId.value.trim() } : {}),
    ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
    limit: String(limit.value)
  }
}

function commentMatchesKeyword(comment: AdminCommunityCommentTrace): boolean {
  const currentKeyword = normalizedKeyword.value
  if (!currentKeyword) return false
  const commentNo = String(comment.commentNo || '').trim().toLowerCase()
  const content = String(comment.content || '').trim().toLowerCase()
  return commentNo === currentKeyword || content.includes(currentKeyword)
}

async function loadPosts() {
  error.value = ''
  posts.value = []
  const safeAuthorId = authorId.value.trim()
  const safeKeyword = keyword.value.trim()
  if (safeAuthorId && !isValidAdminUserId(safeAuthorId)) {
    error.value = '作者 ID 无效，请输入正整数。'
    return
  }
  if (safeKeyword && !isValidAdminCommunityTraceKeyword(safeKeyword)) {
    error.value = '关键词无效：最多 64 字，不能包含测试占位语义。'
    return
  }
  loadingList.value = true
  try {
    posts.value = await getAdminCommunityPosts({
      authorId: safeAuthorId || undefined,
      keyword: safeKeyword || undefined,
      limit: Number(limit.value)
    })
    syncQuery()
    if (posts.value.length === 1) {
      await selectPost(posts.value[0])
    }
  } catch (err) {
    error.value = err instanceof Error && err.message ? err.message : '社区帖子加载失败，请确认管理员权限与服务状态。'
  } finally {
    loadingList.value = false
  }
}

async function selectPost(item: AdminCommunityPostTrace) {
  postId.value = String(item.postId)
  selectedPostId.value = item.postId
  await loadDetail(item.postId)
}

async function loadDetailFromInput() {
  const safePostId = postId.value.trim()
  if (!isValidAdminCommunityTraceId(safePostId)) {
    error.value = '帖子编号无效，请输入真实帖子 ID 或帖子编号。'
    return
  }
  await loadDetail(safePostId)
}

async function loadDetail(id: string | number) {
  error.value = ''
  detail.value = null
  loadingDetail.value = true
  try {
    detail.value = await getAdminCommunityPostDetail(id)
    selectedPostId.value = detail.value.postId
    postId.value = String(detail.value.postId)
    router.replace({ path: `/community-trace/${detail.value.postId}`, query: queryForCurrentFilters() }).catch(() => {})
  } catch (err) {
    error.value = err instanceof Error && err.message ? err.message : '社区详情加载失败，请确认帖子编号与管理员权限。'
  } finally {
    loadingDetail.value = false
  }
}

function syncQuery() {
  router.replace({
    path: route.path,
    query: queryForCurrentFilters()
  }).catch(() => {})
}

onMounted(() => {
  const routePostId = String(route.params.postId || '').trim()
  const routeAuthorId = String(route.query.authorId || '').trim()
  const routeKeyword = String(route.query.keyword || '').trim()
  const routeLimit = Number(route.query.limit)
  if (routeAuthorId) authorId.value = routeAuthorId
  if (routeKeyword) keyword.value = routeKeyword
  if (Number.isInteger(routeLimit) && routeLimit >= 1 && routeLimit <= 100) limit.value = routeLimit
  if (routePostId) {
    postId.value = routePostId
    void loadDetailFromInput()
  }
  void loadPosts()
})
</script>

<style scoped>
.community-grid {
  display: grid;
  grid-template-columns: minmax(260px, 360px) 1fr;
  gap: 16px;
}

.community-list,
.community-detail,
.trace-block {
  background: #fff;
  border: 1px solid #f1d6c4;
  border-radius: 12px;
  padding: 16px;
}

.section-head,
.trace-block-head,
.post-row-title,
.post-row-meta,
.comment-item > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.post-row {
  width: 100%;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #f1d6c4;
  border-radius: 10px;
  background: #fffaf6;
  text-align: left;
  cursor: pointer;
}

.post-row.active {
  border-color: #ef6f3f;
  box-shadow: 0 8px 22px rgba(239, 111, 63, .12);
}

.post-row-title strong,
.post-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.post-row-meta,
.comment-item span,
.comment-item small {
  color: #8f6b57;
  font-size: 12px;
}

.trace-block {
  margin-top: 14px;
}

.trace-block-head span {
  padding: 4px 8px;
  border-radius: 999px;
  background: #fff1e8;
  color: #c05621;
  font-size: 12px;
  font-weight: 800;
}

.trace-block p,
.comment-item p {
  color: #342116;
  line-height: 1.6;
  white-space: pre-wrap;
}

.evidence-grid {
  display: grid;
  gap: 10px;
}

.comment-list {
  display: grid;
  gap: 10px;
}

.comment-item {
  padding: 12px;
  border-radius: 10px;
  background: #fff8f0;
}

.comment-item.matched {
  border: 1px solid rgba(239, 111, 63, .45);
  background: linear-gradient(180deg, #fff7ed, #fff1e7);
  box-shadow: 0 8px 20px rgba(239, 111, 63, .08);
}

.comment-item b {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 999px;
  background: #ef6f3f;
  color: #fff;
  font-size: 11px;
}

.empty.inline {
  margin-top: 10px;
}

@media (max-width: 900px) {
  .community-grid {
    grid-template-columns: 1fr;
  }
}
</style>
