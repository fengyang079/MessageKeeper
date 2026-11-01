package com.messagekeeper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.BgHover
import com.messagekeeper.ui.theme.getAvatarColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onLongClick = onLongClick,
                        onClick = {}
                    )
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        if (message.is_recalled) {
            RecalledMessageBubble(message)
        } else {
            NormalMessageBubble(message)
        }
    }
}

@Composable
private fun NormalMessageBubble(message: Message) {
    val senderColor = getAvatarColor(message.sender_name.hashCode())

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 发送者 + 时间（终端风格）─────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 3px 彩色竖线代替头像
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(senderColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.sender_name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = senderColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 消息内容（无气泡，纯文字，终端风格）──────────────
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 11.dp) // 与竖线对齐
        )

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun RecalledMessageBubble(message: Message) {
    val senderColor = getAvatarColor(message.sender_name.hashCode())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(com.messagekeeper.ui.theme.AccentRedDim)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // ── 发送者 + 时间 + [已撤回] 标签 ─────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(com.messagekeeper.ui.theme.AccentRed)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.sender_name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = senderColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(com.messagekeeper.ui.theme.AccentRed.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "[已撤回]",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.messagekeeper.ui.theme.AccentRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 原始被撤回的消息内容（清晰展现防撤回核心功能）──────────────
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 11.dp)
        )
    }
}

private val chatTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }

private fun formatTime(timestamp: Long): String {
    return (chatTimeFormat.get() ?: SimpleDateFormat("HH:mm", Locale.getDefault())).format(Date(timestamp))
}
