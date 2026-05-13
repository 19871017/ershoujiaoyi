<template>
  <section class="page-shell banner-page">
    <div class="page-title">首页轮播图</div>
    <div class="page-desc">后台更换首页首屏轮播图；用户端读取 /api/home/banners，失败不展示本地假成功图。</div>

    <div class="config-panel">
      <div class="panel-head">
        <div>
          <strong>图片尺寸说明</strong>
          <span>{{ sizeHint }}</span>
        </div>
        <button class="ghost-btn" :disabled="loading" @click="loadBanners">{{ loading ? '刷新中...' : '刷新列表' }}</button>
      </div>
      <div class="safe-note">上传后请填写后端返回的 /uploads/ 路径或 HTTPS 图片地址；禁止 demo/mock/placeholder 临时图进入线上配置。</div>
      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading && banners.length === 0" class="empty">轮播图加载中...</div>
      <div v-if="!loading && banners.length === 0" class="empty">暂无后端轮播图配置。</div>
      <div class="banner-list">
        <article v-for="item in banners" :key="item.id" class="banner-row">
          <img :src="item.imageUrl" :alt="item.title" />
          <div class="banner-info">
            <strong>{{ item.title }}</strong>
            <span>{{ item.kicker }} · {{ item.cta }} · {{ actionLabel(item.action) }}</span>
            <small>排序 {{ item.sortOrder }} / {{ item.enabled ? '已启用' : '已停用' }} / {{ item.updatedAt || '暂无更新时间' }}</small>
          </div>
          <div class="row-actions">
            <button class="ghost-btn" @click="edit(item)">编辑</button>
            <button class="danger-btn" @click="remove(item)">删除</button>
          </div>
        </article>
      </div>
    </div>

    <form class="config-panel" @submit.prevent="saveBanner">
      <div class="panel-head">
        <div>
          <strong>{{ form.id ? '编辑轮播图' : '新增轮播图' }}</strong>
          <span>标题/图片/跳转动作保存后立即以服务端配置为准</span>
        </div>
        <button v-if="form.id" type="button" class="ghost-btn" @click="resetForm">新增一张</button>
      </div>
      <div class="form-grid">
        <label>
          <span>角标文案</span>
          <input v-model.trim="form.kicker" maxlength="32" placeholder="例如 小原圈 · 今日新鲜" />
        </label>
        <label>
          <span>主标题</span>
          <input v-model.trim="form.title" maxlength="40" placeholder="例如 把心爱闲置交给懂它的人" />
        </label>
        <label>
          <span>说明文案</span>
          <input v-model.trim="form.description" maxlength="80" placeholder="一句话说明活动/频道" />
        </label>
        <label>
          <span>按钮文案</span>
          <input v-model.trim="form.cta" maxlength="16" placeholder="例如 去发现" />
        </label>
        <label class="wide-field">
          <span>图片地址</span>
          <input v-model.trim="form.imageUrl" placeholder="/uploads/home/banner.jpg 或 https://cdn.example.com/banner.jpg" />
        </label>
        <label>
          <span>跳转动作</span>
          <select v-model="form.action">
            <option value="closet">小原圈/分类</option>
            <option value="ranking">榜单</option>
            <option value="forum">社区</option>
            <option value="search">搜索</option>
            <option value="none">不跳转</option>
          </select>
        </label>
        <label>
          <span>排序</span>
          <input v-model.number="form.sortOrder" type="number" min="1" max="999" />
        </label>
        <label class="toggle-row">
          <input v-model="form.enabled" type="checkbox" />
          <span>启用展示</span>
        </label>
      </div>
      <p class="safe-note">推荐 750×300px（5:2），单张不超过 500KB；文字主体放中间安全区，避免圆角裁切。</p>
      <label class="confirm-row">
        <span>二次确认</span>
        <input v-model.trim="confirmText" autocomplete="off" placeholder="输入 保存首页轮播 后才能提交" />
      </label>
      <button class="primary-btn" :disabled="saving">{{ saving ? '保存中...' : '保存首页轮播' }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createAdminHomeBanner, deleteAdminHomeBanner, getAdminHomeBanners, updateAdminHomeBanner, type AdminHomeBanner, type AdminHomeBannerAction } from '../../../api'

const defaultHint = '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB；重要文字和主体放在中间安全区。'
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const banners = ref<AdminHomeBanner[]>([])
const sizeHint = ref(defaultHint)
const confirmText = ref('')

const form = reactive({
  id: 0,
  kicker: '',
  title: '',
  description: '',
  cta: '',
  imageUrl: '',
  action: 'closet' as AdminHomeBannerAction,
  sortOrder: 10,
  enabled: true
})

function actionLabel(action: AdminHomeBannerAction) {
  return ({ closet: '小原圈/分类', ranking: '榜单', forum: '社区', search: '搜索', none: '不跳转' } as Record<AdminHomeBannerAction, string>)[action] || action
}

function fillForm(item: AdminHomeBanner) {
  form.id = item.id
  form.kicker = item.kicker
  form.title = item.title
  form.description = item.description
  form.cta = item.cta
  form.imageUrl = item.imageUrl
  form.action = item.action
  form.sortOrder = item.sortOrder
  form.enabled = item.enabled
}

function resetForm() {
  form.id = 0
  form.kicker = ''
  form.title = ''
  form.description = ''
  form.cta = ''
  form.imageUrl = ''
  form.action = 'closet'
  form.sortOrder = banners.value.length ? Math.max(...banners.value.map((item) => item.sortOrder)) + 10 : 10
  form.enabled = true
  confirmText.value = ''
}

function edit(item: AdminHomeBanner) {
  fillForm(item)
  confirmText.value = ''
}

function toPayload() {
  return {
    kicker: form.kicker,
    title: form.title,
    description: form.description,
    cta: form.cta,
    imageUrl: form.imageUrl,
    action: form.action,
    sortOrder: Number(form.sortOrder),
    enabled: Boolean(form.enabled)
  }
}

async function loadBanners() {
  loading.value = true
  error.value = ''
  try {
    banners.value = await getAdminHomeBanners()
    sizeHint.value = banners.value[0]?.sizeHint || defaultHint
    if (!form.id) resetForm()
  } catch {
    banners.value = []
    error.value = '首页轮播图加载失败：请确认管理员权限、后端服务与 /api/admin/home/banners 可用。'
  } finally {
    loading.value = false
  }
}

async function saveBanner() {
  error.value = ''
  if (confirmText.value !== '保存首页轮播') {
    error.value = '首页轮播保存已阻止：请输入“保存首页轮播”完成二次确认。'
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateAdminHomeBanner(form.id, toPayload())
    } else {
      await createAdminHomeBanner(toPayload())
    }
    await loadBanners()
    confirmText.value = ''
  } catch (err) {
    error.value = err instanceof Error ? err.message : '首页轮播保存失败：未写入本地成功态，请检查字段与后端接口。'
  } finally {
    saving.value = false
  }
}

async function remove(item: AdminHomeBanner) {
  error.value = ''
  if (!window.confirm(`确认删除首页轮播图“${item.title}”？`)) return
  try {
    await deleteAdminHomeBanner(item.id)
    await loadBanners()
  } catch {
    error.value = '首页轮播删除失败：未写入本地成功态，请检查权限与后端接口。'
  }
}

onMounted(loadBanners)
</script>

<style scoped>
.banner-list { display:flex; flex-direction:column; gap:14px; }
.banner-row { display:grid; grid-template-columns:180px 1fr auto; gap:16px; align-items:center; padding:14px; border:1px solid rgba(148,163,184,.22); border-radius:18px; background:rgba(15,23,42,.54); }
.banner-row img { width:180px; height:72px; object-fit:cover; border-radius:14px; background:#111827; }
.banner-info { display:flex; flex-direction:column; gap:6px; }
.banner-info strong { color:#f8fafc; }
.banner-info span { color:#cbd5e1; }
.banner-info small { color:#94a3b8; }
.row-actions { display:flex; gap:8px; }
.danger-btn { border:1px solid rgba(248,113,113,.46); color:#fecaca; background:rgba(127,29,29,.26); border-radius:12px; padding:9px 12px; cursor:pointer; }
.wide-field { grid-column:1 / -1; }
</style>
