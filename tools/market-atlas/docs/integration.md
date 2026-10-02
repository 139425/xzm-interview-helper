# 面试助手中的观市工具

入口：面试助手 AI 对话页顶部 **工具 → 学习工具 → 观市 · 股票学习**。在新标签页打开同域 `/tools/market-atlas/`，可继续保留原对话。

## 原版保留范围

完整保留观市的五个页面、15 单元课程、深入阅读、30 道情境题、交互实验、图表、模拟交易引擎、行情与消息解读、持久化与备份。保持原有 React 19、vinext/Vite、Worker 和 D1 运行架构。外层说明和启动脚本原文存于 `docs/original-project/`；归档脚本只作来源记录，当前运行方法见下文。

`docs/source-manifest.json` 记录迁移时 140 个源文件的 SHA-256；133 个字节一致，7 个只适配子路径、Cookie 路径、反代运行入口及验证命令。`tsconfig.tsbuildinfo` 是构建缓存，不迁入 Git。依赖、构建产物、日志和数据库不提交 Git。

```sh
node scripts/verify-source.mjs
```

原始 D1 数据保留在独立快照中，部署时单独导入持久化卷。迁移时共有 3 个初始学习空间和 7 条行情/消息缓存，无已完成课程、收藏、笔记或成交记录。原浏览器身份 Cookie 属于原站点，跨域后会使用新的学习空间。

## 本地运行

需要 Node.js 22.13+。在 `tools/market-atlas` 中执行：

```sh
npm ci
npm run build
npm run start:server
```

首次启动自动用 `CREATE TABLE IF NOT EXISTS` 创建表，不清空既有数据。默认在 `127.0.0.1:5174` 运行。面试助手 `frontend` 的 Vite 开发和预览均代理 `/tools/market-atlas`；也可直接访问 `http://127.0.0.1:5174/tools/market-atlas/`。

服务器部署配置见 [部署说明](../deploy/README.md)。生产需配置真实 `MARKET_ATLAS_PUBLIC_ORIGIN`，使 HTTPS 反代后的保存校验与 Secure Cookie 正常工作。同一站点存在 HTTP 80、HTTPS 80、HTTPS 443 等多个入口时，用可选 `MARKET_ATLAS_PUBLIC_ORIGINS` 配置逗号分隔的允许列表，Nginx 必须覆盖 `X-Forwarded-Proto` 和 `X-Forwarded-Host`；原始浏览器 `Origin` 不变。

## 迁移验证

```sh
npm run typecheck
npm test
npm run test:api
# 检查通过面试助手反代的完整 API：
TEST_ORIGIN=http://127.0.0.1:5183 npm run test:api
```

API 测试使用独立 Cookie 身份，限定回环地址，不操作已有学习空间。现有面试助手测试在 `frontend` 中执行 `npm test`。
