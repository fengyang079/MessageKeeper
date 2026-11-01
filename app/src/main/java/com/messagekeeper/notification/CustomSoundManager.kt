package com.messagekeeper.notification

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CustomSound(
    val id: String,
    val displayName: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis()
)

object CustomSoundManager {

    private const val TAG = "CustomSoundManager"
    private const val PREF_NAME = "custom_sounds_pref"
    private const val KEY_SOUNDS = "sounds_json"

    /**
     * 获取已上传的自定义声音列表
     */
    fun getCustomSounds(context: Context): List<CustomSound> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = sp.getString(KEY_SOUNDS, null) ?: return emptyList()
        val list = mutableListOf<CustomSound>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val file = File(obj.optString("filePath"))
                if (file.exists()) {
                    list.add(
                        CustomSound(
                            id = obj.optString("id"),
                            displayName = obj.optString("displayName"),
                            filePath = obj.optString("filePath"),
                            addedAt = obj.optLong("addedAt")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析自定义音频列表失败", e)
        }
        return list
    }

    /**
     * 保存从 Uri 导入的音频文件
     */
    fun addCustomSound(context: Context, uri: Uri, originalName: String?): CustomSound? {
        return try {
            val soundDir = File(context.filesDir, "custom_sounds").apply { if (!exists()) mkdirs() }
            val cleanName = originalName?.replace(Regex("[^a-zA-Z0-9_\\.\\-\\u4e00-\\u9fa5]"), "_") ?: "custom_${System.currentTimeMillis()}.mp3"
            val targetFile = File(soundDir, "${System.currentTimeMillis()}_$cleanName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val displayName = "🎵 " + (originalName?.substringBeforeLast('.') ?: "自定义音频")
            val id = "custom_" + targetFile.nameWithoutExtension

            val sound = CustomSound(
                id = id,
                displayName = displayName,
                filePath = targetFile.absolutePath
            )

            val currentSounds = getCustomSounds(context).toMutableList()
            currentSounds.add(sound)
            saveCustomSounds(context, currentSounds)

            Log.d(TAG, "成功保存自定义音频: ${sound.displayName} -> ${sound.filePath}")
            sound
        } catch (e: Exception) {
            Log.e(TAG, "导入自定义音频失败", e)
            null
        }
    }

    /**
     * 删除自定义音频
     */
    fun deleteCustomSound(context: Context, soundId: String) {
        val currentSounds = getCustomSounds(context).toMutableList()
        val target = currentSounds.find { it.id == soundId }
        if (target != null) {
            try {
                File(target.filePath).delete()
            } catch (e: Exception) {
                Log.w(TAG, "删除音频文件失败: ${target.filePath}")
            }
            currentSounds.removeAll { it.id == soundId }
            saveCustomSounds(context, currentSounds)
        }
    }

    private fun saveCustomSounds(context: Context, sounds: List<CustomSound>) {
        val array = JSONArray()
        for (s in sounds) {
            array.put(JSONObject().apply {
                put("id", s.id)
                put("displayName", s.displayName)
                put("filePath", s.filePath)
                put("addedAt", s.addedAt)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUNDS, array.toString())
            .apply()
    }
}
