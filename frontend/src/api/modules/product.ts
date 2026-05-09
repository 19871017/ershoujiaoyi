import { del, get, post, put } from '../http'

export type ProductCreateStatus = 'created' | 'PENDING_AUDIT' | 'ACTIVE' | 'SOLD'
export type ProductAuditState = 'pending' | 'PENDING' | 'APPROVED' | 'REJECTED'

export interface CreateProductRequest {
  title: string
  description?: string
  price: string
  imageUrls: string[]
}

export interface CreateProductResponse {
  productId: number
  productNo: string
  title: string
  price: string
  status: ProductCreateStatus
  auditState: ProductAuditState
  visible: boolean
  tradeRule: string
  createdAt: string
}

export interface UpdateProductResponse {
  productId: number
  productNo: string
  title: string
  description?: string
  price: string
  imageUrls: string[]
  status: ProductCreateStatus
  auditState: ProductAuditState
  visible: boolean
  tradeRule: string
}

export interface ProductListItemResponse {
  productId: number
  productNo: string
  title: string
  price: string
  coverImageUrl: string | null
  status: ProductCreateStatus
  auditState: ProductAuditState
  visible: boolean
  createdAt: string
}

export interface ProductDetailResponse {
  productId: number
  productNo: string
  title: string
  description?: string
  price: string
  imageUrls: string[]
  status: ProductCreateStatus
  auditState: ProductAuditState
  visible: boolean
  tradeRule: string
  createdAt: string
  sellerId?: number | null
}

export function listProducts() {
  return get<ProductListItemResponse[]>('/api/products')
}

export function listSellerProducts(sellerId: number | string) {
  return get<ProductListItemResponse[]>(`/api/products/seller/${encodeURIComponent(String(sellerId))}`)
}

export function listMyProducts() {
  return get<ProductListItemResponse[]>('/api/products/mine')
}

export function getProductDetail(productId: number) {
  return get<ProductDetailResponse>(`/api/products/${productId}`)
}

export function createProduct(data: CreateProductRequest) {
  return post<CreateProductResponse>('/api/products', data)
}

export function updateProduct(productId: number, data: CreateProductRequest) {
  return put<UpdateProductResponse>(`/api/products/${productId}`, data)
}

export function listFavoriteProducts() {
  return get<ProductListItemResponse[]>('/api/products/favorites')
}

export function favoriteProduct(productId: number) {
  return post<void>(`/api/products/${productId}/favorite`)
}

export function unfavoriteProduct(productId: number) {
  return del<void>(`/api/products/${productId}/favorite`)
}
