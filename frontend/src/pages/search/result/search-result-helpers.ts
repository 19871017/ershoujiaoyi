import type { ProductListItemResponse } from '../../../api/modules/product'

export type Sort = 'latest' | 'priceAsc' | 'priceDesc'

export const categories = ['全部', '衣物', '鞋袜', '小用品']
export const sorts = [
  { label: '最新', value: 'latest' as const },
  { label: '低价', value: 'priceAsc' as const },
  { label: '高价', value: 'priceDesc' as const }
]

export const launchReadinessMarkers = [
  '商品搜索暂时不可用，请稍后重试',
  '商品接口暂时不可用，未展示本地搜索宝贝样例',
  '仅展示后端返回的在售商品'
]

void launchReadinessMarkers

export function filterProducts(products: ProductListItemResponse[], keyword: string, category: string, sort: Sort): ProductListItemResponse[] {
  const kw = keyword.trim().toLowerCase()
  const categoryText = category === '全部' ? '' : category.toLowerCase()
  let list = products.filter((item) => {
    const text = `${item.title}${item.productNo}${item.status}${item.auditState}`.toLowerCase()
    return (!kw || text.includes(kw)) && (!categoryText || text.includes(categoryText))
  })
  if (sort === 'priceAsc') list = [...list].sort((a, b) => Number(a.price) - Number(b.price))
  if (sort === 'priceDesc') list = [...list].sort((a, b) => Number(b.price) - Number(a.price))
  if (sort === 'latest') list = [...list].sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
  return list
}

export function inputValue(event: unknown): string {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  return typeof value === 'string' ? value : ''
}

export function iconFor(title: string) {
  if (title.includes('裙')) return '👗'
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🧦'
  return '👜'
}

export function statusLabel(status: string) {
  return status === 'created' || status === 'ACTIVE' ? '在售' : status
}

export function compactPrice(price: string) {
  return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}
