package com.messagekeeper.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.ui.theme.getAvatarColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarColor = getAvatarColor(conversation.display_name.hashCode())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.display_name.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = avatarColor
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.display_name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.is_pinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "已置顶",
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTime(conversation.last_message_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Platform.displayName(conversation.platform),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = conversation.last_message.ifEmpty { "暂无消息" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "会话操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private val monthDayFormat = ThreadLocal.withInitial { SimpleDateFormat("M月d日", Locale.getDefault()) }
private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy/M/d", Locale.getDefault()) }

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    val now = Date()
    val dayFormat = dateFormat.get() ?: SimpleDateFormat("yyyy/M/d", Locale.getDefault())
    return if (dayFormat.format(date) == dayFormat.format(now)) {
        (timeFormat.get() ?: SimpleDateFormat("HH:mm", Locale.getDefault())).format(date)
    } else {
        (monthDayFormat.get() ?: SimpleDateFormat("M月d日", Locale.getDefault())).format(date)
    }
}
