import io
import re

BASE = "app/src/main/java/com/messagekeeper/"

def patch(path, pairs, add_import=None, drop_import=None):
    p = BASE + path
    s = io.open(p, encoding="utf-8").read()
    for old, new in pairs:
        assert old in s, path + " MISSING: " + old[:80]
        s = s.replace(old, new)
    if add_import and add_import not in s:
        # insert after the last existing import line
        lines = s.split("\n")
        last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
        lines.insert(last_import + 1, add_import)
        s = "\n".join(lines)
    if drop_import:
        s = re.sub(r"^import " + re.escape(drop_import) + r"\n", "", s, flags=re.M)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    left = s.count("Toast.makeText")
    print(path, "ok, Toast.makeText left:", left)

# ---------- 1) MainActivity: overlay block -> SonnerHost ----------
old_ma = """                        val errorMsg by com.messagekeeper.ui.components.ErrorNotifier.errorFlow.collectAsState()
                        androidx.compose.animation.AnimatedVisibility(
                            visible = errorMsg != null,
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it - 50 }) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it - 50 }) + androidx.compose.animation.fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                        ) {
                            if (errorMsg != null) {
                                com.messagekeeper.ui.components.GlobalErrorToast(errorMessage = errorMsg!!)
                            }
                        }"""
new_ma = """                        // Sonner 风格堆叠提示（操作成功/失败/信息）
                        com.messagekeeper.ui.components.SonnerHost(
                            modifier = Modifier.align(Alignment.TopCenter)
                        )"""
patch("MainActivity.kt", [(old_ma, new_ma)])

# ---------- 2) MessageKeeperApp: crash handler ----------
patch("MessageKeeperApp.kt", [
    ('com.messagekeeper.ui.components.ErrorNotifier.showError("致命错误: ${throwable.message ?: "未知异常"}")',
     'com.messagekeeper.ui.components.Sonner.error("致命错误: ${throwable.message ?: "未知异常"}")'),
])

# ---------- 3) SettingsScreen: toggle+preview -> sonner preview; toasts -> Sonner ----------
old_st = """            val prefs = remember { context.getSharedPreferences("message_keeper_prefs", Context.MODE_PRIVATE) }
            var showErrorToast by remember { mutableStateOf(prefs.getBoolean("show_error_toast", true)) }

            SettingsItem(
                key = "显示严重错误弹窗",
                value = if (showErrorToast) "已开启" else "已关闭",
                description = "当应用发生严重错误时在屏幕顶部显示弹窗",
                onClick = {
                    showErrorToast = !showErrorToast
                    prefs.edit().putBoolean("show_error_toast", showErrorToast).apply()
                },
                valueColor = if (showErrorToast) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsItem(
                key = "预览错误弹窗",
                value = "点击预览",
                description = "预览顶部严重错误弹窗的展示效果（3秒后自动关闭）",
                onClick = {
                    com.messagekeeper.ui.components.ErrorNotifier.showError("致命错误: 这是一条用于预览的严重错误测试信息")
                },
                valueColor = AccentOrange
            )"""
new_st = """            SettingsItem(
                key = "预览提示弹窗",
                value = "点击预览",
                description = "预览堆叠式提示的三种形态：成功 / 失败 / 信息",
                onClick = {
                    com.messagekeeper.ui.components.Sonner.success("操作已成功完成")
                    com.messagekeeper.ui.components.Sonner.error("操作失败，请稍后重试")
                    com.messagekeeper.ui.components.Sonner.info("这是一条普通信息提示")
                },
                valueColor = AccentOrange
            )"""

def t(kind, msg):
    old = 'Toast.makeText(\n                        context, "%s", Toast.LENGTH_SHORT\n                    ).show()' % msg
    new = 'Sonner.%s("%s")' % (kind, msg)
    return (old, new)

pairs_st = [
    (old_st, new_st),
    ('Toast.makeText(\n                        context, "请在已下载的应用中找到 MessageKeeper 并开启", Toast.LENGTH_LONG\n                    ).show()',
     'Sonner.info("请在已下载的应用中找到 MessageKeeper 并开启")'),
    ('Toast.makeText(context, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()',
     'Sonner.error("无法打开无障碍设置")'),
    ('Toast.makeText(context, "请在设置中允许忽略电池优化", Toast.LENGTH_LONG).show()',
     'Sonner.info("请在设置中允许忽略电池优化")'),
    ('Toast.makeText(context, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()',
     'Sonner.error("无法打开文件: ${e.message}")'),
    ('Toast.makeText(context, "无法分享文件: ${e.message}", Toast.LENGTH_SHORT).show()',
     'Sonner.error("无法分享文件: ${e.message}")'),
    ('Toast.makeText(context, "正在导出 txt...", Toast.LENGTH_SHORT).show()',
     'Sonner.info("正在导出 txt...")'),
    ('Toast.makeText(context, "正在导出 html...", Toast.LENGTH_SHORT).show()',
     'Sonner.info("正在导出 html...")'),
    ('Toast.makeText(context, "数据已清空", Toast.LENGTH_SHORT).show()',
     'Sonner.success("数据已清空")'),
    ('Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()',
     'Sonner.success("日志已复制到剪贴板")'),
]
patch("ui/screens/settings/SettingsScreen.kt", pairs_st,
      add_import="import com.messagekeeper.ui.components.Sonner",
      drop_import="android.widget.Toast")

# ---------- 4) SpecialAlertScreen ----------
pairs_sa = [
    ('Toast.makeText(context, "已发送测试通知", Toast.LENGTH_SHORT).show()', 'Sonner.success("已发送测试通知")'),
    ('Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()', 'Sonner.success("规则已删除")'),
    ('Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()', 'Sonner.success("规则已保存")'),
    ('Toast.makeText(context, "已更新声音", Toast.LENGTH_SHORT).show()', 'Sonner.success("已更新声音")'),
]
patch("ui/screens/specialalert/SpecialAlertScreen.kt", pairs_sa,
      add_import="import com.messagekeeper.ui.components.Sonner",
      drop_import="android.widget.Toast")

# ---------- 5) ChatScreen ----------
patch("ui/screens/chat/ChatScreen.kt", [
    ('Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()', 'Sonner.success("已复制到剪贴板")'),
], add_import="import com.messagekeeper.ui.components.Sonner",
   drop_import="android.widget.Toast")

print("ALL DONE")
