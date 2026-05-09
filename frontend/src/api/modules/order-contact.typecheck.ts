import { resolveOrderContactTarget, resolveProductSellerContactTarget, type OrderContactAction } from './order-contact'
import type { OrderDetailResponse, OrderListItemResponse } from './order'
import type { ProductDetailResponse } from './product'

function assertEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${expected}, got ${actual}`)
}

function assertIncludes(actual: string | undefined, expected: string, message: string) {
  if (!actual?.includes(expected)) throw new Error(`${message}: expected to include ${expected}, got ${actual}`)
}

const sellerOrder: Pick<OrderListItemResponse, 'buyerId' | 'sellerId' | 'role'> = { buyerId: 7, sellerId: 21, role: 'seller' }
assertEqual(resolveOrderContactTarget(sellerOrder, '联系买家').receiverId, 7, 'seller list item should contact buyer')

const buyerOrder: Pick<OrderListItemResponse, 'buyerId' | 'sellerId' | 'role'> = { buyerId: 7, sellerId: 21, role: 'buyer' }
assertEqual(resolveOrderContactTarget(buyerOrder, '联系卖家').receiverId, 21, 'buyer list item should contact seller')

const detail: Pick<OrderDetailResponse, 'buyerId' | 'sellerId'> = { buyerId: 7, sellerId: 21 }
assertEqual(resolveOrderContactTarget(detail, '联系卖家').receiverId, 21, 'detail should contact seller')
assertEqual(resolveOrderContactTarget(detail, '联系买家').receiverId, 7, 'detail should contact buyer')

assertIncludes(resolveOrderContactTarget(detail, '联系客服').error, '不能使用固定账号伪连接', 'customer service should not return fixed account')

const invalid: Pick<OrderListItemResponse, 'buyerId' | 'sellerId' | 'role'> = { buyerId: 0, sellerId: 21, role: 'seller' }
assertIncludes(resolveOrderContactTarget(invalid, '联系买家').error, '缺少有效对方账号', 'invalid counterparty should fail explicitly')

const product: Pick<ProductDetailResponse, 'sellerId'> = { sellerId: 33 }
assertEqual(resolveProductSellerContactTarget(product).receiverId, 33, 'product contact should use backend seller id')

const productWithoutSeller: Pick<ProductDetailResponse, 'sellerId'> = { sellerId: null }
assertIncludes(resolveProductSellerContactTarget(productWithoutSeller).error, '缺少有效卖家账号', 'product without seller should fail explicitly')

const action: OrderContactAction = '联系卖家'
assertEqual(resolveOrderContactTarget(buyerOrder, action).receiverId, 21, 'typed action should resolve')
