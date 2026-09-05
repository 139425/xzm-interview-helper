# XZM 面试助手 Mobile

独立的 Android 移动端客户端，复用现有 Spring Boot API，包含登录注册、AI 对话、笔面测待办、投递追踪与秋招信息。

## 本地开发

```bash
npm install
npm run dev
```

## 构建 Android APK

需准备 JDK 21 与 Android SDK：

```bash
npm run android:apk
```

产物位于 `android/app/build/outputs/apk/debug/app-debug.apk`。App 默认连接 `http://120.48.47.80:8104/xzm`，首次打开无需填写 API 地址；“我的”页面仍可按需切换到其他后端。
