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

产物位于 `android/app/build/outputs/apk/debug/app-debug.apk`。首次打开 App 后，在登录页右上角或“我的”页面配置 API 根地址（需包含 `/xzm`）。Android 模拟器访问本机后端可填写 `http://10.0.2.2:8104/xzm`。
