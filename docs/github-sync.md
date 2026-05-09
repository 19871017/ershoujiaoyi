# 小原圈 GitHub 同步说明

仓库地址：<https://github.com/19871017/ershoujiaoyi.git>

## 同步原则

- 每次功能修改、修复、巡检优化完成后，都要同步到 GitHub。
- 提交前必须先运行必要验证，至少包含前端类型检查和相关专项检查。
- 不提交依赖目录、构建产物、本地工具包、日志、`.env`、密钥、证书、数据库密码等敏感文件。
- 生产配置中的账号、密码、Token、连接串必须通过环境变量或部署平台 Secret 注入。

## 推荐提交流程

```bash
cd /mnt/c/Users/WangYu/Desktop/esxz-sj-jypt

# 查看变更
git status --short

# 前端常规验证
cd frontend
npm run typecheck
npm run check:pages
npm run build:h5
npm run build:h5:prod
cd ..

# 提交并推送
git add .
git commit -m "chore: sync latest 小原圈 project updates"
git push origin main
```

## 当前忽略范围

`.gitignore` 已排除：

- `node_modules/`
- `frontend/dist/`、`admin/dist/`、`backend/target/`
- `.tools/`、压缩包、本地 JDK/SDK
- `.env`、证书、密钥、凭据文件
- 日志、缓存、临时目录、IDE 配置

## 自动化要求

后续我每次完成代码修改、功能完善或巡检修复后，应执行：

1. 运行对应验证命令；
2. `git status --short` 确认变更；
3. `git add .`；
4. 用清晰的 conventional commit message 提交；
5. `git push origin main` 同步 GitHub；
6. 在报告中说明提交哈希和推送状态。
