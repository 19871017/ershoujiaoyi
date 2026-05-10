import { describe, expect, it } from 'vitest'
import { adminRoutes } from './index'

function childPaths() {
  const shell = adminRoutes.find((route) => route.path === '/' && Array.isArray(route.children))
  return shell?.children?.map((route) => route.path) ?? []
}

describe('admin router sensitive detail routes', () => {
  it('registers backend-id detail routes for order, after-sales, and user workbenches', () => {
    expect(childPaths()).toEqual(expect.arrayContaining(['orders/:orderNo', 'after-sales/:afterSalesNo', 'users/:userId']))
    expect(childPaths()).toEqual(expect.arrayContaining(['audit/:auditNo']))
  })
})
