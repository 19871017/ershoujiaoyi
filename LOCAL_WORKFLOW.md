# 小原圈本地开发与上线同步流程

本地项目目录：/Users/wangyu/Projects/xiaoyuanquan
GitHub 仓库：https://github.com/19871017/ershoujiaoyi.git
生产服务器：root@23.138.12.9
服务器部署根目录：/www/wwwroot/esxz-old
服务名：esxz-old

以后默认流程：
1. 在本地目录 /Users/wangyu/Projects/xiaoyuanquan 修改代码。
2. 修改完成后先检查/构建。
3. 提交并推送到 GitHub main。
4. 再从 GitHub 或本地构建产物更新服务器。

常用命令：

进入项目：
cd /Users/wangyu/Projects/xiaoyuanquan

查看改动：
git status --short

git 同步提交：
bash scripts/local-sync-git.sh "fix: 本次修改说明"

前端 H5 生产构建：
cd /Users/wangyu/Projects/xiaoyuanquan/frontend
npm install
npm run build:h5:prod

管理后台构建：
cd /Users/wangyu/Projects/xiaoyuanquan/admin
npm install
npm run build

后端构建：
cd /Users/wangyu/Projects/xiaoyuanquan/backend
mvn -DskipTests package

注意：
- 用户端开发预览/补页面时，按项目约定保留 mock/dev headers；生产检查走 build:h5:prod，不要为了本地预览随便关闭开发开关。
- 部署服务器需要 SSH 权限；当前本机还不能免密登录 root@23.138.12.9。
- 服务器现有路径记录：current->{h5,admin,backend.jar}，服务文件 /etc/systemd/system/esxz-old.service，env/data/logs 在 /opt/esxz-old，nginx 配置 old.tiklxd09.club.conf，备份 /www/backup/esxz-old。
