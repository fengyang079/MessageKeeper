# 消息助手 ProGuard 规则

# Keep Room entities
-keep class com.messagekeeper.data.db.entity.** { *; }

# Keep service classes
-keep class com.messagekeeper.service.** { *; }

# Keep notification classes
-keep class com.messagekeeper.notification.** { *; }

# Keep Application class
-keep class com.messagekeeper.MessageKeeperApp { *; }
