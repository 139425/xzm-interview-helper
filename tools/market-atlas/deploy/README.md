# 观市部署到面试助手

观市保留独立 Worker 与本地 D1，通过面试助手的 `/tools/market-atlas/` 入口访问。服务监听宿主 `127.0.0.1:5174`，现有 Nginx 负责公网访问。本文使用 `interview.example.com` 演示多个协议或端口入口；实际地址与环境文件只保存在部署环境，不进入 Git。

## 本地构建与 systemd 部署

学习助手需要服务端 `MARKET_ATLAS_DEEPSEEK_API_KEY`，放入权限0600的环境文件。启动脚本写入服务私有 `dist/server/.dev.vars`（0600）；开发时使用被忽略的 `.dev.vars`。密钥不进入Git、发布包或客户端。不配置时课程正常阅读，助手返回503。

模型为 `deepseek-flash`，显式设置 `thinking.type=disabled`。每次问答先生成、再独立核对事实和计算，正常调用模型两次；失败最多重试一轮，总超时45秒。额度按用户问答计：每个空间每分钟6次、UTC日60次，全站每日300次；并发4，同一空间同时1次。D1保存额度计数，不保存问答正文。旧测验归档到 `learning.previousQuizResults`；新版接口拒绝旧题库版本。

需要 Node.js 22.13+ 和 npm。在开发机器的 `tools/market-atlas` 中执行 `npm ci`、`npm run build`，将同版本模块源码和完整 `dist/` 上传到服务器 `/opt/xzm-market-atlas/releases/<release-id>/`。不要上传本地 `node_modules`、数据库、环境文件或工具缓存。服务器执行 `npm ci --include=dev` 安装锁定依赖；运行时仍需要 devDependencies 中的 Wrangler，无需在服务器再次构建。

创建系统账号 `xzm-market-atlas` 与持久目录 `/var/lib/xzm-market-atlas`，让该账号拥有持久目录并能读取发布目录。将 `/opt/xzm-market-atlas/current` 符号链接指向这次发布目录，保持前一版目录以便回滚。

在服务器创建 `/etc/xzm-market-atlas.env`，权限设为 `0600`。以下内容是通用配置示例，请在服务器填写实际入口；systemd 环境文件不使用 `export`：

`MARKET_ATLAS_PUBLIC_ORIGIN` 是默认入口；可选的 `MARKET_ATLAS_PUBLIC_ORIGINS` 用逗号列出同站点的其他入口。省略列表时仅使用默认入口。Worker 根据 Nginx 覆盖的 `X-Forwarded-Proto`、`X-Forwarded-Host` 恢复公开 Origin，仅接受运维配置的列表成员；其他转发值回退默认入口。浏览器原始 `Origin` 保持原样，由原应用校验同源写入。HTTP 80 和 HTTPS 443 是默认端口，配置中省略端口；HTTPS 80 必须写 `:80`。

```ini
MARKET_ATLAS_PUBLIC_ORIGIN=https://interview.example.com:80
MARKET_ATLAS_PUBLIC_ORIGINS=https://interview.example.com:80,http://interview.example.com,https://interview.example.com
MARKET_ATLAS_HOST=127.0.0.1
MARKET_ATLAS_PORT=5174
MARKET_ATLAS_STATE_DIR=/var/lib/xzm-market-atlas/state
SITES_RUNTIME_ROOT=/var/lib/xzm-market-atlas/runtime
```

首次部署若要保留原记录，在启动前将原项目 `.wrangler/state` 的一致快照导入空的 `/var/lib/xzm-market-atlas/state`，保持原目录层级并归属 `xzm-market-atlas`。已有记录时跳过导入。制作快照时停止写入并保留仍存在的 WAL/SHM 文件；迁移前后核对文件 SHA-256。应用首次启动仅创建缺失的表，不清空已有数据。

在发布目录安装服务模板并启动：

```sh
sudo install -m 0644 deploy/market-atlas.service /etc/systemd/system/market-atlas.service
sudo systemctl daemon-reload
sudo systemctl enable --now market-atlas.service
sudo systemctl status market-atlas.service --no-pager
sudo journalctl -u market-atlas.service -n 60 --no-pager
```

systemd 管理整个进程组，异常退出后重启；D1 与工具运行状态分别保存在持久目录的 `state/` 和 `runtime/`。接入 Nginx 后按本文验证段检查页面、同源保存与已有面试助手服务。

更新前停止服务并备份整个持久目录，然后上传新的本地构建产物、安装依赖、切换 `current` 并启动服务。回滚代码只需停服务、将 `current` 指向保留版本后重新启动，持久目录继续使用。恢复数据库前先停止服务并保存当前数据快照。

## 可选 Docker 构建与配置

在服务器仓库的 `tools/market-atlas` 目录执行。部署时必须设置真实公网 Origin，包含协议及非默认端口，不包含路径或结尾斜杠；使用每次发布独立的镜像标签，保留上一个标签以便回滚。

```sh
export MARKET_ATLAS_PUBLIC_ORIGIN='https://interview.example.com:80'
export MARKET_ATLAS_PUBLIC_ORIGINS='https://interview.example.com:80,http://interview.example.com,https://interview.example.com'
export MARKET_ATLAS_IMAGE_TAG="$(git rev-parse --short HEAD)"
docker compose -f deploy/compose.yaml config --quiet
docker compose -f deploy/compose.yaml build
docker volume create xzm-market-atlas-state
```

镜像使用 Node 24，安装锁定依赖后执行 `node scripts/run-framework.mjs build`。构建上下文排除本地数据库、环境文件与 `.sites-runtime`；数据库通过独立命名卷保存，不进入镜像和 Git。

## Docker 首次迁移数据库

要完整搬入原项目的记录，在第一次启动容器前，把原项目完整 `.wrangler/state` 的一致性快照安全传到服务器。快照应已停止写入；同时保留 SQLite 主文件及仍存在的 WAL/SHM 文件，不能只复制一个正在写入的 `.sqlite` 文件。

将下方路径改成服务器上已传入的快照目录。导入命令仅允许目标卷为空，已有数据时会退出；后续发布跳过这一步。

```sh
export MARKET_ATLAS_SOURCE_STATE='/absolute/path/to/transferred/state'
docker run --rm \
  --mount "type=bind,src=$MARKET_ATLAS_SOURCE_STATE,dst=/source,readonly" \
  --mount type=volume,src=xzm-market-atlas-state,dst=/target \
  node:24-bookworm-slim sh -c \
  'test -z "$(ls -A /target)" || { echo "目标卷已有数据，拒绝覆盖"; exit 1; }; cp -a /source/. /target/'
```

启动前对照迁移前记录的 SHA-256 检查卷内主数据库及其他快照文件。原项目与容器的 Worker 配置保持同一 D1 绑定，快照目录结构必须保持原样。启动脚本将 `drizzle/0000_sharp_wallflower.sql` 转为 `CREATE TABLE IF NOT EXISTS`，仅创建缺失的表；已有表和记录保留，升级不删除或覆盖卷。原浏览器 Cookie 对应学习空间，换设备或丢失 Cookie 会打开新空间，已迁移的记录仍在数据库中。

```sh
docker compose -f deploy/compose.yaml up -d
docker compose -f deploy/compose.yaml ps
docker compose -f deploy/compose.yaml logs --tail 60 market-atlas
```

健康检查调用 `/tools/market-atlas/api/state` 并使用固定探针 Cookie，因此只使用一个探针空间。

## 接入 Nginx 与前端

先用 `nginx -T` 核实当前公网 `server`、配置位置与前端 `root`，备份这份配置及原前端文件。将 `deploy/nginx.conf` 的两个 `location` 放入各个公开入口对应的 `server`。它保留完整路径、Host 与原始 Origin，覆盖 `X-Forwarded-Proto`、`X-Forwarded-Host`，清空可由客户端伪造的平台身份头。独立 Worker 同样移除所有 `oai-authenticated-user-*` 身份头。

```sh
nginx -t
systemctl reload nginx
```

以上重载命令适用于确认由 systemd 管理 Nginx 的服务器；其他管理方式使用其实际重载命令。面试助手前端按原发布方式构建并替换实际静态目录，工具栏入口指向 `/tools/market-atlas/`。发布过程中保留上一个前端版本及镜像标签。

## 验证

先确认 systemd 服务处于 `active`，或 Compose 显示 `healthy`，再从公网检查补斜杠跳转、页面、持久化接口和原面试助手健康接口。下列命令的 `MARKET_ATLAS_PUBLIC_ORIGIN` 需在当前终端设置为待验证入口。证书验证应使用服务器实际证书信任配置。

```sh
curl -I "$MARKET_ATLAS_PUBLIC_ORIGIN/tools/market-atlas"
curl -I "$MARKET_ATLAS_PUBLIC_ORIGIN/tools/market-atlas/"
curl -H 'Cookie: market_atlas=00000000-0000-4000-8000-000000000001' \
  "$MARKET_ATLAS_PUBLIC_ORIGIN/tools/market-atlas/api/state"
curl "$MARKET_ATLAS_PUBLIC_ORIGIN/xzm/actuator/health"
```

分别从配置的公开入口在浏览器点击面试助手工具栏入口，逐项检查学习、实验、模拟交易、行情消息与复盘；新增一条笔记后刷新并重启服务或重建容器，确认记录仍存在。浏览器请求必须保持 `/tools/market-atlas/` 前缀，静态资源无 404，同源 POST 不被 Origin 校验拒绝，外域 Origin 的 POST 仍被拒绝。

## Docker 更新、备份与回滚

每次升级先停止观市容器，对命名卷做完整快照，记录旧镜像标签，再构建新标签并启动。快照保存到已核实的服务器备份目录，保持原目录结构。

```sh
docker compose -f deploy/compose.yaml stop market-atlas
export MARKET_ATLAS_BACKUP_DIR='/absolute/path/to/confirmed/backup-directory'
docker run --rm \
  --mount type=volume,src=xzm-market-atlas-state,dst=/state,readonly \
  --mount "type=bind,src=$MARKET_ATLAS_BACKUP_DIR,dst=/backup" \
  node:24-bookworm-slim tar -czf /backup/market-atlas-state.tar.gz -C /state .
```

回滚代码时将 `MARKET_ATLAS_IMAGE_TAG` 设置为保留的旧标签，执行 `docker compose -f deploy/compose.yaml up -d --no-build`，保留当前卷；恢复原前端目录及 Nginx 配置，先通过 `nginx -t` 再重载。如需回滚数据，应先停止容器并另存当前卷，再把选定快照恢复到卷中。首次接入的回滚只需恢复原前端和 Nginx 配置并停止观市容器。不要运行 `docker compose down -v`，否则会删除持久化记录。
