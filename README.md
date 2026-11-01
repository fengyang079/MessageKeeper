# MessageKeeper 📟

**MessageKeeper** 是一款专为极客设计的 Android 消息拦截与分析终端。不仅能完美拦截并保存微信、QQ 等主流社交软件的通知消息（实现防撤回），还内置了酷炫的字符风情报分析面板和强大的特别关心唤醒机制。

![Terminal UI](https://img.shields.io/badge/UI-Terminal_Hacker-black?style=for-the-badge&logo=gnu-terminal) ![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-blue?style=for-the-badge&logo=kotlin) ![Room](https://img.shields.io/badge/Database-Room_SQLite-green?style=for-the-badge&logo=sqlite)

## ✨ 核心特性 (Features)

*   **🛡️ 绝对防撤回 (Anti-Recall)**
    *   基于 `NotificationListenerService` 实时捕获系统状态栏通知。
    *   哪怕对方光速撤回，消息依然永久保存在本地 SQLite 数据库中。
    *   智能去重与自动合并算法：完美兼容带有 `(X条新消息)` 后缀的杂乱通知，保持对话列表纯净。
*   **💻 极客终端 UI (Geek Terminal UI)**
    *   全应用采用 Jetpack Compose 构建，纯黑客命令行交互风格。
    *   丢掉花哨的 Material 动画，享受 `root@message-keeper:~$` 的纯粹硬核沉浸感。
*   **📊 情报分析面板 (Analytics Dashboard)**
    *   基于底层 SQLite 聚合查询，超低功耗生成你的社交情报。
    *   纯手绘 ASCII 字符进度条展示**话痨排行榜 (Top Contacts)** 和 **24小时热度分布 (Activity Heatmap)**。
*   **🚨 强力特别关心 (Special Alerts)**
    *   针对设定的特别关心联系人，哪怕手机静音，也能直接获取 `WAKE_LOCK` 点亮屏幕、持续震动并播放高优警报，绝不错过重要工作与消息。
*   **💾 数据主权与导出 (Data Export)**
    *   支持一键将本地数据库中的所有聊天记录渲染为精美的纯静态 HTML 网页文件，方便脱机永久归档保存。
*   **📱 手机间迁移 (Phone Transfer)**
    *   内置配对码机制，支持在局域网内把全部聊天记录一键迁移到新手机（接收端开服务、发送端输 IP + 配对码）。

## 🏗️ 核心架构 (Architecture)

1.  **拦截层 (Interceptor):** `MessageNotificationListener` 是系统级服务，负责在后台无声息地捕获一切目标包名的通知。
2.  **解析层 (Parser):** `NotificationParser` 是一套强健的正则解析引擎，负责剥离无用前缀、兼容全角/半角符号，提取出干净的联系人、群组和内容。
3.  **持久层 (Repository):** 采用 Room 框架，通过 `MessageRepository` 统一管理会话 (`Conversation`) 与消息 (`Message`)，并在冷启动时自动愈合、合并受污染的历史数据。
4.  **展现层 (UI):** 基于 MVI 架构，`ViewModel` 通过 StateFlow 将数据推送到各个终端风 Compose 页面。

## 🚀 编译与安装 (Build & Install)

本项目基于标准的 Android Gradle 体系，推荐使用 Android Studio 打开：

```bash
# 克隆仓库
git clone https://github.com/yourusername/MessageKeeper.git
cd MessageKeeper

# 编译 Debug APK
./gradlew app:assembleDebug
```

> **注意：** 安装后，请务必在系统设置中授予本应用 **“读取通知” (Notification Access)** 权限，否则无法捕获任何消息！对于强提醒功能，可能还需要授予“允许后台弹窗”及“无限制耗电”权限。

## ⚠️ 免责声明 (Disclaimer)

本项目仅供 Android 逆向学习、Compose 界面设计交流及个人数据备份之用。请勿用于侵犯他人隐私或任何非法用途。

---
*Created with ❤️ & 💻 by Geek*
