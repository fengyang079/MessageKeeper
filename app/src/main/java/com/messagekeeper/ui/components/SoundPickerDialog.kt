package com.messagekeeper.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagekeeper.data.db.entity.SoundEffects
import com.messagekeeper.notification.CustomSoundManager
import com.messagekeeper.notification.SpecialAlertManager
import com.messagekeeper.ui.theme.AccentOrange

/**
 * 声音选择器弹窗
 * 展示所有可选声音（含内置与用户自定义上传），支持试听与上传
 */
@Composable
fun SoundPickerDialog(
    currentSound: String,
    onDismiss: () -> Unit,
    onSoundSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var playingSound by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var customSounds by remember { mutableStateOf(CustomSoundManager.getCustomSounds(context)) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val newSound = CustomSoundManager.addCustomSound(context, uri, fileName)
            if (newSound != null) {
                customSounds = CustomSoundManager.getCustomSounds(context)
                onSoundSelected(newSound.id)
            }
        }
    }

    // 组件销毁时停止播放
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    AlertDialog(
        onDismissRequest = {
            mediaPlayer?.release()
            onDismiss()
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "选择专属提示音",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "支持内置音频与本地铃声上传",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "上传",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上传铃声", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 内置声音列表
                items(SoundEffects.sounds) { sound ->
                    val isSelected = sound.name == currentSound
                    val isPlaying = playingSound == sound.name

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                mediaPlayer?.release()
                                mediaPlayer = null
                                playingSound = null
                                onSoundSelected(sound.name)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) AccentOrange.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AccentOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sound.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (sound.name) {
                                        "default" -> "系统默认通知提示音"
                                        "silent" -> "静音（仅视觉通知）"
                                        else -> "内置专属语音 / 铃声"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }

                            if (sound.name != "default" && sound.name != "silent") {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            mediaPlayer?.release()
                                            mediaPlayer = null
                                            playingSound = null
                                        } else {
                                            mediaPlayer?.release()
                                            val player = SpecialAlertManager.playPreview(context, sound.name)
                                            mediaPlayer = player
                                            playingSound = sound.name
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "停止" else "试听",
                                        tint = if (isPlaying) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 自定义上传声音列表
                if (customSounds.isNotEmpty()) {
                    item {
                        Text(
                            text = "自定义上传铃声",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }

                    items(customSounds, key = { it.id }) { sound ->
                        val isSelected = sound.id == currentSound
                        val isPlaying = playingSound == sound.id

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    playingSound = null
                                    onSoundSelected(sound.id)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) AccentOrange.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AccentOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sound.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "本地上传铃声",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentOrange,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            mediaPlayer?.release()
                                            mediaPlayer = null
                                            playingSound = null
                                        } else {
                                            mediaPlayer?.release()
                                            val player = SpecialAlertManager.playPreview(context, sound.id)
                                            mediaPlayer = player
                                            playingSound = sound.id
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "停止" else "试听",
                                        tint = if (isPlaying) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        CustomSoundManager.deleteCustomSound(context, sound.id)
                                        customSounds = CustomSoundManager.getCustomSounds(context)
                                        if (currentSound == sound.id) {
                                            onSoundSelected("ganlu")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                mediaPlayer?.release()
                onDismiss()
            }) {
                Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
