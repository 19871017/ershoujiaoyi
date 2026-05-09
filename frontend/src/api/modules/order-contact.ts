import type { OrderDetailResponse, OrderListItemResponse } from './order'
import type { ProductDetailResponse } from './product'

export type OrderContactAction = '联系买家' | '联系卖家' | '联系客服'

export interface OrderContactTarget {
  receiverId: number | null
  error?: string
}

type OrderContactSource =
  | Pick<OrderListItemResponse, 'buyerId' | 'sellerId' | 'role'>
  | Pick<OrderDetailResponse, 'buyerId' | 'sellerId'>

export function resolveOrderContactTarget(
  order: OrderContactSource,
  action: OrderContactAction
): OrderContactTarget {
  if (action === '联系客服') return { receiverId: null, error: '客服会话需要后端分配接待坐席，当前不能使用固定账号伪连接' }

  const isListItem = 'role' in order
  const receiverId = isListItem
    ? (order.role === 'seller' ? order.buyerId : order.sellerId)
    : (action === '联系买家' ? order.buyerId : order.sellerId)

  return validReceiver(receiverId, '订单缺少有效对方账号，不能发起聊天')
}

export function resolveProductSellerContactTarget(product: Pick<ProductDetailResponse, 'sellerId'>): OrderContactTarget {
  return resolveSellerContactTarget(product, '商品缺少有效卖家账号，不能发起聊天')
}

export function resolveSellerContactTarget(source: { sellerId?: number | null }, error = '缺少有效卖家账号，不能发起聊天'): OrderContactTarget {
  return validReceiver(source.sellerId, error)
}

function validReceiver(receiverId: number | null | undefined, error: string): OrderContactTarget {
  if (!Number.isSafeInteger(receiverId) || !receiverId || receiverId <= 0) return { receiverId: null, error }
  return { receiverId }
}
