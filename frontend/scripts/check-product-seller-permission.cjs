const fs = require('fs')
const path = require('path')

const projectRoot = path.resolve(__dirname, '..', '..')
const servicePath = path.join(projectRoot, 'backend/src/main/java/com/secondhand/platform/modules/product/application/ProductApplicationService.java')
const testPath = path.join(projectRoot, 'backend/src/test/java/com/secondhand/platform/modules/product/application/ProductApplicationServiceTest.java')

const service = fs.readFileSync(servicePath, 'utf8')
const test = fs.readFileSync(testPath, 'utf8')
const failures = []

function sectionBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker)
  const end = source.indexOf(endMarker, start)
  if (start < 0 || end <= start) return ''
  return source.slice(start, end)
}

function requireMarkers(source, markers, label) {
  for (const marker of markers) {
    if (!source.includes(marker)) failures.push(`${label} missing marker: ${marker}`)
  }
}

const createProductBody = sectionBetween(
  service,
  'public CreateProductResponse createProduct(Long sellerId, CreateProductRequest request)',
  'public List<ProductListItemResponse> listProducts()'
)
const sellerCheckIndex = createProductBody.indexOf('requireCertifiedSeller(sellerId)')
const insertIndex = createProductBody.indexOf('insert into product_item')

if (!createProductBody) failures.push('product service missing createProduct body')
if (sellerCheckIndex < 0) failures.push('createProduct missing seller certification check')
if (insertIndex < 0) failures.push('createProduct missing product insert')
if (sellerCheckIndex >= 0 && insertIndex >= 0 && sellerCheckIndex > insertIndex) {
  failures.push('createProduct checks seller certification after product insert')
}

const requireCertifiedSellerBody = sectionBetween(
  service,
  'private void requireCertifiedSeller(Long sellerId)',
  'private ProductRecord findByProductNo(String productNo)'
)
if (!requireCertifiedSellerBody) failures.push('product service missing requireCertifiedSeller body')

requireMarkers(requireCertifiedSellerBody, [
  "a.status = 'ACTIVE'",
  "UPPER(COALESCE(p.main_role, 'BUYER')) IN ('SELLER', 'BOTH')",
  "p.video_identity_status = 'APPROVED'",
  'p.video_verified = TRUE',
  'seller certification required'
], 'requireCertifiedSeller')

requireMarkers(test, [
  'void buyerCannotCreateProductUntilSellerCertificationApproved()',
  'service.createProduct(21L',
  'assertEquals("seller certification required", error.getMessage())',
  'void approveForSaleShouldRejectProductWhenSellerCertificationWasRevoked()',
  'upsertProfile(1L, "BUYER", "REJECTED", false)',
  'assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()))'
], 'product seller permission test')

if (failures.length) {
  console.error('product seller permission check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('product publishing requires approved seller certification before insert and is covered by regression tests')
