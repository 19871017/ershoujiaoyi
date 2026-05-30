# P0 Seller Permission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make registration default to buyer mode and allow product publishing only for backend-verified sellers.

**Architecture:** Keep frontend gating for user experience, but enforce the rule in backend services before product creation. Video identity approval remains the first-stage seller certification signal and promotes the user profile to seller status.

**Tech Stack:** Java 17, Spring Boot, JdbcTemplate, JUnit 5, Vue 3, uni-app H5, TypeScript.

---

## File Structure

- Modify: `backend/src/main/java/com/secondhand/platform/modules/audit/application/AuditApplicationService.java` — promote approved video identity users to seller role and demote rejected video identity users to buyer-only role.
- Modify: `backend/src/main/java/com/secondhand/platform/modules/product/application/ProductApplicationService.java` — enforce certified seller permission before creating products.
- Modify: `backend/src/test/java/com/secondhand/platform/modules/audit/application/AuditApplicationServiceTest.java` — verify approval/rejection changes seller role correctly.
- Modify: `backend/src/test/java/com/secondhand/platform/modules/product/application/ProductApplicationServiceTest.java` — verify unverified buyers cannot publish and verified sellers can publish.
- Verify existing frontend: `frontend/src/pages/tabbar/me/index.vue`, `frontend/src/pages/product/publish/index.vue`, `frontend/src/pages/tabbar/publish/index.vue` — keep current server-derived `canPublish` and publish-page redirect behavior.

### Task 1: Video Identity Approval Promotes Seller Role

**Files:**
- Modify: `backend/src/main/java/com/secondhand/platform/modules/audit/application/AuditApplicationService.java`
- Test: `backend/src/test/java/com/secondhand/platform/modules/audit/application/AuditApplicationServiceTest.java`

- [ ] **Step 1: Write the failing approval/rejection assertions**

In `videoIdentityAuditShouldMarkProfilePendingThenApproved`, after approval, assert:

```java
assertEquals("SELLER", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
```

In `rejectedVideoIdentityAuditShouldNotExposeSellerAsVerified`, insert the profile as a seller before submission and assert rejection returns to buyer:

```java
jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?,?,?,?,?)", userId, "VERIFIED", "SELLER", "UNVERIFIED", false);
...
assertEquals("BUYER", jdbcTemplate.queryForObject("SELECT main_role FROM user_profile WHERE user_id = ?", String.class, userId));
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f /Users/wangyu/Projects/xiaoyuanquan/backend/pom.xml test -Dtest=AuditApplicationServiceTest#videoIdentityAuditShouldMarkProfilePendingThenApproved,AuditApplicationServiceTest#rejectedVideoIdentityAuditShouldNotExposeSellerAsVerified
```

Expected: at least one assertion fails because `main_role` is not updated by video identity review.

- [ ] **Step 3: Implement role sync**

In `syncVideoIdentityStatus`, change approval update to set `main_role = 'SELLER'` and rejection update to set `main_role = 'BUYER'`:

```java
jdbcTemplate.update("update user_profile set main_role = ?, video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", "SELLER", STATUS_APPROVED, true, userId);
```

```java
jdbcTemplate.update("update user_profile set main_role = ?, video_identity_status = ?, video_verified = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", "BUYER", STATUS_REJECTED, false, userId);
```

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: BUILD SUCCESS.

### Task 2: Product Creation Requires Certified Seller

**Files:**
- Modify: `backend/src/main/java/com/secondhand/platform/modules/product/application/ProductApplicationService.java`
- Test: `backend/src/test/java/com/secondhand/platform/modules/product/application/ProductApplicationServiceTest.java`

- [ ] **Step 1: Add seller fixtures and failing tests**

Add a `JdbcTemplate jdbcTemplate` field to the test class, initialize it once in `setUp`, and use it for `service`. Add helper:

```java
private void upsertProfile(long userId, String role, String videoStatus, boolean videoVerified) {
    jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)", userId, "U-PRODUCT-" + userId, "1380013" + String.format("%04d", userId), "hash", "商品用户" + userId, "ACTIVE");
    jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?)", userId, "VERIFIED", role, videoStatus, videoVerified);
}
```

Add test:

```java
@Test
void buyerCannotCreateProductUntilSellerCertificationApproved() {
    upsertProfile(21L, "BUYER", "UNVERIFIED", false);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createProduct(21L, product(21L, "未认证发布商品", "79.00")));

    assertEquals("seller certification required", error.getMessage());
}
```

Update existing create-product tests to call `upsertProfile` for seller ids before `createProduct`.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f /Users/wangyu/Projects/xiaoyuanquan/backend/pom.xml test -Dtest=ProductApplicationServiceTest#buyerCannotCreateProductUntilSellerCertificationApproved
```

Expected: FAIL until backend permission check is added.

- [ ] **Step 3: Implement backend permission check**

At the top of `createProduct`, after `requirePositiveId`, call:

```java
requireCertifiedSeller(sellerId);
```

Add method:

```java
private void requireCertifiedSeller(Long sellerId) {
    List<Boolean> rows = jdbcTemplate.query("""
            SELECT CASE WHEN a.status = 'ACTIVE'
                     AND UPPER(COALESCE(p.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                     AND p.video_identity_status = 'APPROVED'
                     AND p.video_verified = TRUE
                THEN TRUE ELSE FALSE END AS can_publish
            FROM user_account a
            JOIN user_profile p ON p.user_id = a.id
            WHERE a.id = ?
            """, (rs, rowNum) -> rs.getBoolean("can_publish"), sellerId);
    if (rows.isEmpty() || !rows.get(0)) {
        throw new IllegalArgumentException("seller certification required");
    }
}
```

- [ ] **Step 4: Run product tests**

Run:

```bash
mvn -f /Users/wangyu/Projects/xiaoyuanquan/backend/pom.xml test -Dtest=ProductApplicationServiceTest
```

Expected: BUILD SUCCESS.

### Task 3: Verify P0 Frontend Guards

**Files:**
- Verify: `frontend/src/pages/tabbar/me/index.vue`
- Verify: `frontend/src/pages/product/publish/index.vue`
- Verify: `frontend/src/pages/tabbar/publish/index.vue`

- [ ] **Step 1: Confirm frontend derives publishing from backend profile**

Check `canPublish` and `publishReady` use `profile.videoVerified` and `mainRole` from `getMyProfile`.

- [ ] **Step 2: Run frontend checks**

Run:

```bash
npm --prefix /Users/wangyu/Projects/xiaoyuanquan/frontend run check:me-real-data
npm --prefix /Users/wangyu/Projects/xiaoyuanquan/frontend run check:publish-form-no-static-defaults
npm --prefix /Users/wangyu/Projects/xiaoyuanquan/frontend run check:pages-consistency
npm --prefix /Users/wangyu/Projects/xiaoyuanquan/frontend run typecheck
```

Expected: all commands pass.

### Task 4: Final Verification and Report

**Files:**
- Verify backend and frontend builds.

- [ ] **Step 1: Run targeted backend tests**

```bash
mvn -f /Users/wangyu/Projects/xiaoyuanquan/backend/pom.xml test -Dtest=AuthApplicationServiceTest,AuditApplicationServiceTest,ProductApplicationServiceTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run production static check**

```bash
python3 /Users/wangyu/Projects/xiaoyuanquan/scripts/check-production-readiness.py
```

Expected: `issues=0`.

- [ ] **Step 3: Build production artifacts if deploying**

```bash
npm --prefix /Users/wangyu/Projects/xiaoyuanquan/frontend run build:h5:prod
mvn -f /Users/wangyu/Projects/xiaoyuanquan/backend/pom.xml package spring-boot:repackage -DskipTests
jar xf /Users/wangyu/Projects/xiaoyuanquan/backend/target/backend-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF && grep -E 'Main-Class|Start-Class' META-INF/MANIFEST.MF
```

Expected manifest contains `Main-Class: org.springframework.boot.loader.launch.JarLauncher` and `Start-Class: com.secondhand.platform.apps.api.ApiApplication`.

- [ ] **Step 4: Write repair report in final response**

Report must include: changed behavior, files changed, tests/builds run, deployment/Git status, and remaining P1/P2 items.
