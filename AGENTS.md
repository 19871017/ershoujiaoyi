# 小原圈 / xiaoyuanquan 项目记忆

## 项目定位与当前优先级
- 项目：小原圈，一个二手闲置社交交易平台，MVP / 快速上线导向。
- 优先级：先修上线阻塞、支付/订单/IM/财务/安全/真实数据闭环，再做 UI 细节打磨。
- 首期原则：模块化单体；IM 与财务优先边界化；所有资金动作账本化；页面不直接管理 WebSocket；核心协议与核心表不要多 agent 同步改写。
- 用户偏好：页面“小而美”、紧凑、温暖橙色系；文案使用“社区”，不要用“论坛”；保留社区入口、男神/女神排行入口。

## 本地仓库与 Git
- 本地路径：`/Users/wangyu/Projects/xiaoyuanquan`
- 用户端前端：`frontend/`，uni-app / Vue H5。
- 管理后台：`admin/`。
- 后端：`backend/`，Spring Boot。
- GitHub remote：`https://github.com/19871017/ershoujiaoyi.git`
- 主分支：`main`
- 当前本地状态记录：`main` 相对 `origin/main` ahead 27，最近提交 `b686cac fix(admin): restore deployed backoffice login`。不要假设远端已经同步。

## 线上服务器与部署结构
- 服务器：`root@23.138.12.9`
- 部署根：`/www/wwwroot/esxz-old`
- 当前链接：`/www/wwwroot/esxz-old/current -> /www/wwwroot/esxz-old/releases/20260512-215939`
- 线上结构：
  - H5：`/www/wwwroot/esxz-old/current/h5`
  - H5 绝对资源镜像：`/www/wwwroot/esxz-old/current/assets`
  - 管理后台静态文件：`/www/wwwroot/esxz-old/current/admin`
  - 后端 jar：`/www/wwwroot/esxz-old/current/backend.jar`
- 服务：`/etc/systemd/system/esxz-old.service`，服务名 `esxz-old`。
- 环境/数据/日志：`/opt/esxz-old`。
- nginx 配置：`old.tiklxd09.club.conf`。
- 备份：`/www/backup/esxz-old`。
- 线上后端端口：`18080`。
- 最近线上核验：`systemctl is-active esxz-old` 为 active，`http://127.0.0.1:18080/actuator/health` 返回 `{"status":"UP"}`，`http://old.tiklxd09.club/` 和 `/admin/` 返回 200。
- 线上当前 release 里有 `backend.jar.bad-no-main.20260514235549`，说明曾因非 Spring Boot 可执行 jar 出过 `no main manifest attribute`，后端发版必须验证 jar manifest。

## H5 构建与部署规则
- 前端根目录：`/Users/wangyu/Projects/xiaoyuanquan/frontend`
- 常规验证：
  - `npm run typecheck`
  - `npm run build:h5`
- 常规 `build:h5` 故意启用 mock/dev headers/LAN fallback，用于开发预览/补页面；不要为了普通页面完成而默认关闭。
- 生产检查才用：`npm run build:h5:prod`。
- H5 输出：`frontend/dist/build/h5`
- uni-app H5 的 `index.html` 使用绝对 `/assets/...` 资源路径。因此部署不能只同步 `h5/`，还必须同步服务器根级 `assets/`：
  1. `frontend/dist/build/h5/` -> `/www/wwwroot/esxz-old/current/h5/`
  2. `/www/wwwroot/esxz-old/current/h5/assets/` -> `/www/wwwroot/esxz-old/current/assets/`
- 线上最近核验：`h5/index.html` 引用了 `/assets/uni.0c5863a7.css`、`/assets/index-Dno8ErxB.js`、`/assets/index-DcsixrqJ.css`；`current/h5` 与 `current/assets` 均存在。

## 后端构建与部署规则
- 后端根目录：`/Users/wangyu/Projects/xiaoyuanquan/backend`
- 如果有后端 Java/API 改动，不能只看 `mvn package` 成功；必须构建 Spring Boot 可执行 jar：
  - `mvn package spring-boot:repackage -DskipTests`
- 替换线上 `backend.jar` 前必须验证 manifest：
  - `Main-Class: org.springframework.boot.loader.launch.JarLauncher`
  - `Start-Class: com.secondhand.platform.apps.api.ApiApplication`
- 部署后验证：
  - `systemctl is-active esxz-old`
  - `curl -fsS http://127.0.0.1:18080/actuator/health`
- 如果日志出现 `no main manifest attribute`，立即重新 repackage、替换 jar、重启服务。

## 管理后台 / admin
- 线上入口：`http://old.tiklxd09.club/admin/`
- 线上静态路径：`/www/wwwroot/esxz-old/current/admin`
- 本地路径：`/Users/wangyu/Projects/xiaoyuanquan/admin`
- 登录接口：`/api/admin/session/login`
- 前端 sessionStorage key：`xiaoyuanquan_admin_session`
- 管理员种子账号来自 `backend/src/main/resources/db/data.sql`：
  - mobile / phone：`13800138000`
  - nickname：`小原圈管理员`
  - 密码是 PBKDF2 hash，不能反推明文。
- `AdminSessionController` 校验 `mobile/password`，并要求用户至少有一条启用的 `admin_user_permission`。
- admin 构建坑：源码 API 路径已经包含 `/api/...`，如果构建时再设置 `VITE_API_BASE=/api`，可能变成 `/api/api/admin/session/login`，导致登录失败。上线前 grep admin assets，确保没有 `localhost:18080`、没有 `/api/api/`，浏览器应 same-origin 请求 `/api/admin/session/login`。

## 前端 API / 环境规则
- HTTP 客户端：`frontend/src/api/http.ts`
- 关键环境变量：
  - `VITE_API_BASE_URL`
  - `VITE_ENABLE_MOCK_DATA`
  - `VITE_ENABLE_DEV_HEADERS`
  - `VITE_ENABLE_LAN_API_FALLBACK`
  - `VITE_DEV_USER_ID`
- 开发 headers：`X-User-Id` 与 `X-Dev-Mode: enabled`。
- 生产域名不要让浏览器请求 `old.tiklxd09.club:18080`。LAN fallback 只能用于私有局域网主机或手机预览；生产应走 same-origin `/api/...`。
- 若本机 curl 后端正常但浏览器失败，先查 H5 bundle 是否把 API base 变成 `:18080` 或 `/api/api/`。

## 业务安全与真实数据规则
- 视频认证卖家信任卡/徽章只能在后端持久化视频认证审核通过后展示；pending/rejected/default 不显示。
- 敏感媒体必须走后端媒体上传票据，不能把本地、placeholder、preview、外链 URL 持久化。已知场景：
  - `VIDEO_IDENTITY`
  - `PRODUCT_IMAGE`
  - `COMMUNITY_IMAGE`
  - `AFTER_SALES_EVIDENCE`
  - `REPORT_EVIDENCE`
  - `CHAT_IMAGE`
- 售后申请必须由已支付订单买家提交；证据必须是 `AFTER_SALES_EVIDENCE` ticket URL；退款金额必须 >0 且不超过订单金额。
- 页面不要用本地 demo 数据假装成功；失败要 fail-closed，并给用户友好文案。
- 钱包、支付、订单、IM、资料、关注、举报、售后等状态不能本地乐观伪造成功。

## 用户端重点页面与偏好
- 页面配置有两份，涉及导航标题时要保持一致：
  - `frontend/src/pages.json`
  - `frontend/pages.json`
- 首页：`frontend/src/pages/tabbar/home/index.vue`
  - 保留社区和男神/女神排行入口。
  - 排行卡偏好：漫画风真实图片背景；女神榜暖玫瑰/橙/金，男神榜深蓝/电光/高质感；文字要有暗色遮罩确保可读。
  - 当前源码含 `rankingArtwork`，榜单入口文案包括“魅力女神榜”“多金男神榜”。
- 社区 tab：`frontend/src/pages/tabbar/message/index.vue`
  - 使用“社区”语义。
  - 发帖入口偏好为右下角圆形 `＋` FAB（`compose-fab`），不是顶部明显“发帖”按钮。
  - topic 过滤要对齐后端实际值：`生活日常`、`闲置避坑`、`交易经验`、`求购心愿`。
- 宝贝页 / category：`frontend/src/pages/tabbar/category/index.vue`
  - 保留搜索和真实后端商品筛选。
- 我的页：`frontend/src/pages/tabbar/me/index.vue`
  - 删除开发/审核式文案，保留真实 profile/wallet 数据与 fail-closed。
- 上新 / publish：`frontend/src/pages/tabbar/publish/index.vue`
  - 图片上传必须使用 `PRODUCT_IMAGE`。
  - H5 输入问题优先用显式 `:value` + `@input`，不要依赖可能漂移的 `v-model.trim`。
  - 交易规则如需简化，只暴露并提交“按平台订单流程交易”。
- 公共卖家资料：`frontend/src/pages/user/public-profile/index.vue`
  - `profile.videoVerified` 为真才展示“视频认证卖家”等信任卡/徽章。

## 项目检查脚本
- 总体生产静态检查：`python3 scripts/check-production-readiness.py`
- 前端基础检查：`npm run typecheck`
- H5 构建：`npm run build:h5`
- 前端有大量 `check:*` 守护脚本，修改对应页面后优先跑对应脚本。例如：
  - `npm run check:home-real-products`
  - `npm run check:category-real-products`
  - `npm run check:community-feed-real-data`
  - `npm run check:community-compose-real-post`
  - `npm run check:community-comment`
  - `npm run check:community-detail-follow`
  - `npm run check:me-real-data`
  - `npm run check:notification-real-data`
  - `npm run check:upload-evidence`
  - `npm run check:after-sales-route-guard`
  - `npm run check:lan-api-base`
  - `npm run check:admin-sensitive-id-guards`
  - `npm run check:user-no-admin-entry`
- 最近一次生产 readiness 静态检查曾通过：`java_files=153`，`frontend_files=72`，`issues=0`。这只是静态检查，不等于所有业务链路都已完成。

## 工作流偏好
- 修改前先读目标文件，不要只凭搜索结果 patch。
- 完成后先本地检查/构建，再 commit；如用户期待同步，尝试推 GitHub 和部署服务器，失败时说明阻塞。
- 小原圈回复要简洁：问题/改动/验证/部署或 Git 状态/下一优先级。不要尾部广告式重复或无必要 URL。
- 如果让 Codex 接手，优先让它从本文件、`README.md`、`LOCAL_WORKFLOW.md` 以及相关源码文件读取上下文，不要凭记忆直接大改。
