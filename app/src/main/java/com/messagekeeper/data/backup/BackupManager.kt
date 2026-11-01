package com.messagekeeper.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.messagekeeper.MessageKeeperApp
import com.messagekeeper.data.prefs.RetentionPreferences
import com.messagekeeper.data.repository.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 本地备份管理器：备份的创建 / 列举 / 读取 / 删除 / 回退 / 合并，
 * 以及「每天最多一次」的自动备份与历史消息自动清理。
 *
 * 存储位置（App 专属外部目录，卸载随 App 一起清除，无需任何存储权限）：
 * ```
 * getExternalFilesDir(null)/backups/
 *   backup_<kind>_<yyyyMMdd_HHmmss>.json.gz        备份内容（exportBackupJson() 的 JSON，GZIP 压缩）
 *   backup_<kind>_<yyyyMMdd_HHmmss>.json.gz.meta   备份元数据（org.json）
 * ```
 *
 * 约定：
 * - 所有对外 suspend 方法内部自行切到 [Dispatchers.IO]，并用 [opMutex] 串行化，
 *   避免"备份写到一半被 prune 删掉"这类并发问题；
 * - 任何失败都不会抛给调用方：读取/列举失败返回 null / 空列表，回退/合并返回 Result.failure，
 *   创建备份失败返回 null（同时打日志）；
 * - 回退（restore）是破坏性操作：**先创建 prerestore 保护性备份，成功后才允许替换数据**。
 */
object BackupManager {

    private const val TAG = "BackupManager"

    // ==================== 常量 ====================

    private const val PREFS_NAME = "message_keeper_backup_prefs"
    private const val KEY_LAST_AUTO_BACKUP_DATE = "last_auto_backup_date"
    private const val KEY_LAST_RETENTION_DATE = "last_retention_date"

    private const val BACKUP_DIR_NAME = "backups"
    private const val GZ_SUFFIX = ".json.gz"
    private const val META_SUFFIX = ".meta"
    private const val TEMP_SUFFIX = ".tmp"

    private const val FILE_NAME_PATTERN = "yyyyMMdd_HHmmss"
    private const val DAY_PATTERN = "yyyy-MM-dd"

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private const val DEFAULT_MAX_BACKUP_COUNT = 20
    private const val DEFAULT_MAX_BACKUP_AGE_DAYS = 30
    private const val MAX_SAME_SECOND_BACKUPS = 1000

    // 备份类型（既是 BackupInfo.kind，也用于文件名，只允许这三个值）
    private const val KIND_AUTO = "auto"
    private const val KIND_MANUAL = "manual"
    private const val KIND_PRERESTORE = "prerestore"

    // ==================== 数据模型 ====================

    /**
     * 一条备份的描述信息（由 .meta 解析而来；meta 缺失/损坏时回退到文件信息）。
     */
    data class BackupInfo(
        val fileName: String,
        val createdAt: Long,
        /** "auto" / "manual" / "prerestore" */
        val kind: String,
        val conversations: Int,
        val messages: Int,
        val sizeBytes: Long
    )

    // ==================== 状态 ====================

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 备份文件操作串行锁：防止并发创建 / 清理 / 回退互相踩踏（Mutex 不可重入，内部实现不再取同一把锁） */
    private val opMutex = Mutex()

    private val repository: MessageRepository
        get() = MessageRepository.getInstance()

    // ==================== 初始化 ====================

    /**
     * 幂等初始化。只做 SharedPreferences 级别的轻量初始化，不触碰磁盘目录，
     * 因此在 Application.onCreate 主线程调用也不会拖慢启动。
     */
    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            val ctx = context.applicationContext
            appContext = ctx
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "初始化完成")
        }
    }

    /** 取 Context：优先 init 注入的，其次从 Application 单例兜底（都拿不到则返回 null） */
    private fun ensureContext(): Context? {
        appContext?.let { return it }
        val ctx = runCatching { MessageKeeperApp.instance.applicationContext }.getOrNull() ?: return null
        init(ctx)
        return appContext
    }

    private fun ensurePrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        val loaded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = loaded
        return loaded
    }

    // ==================== 对外 API：创建 / 列举 / 读取 / 删除 / 清理 ====================

    /**
     * 创建一份备份（默认手动）。失败返回 null 并打日志，不抛异常。
     *
     * 流程：导出 JSON → 统计会话/消息数 → GZIP 写临时文件后改名 → 写 .meta → 清理旧备份。
     */
    suspend fun createBackup(kind: String = KIND_MANUAL): BackupInfo? = withContext(Dispatchers.IO) {
        try {
            opMutex.withLock { createBackupInternal(kind) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "创建备份失败 kind=$kind", e)
            null
        }
    }

    /**
     * 每天最多一份自动备份：当天已经备份过（prefs 日期标记或已存在当天 auto 备份）则返回 null。
     */
    suspend fun ensureDailyBackupIfDue(): BackupInfo? = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: run {
                Log.e(TAG, "每日自动备份失败：BackupManager 未初始化（缺少 Context）")
                return@withContext null
            }
            opMutex.withLock { ensureDailyBackupIfDueLocked(context) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "每日自动备份异常", e)
            null
        }
    }

    /** 按 createdAt 倒序列出全部备份；目录不存在或读取失败时返回空列表 */
    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: return@withContext emptyList()
            opMutex.withLock { listBackupsInternal(context) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取备份列表失败", e)
            emptyList()
        }
    }

    /** 读取并解压指定备份的 JSON 内容；文件不存在 / 名称非法 / 解压失败返回 null */
    suspend fun readBackupJson(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: return@withContext null
            opMutex.withLock { readBackupJsonInternal(context, fileName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取备份失败: $fileName", e)
            null
        }
    }

    /** 删除指定备份（含 .meta）；文件本就不存在时视为成功 */
    suspend fun deleteBackup(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: return@withContext false
            opMutex.withLock { deleteBackupInternal(context, fileName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "删除备份失败: $fileName", e)
            false
        }
    }

    /**
     * 按策略清理旧备份：
     * 1) createdAt 超过 [maxAgeDays] 天的删除（maxAgeDays <= 0 表示不按年龄清理）；
     * 2) 剩余数量超过 [maxCount] 时，从最旧的开始删（maxCount <= 0 表示不按数量清理）。
     */
    suspend fun pruneOldBackups(
        maxCount: Int = DEFAULT_MAX_BACKUP_COUNT,
        maxAgeDays: Int = DEFAULT_MAX_BACKUP_AGE_DAYS
    ): Unit = withContext(Dispatchers.IO) {
            try {
                val context = ensureContext() ?: return@withContext
                opMutex.withLock { pruneOldBackupsInternal(context, maxCount, maxAgeDays) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "清理旧备份失败", e)
            }
        }

    // ==================== 对外 API：回退 / 合并 ====================

    /**
     * 回退到指定备份（**破坏性**：整体替换本地 messages / conversations，special_alerts 保留）。
     *
     * 安全红线：必须先成功创建 prerestore 保护性备份，才会执行替换；
     * 替换在单个 Room 事务内完成，失败整体回滚。
     *
     * @return Result.success(实际导入的消息条数) 或 Result.failure(原因)
     */
    suspend fun restoreFromBackup(fileName: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: run {
                Log.e(TAG, "回退失败：BackupManager 未初始化（缺少 Context）")
                return@withContext Result.failure<Int>(IllegalStateException("BackupManager 未初始化"))
            }
            opMutex.withLock { restoreFromBackupLocked(context, fileName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "回退失败: $fileName", e)
            Result.failure<Int>(e)
        }
    }

    /**
     * 把备份合并进当前数据（**非破坏性**）：直接复用
     * [MessageRepository.importBackupJson] 的合并 + 去重语义，不删除任何本地数据。
     *
     * @return Result.success(实际新增的消息条数) 或 Result.failure(原因)
     */
    suspend fun mergeFromBackup(fileName: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: run {
                Log.e(TAG, "合并失败：BackupManager 未初始化（缺少 Context）")
                return@withContext Result.failure<Int>(IllegalStateException("BackupManager 未初始化"))
            }
            opMutex.withLock { mergeFromBackupLocked(context, fileName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "合并失败: $fileName", e)
            Result.failure<Int>(e)
        }
    }

    // ==================== 对外 API：每日维护（内部触发，UI 无需调用） ====================

    /**
     * 每天最多执行一次的历史备份自动清理：
     * 读取 [RetentionPreferences.getRetentionDays]，0 表示关闭；
     * 按设定的保留天数清理过期的备份文件（不再删除数据库中的消息）。
     *
     * @return 实际删除的备份文件份数（跳过 / 失败时为 0）
     */
    suspend fun runRetentionIfDue(): Int = withContext(Dispatchers.IO) {
        try {
            val context = ensureContext() ?: run {
                Log.e(TAG, "自动清理失败：BackupManager 未初始化（缺少 Context）")
                return@withContext 0
            }
            opMutex.withLock { runRetentionIfDueLocked(context) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "自动清理异常", e)
            0
        }
    }

    // ==================== 内部实现（都在 opMutex 保护下执行） ====================

    private suspend fun createBackupInternal(kind: String): BackupInfo? {
        val context = ensureContext() ?: run {
            Log.e(TAG, "创建备份失败：BackupManager 未初始化（缺少 Context）")
            return null
        }
        val repo = repository

        // 1. 导出全量 JSON（已有实现，含 special_alerts 之外的会话与消息）
        val jsonStr = repo.exportBackupJson()
        if (jsonStr.isBlank()) {
            Log.e(TAG, "创建备份失败：导出的 JSON 为空")
            return null
        }

        // 2. 统计条目数（用 DAO 计数，无需再次解析大 JSON）
        val conversationCount = repo.getAllConversationsSync().size
        val messageCount = repo.getMessageCount()

        // 3. 准备目录与文件名
        val dir = backupDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.e(TAG, "创建备份失败：无法创建备份目录 ${dir.absolutePath}")
            return null
        }
        val now = System.currentTimeMillis()
        val safeKind = normalizeKind(kind)
        val fileName = uniqueBackupFileName(dir, safeKind, now)
        val gzFile = File(dir, fileName)
        val metaFile = File(dir, "$fileName$META_SUFFIX")
        val tempFile = File(dir, "$fileName$TEMP_SUFFIX")

        // 4. GZIP 压缩：先写临时文件再改名，避免中途失败留下半截 .json.gz 被当成有效备份
        try {
            GZIPOutputStream(FileOutputStream(tempFile)).use { gzip ->
                gzip.write(jsonStr.toByteArray(Charsets.UTF_8))
            }
            if (!tempFile.renameTo(gzFile)) {
                throw IOException("备份临时文件改名失败: ${tempFile.absolutePath}")
            }
        } catch (e: Exception) {
            tempFile.delete()
            gzFile.delete()
            throw e
        }

        // 5. 写元数据（fileName/createdAt/kind/conversations/messages/sizeBytes）
        val sizeBytes = gzFile.length()
        val metaJson = JSONObject().apply {
            put("fileName", fileName)
            put("createdAt", now)
            put("kind", safeKind)
            put("conversations", conversationCount)
            put("messages", messageCount)
            put("sizeBytes", sizeBytes)
        }
        metaFile.writeText(metaJson.toString(2), Charsets.UTF_8)

        Log.i(
            TAG,
            "备份完成: kind=$safeKind, file=$fileName, 会话=$conversationCount, " +
                "消息=$messageCount, 大小=${sizeBytes}B"
        )

        // 6. 备份成功后按策略清理过期 / 超额备份
        RetentionPreferences.init(context)
        val retentionDays = RetentionPreferences.getRetentionDays()
        pruneOldBackupsInternal(context, DEFAULT_MAX_BACKUP_COUNT, retentionDays)

        return BackupInfo(
            fileName = fileName,
            createdAt = now,
            kind = safeKind,
            conversations = conversationCount,
            messages = messageCount,
            sizeBytes = sizeBytes
        )
    }

    private suspend fun ensureDailyBackupIfDueLocked(context: Context): BackupInfo? {
        val backupPrefs = ensurePrefs(context)
        val today = dayStamp(System.currentTimeMillis())

        if (backupPrefs.getString(KEY_LAST_AUTO_BACKUP_DATE, null) == today) {
            Log.d(TAG, "今日($today)已有自动备份，跳过")
            return null
        }

        // 双保险：即使日期标记丢失，只要今天已存在 auto 备份也不再重复生成
        val existingToday = listBackupsInternal(context).firstOrNull {
            it.kind == KIND_AUTO && dayStamp(it.createdAt) == today
        }
        if (existingToday != null) {
            backupPrefs.edit().putString(KEY_LAST_AUTO_BACKUP_DATE, today).apply()
            Log.d(TAG, "今日($today)已存在自动备份 ${existingToday.fileName}，补记日期标记后跳过")
            return null
        }

        val info = createBackupInternal(KIND_AUTO)
        if (info == null) {
            Log.w(TAG, "每日自动备份失败，等待下次心跳重试")
            return null
        }
        backupPrefs.edit().putString(KEY_LAST_AUTO_BACKUP_DATE, today).commit()
        Log.i(TAG, "每日自动备份完成: ${info.fileName}")
        return info
    }

    private suspend fun runRetentionIfDueLocked(context: Context): Int {
        // 兜底初始化，保证读到的是用户在设置页选的档位（未初始化时默认 30 天）
        RetentionPreferences.init(context)
        val retentionDays = RetentionPreferences.getRetentionDays()
        if (retentionDays <= 0) {
            Log.d(TAG, "历史备份自动清理已关闭(retentionDays=0)，跳过")
            return 0
        }

        val backupPrefs = ensurePrefs(context)
        val today = dayStamp(System.currentTimeMillis())
        if (backupPrefs.getString(KEY_LAST_RETENTION_DATE, null) == today) {
            Log.d(TAG, "今日($today)已执行过备份自动清理，跳过")
            return 0
        }

        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * DAY_MS
        Log.w(
            TAG,
            "开始自动清理过期备份：保留 $retentionDays 天，cutoff=$cutoff（" +
                SimpleDateFormat(DAY_PATTERN, Locale.US).format(Date(cutoff)) + " 之前的备份将被删除，数据库消息不受影响）"
        )
        val deleted = pruneOldBackupsInternal(context, DEFAULT_MAX_BACKUP_COUNT, retentionDays)
        backupPrefs.edit().putString(KEY_LAST_RETENTION_DATE, today).commit()
        Log.i(TAG, "每日备份自动清理完成：保留 $retentionDays 天，共删除备份 $deleted 份")
        return deleted
    }

    private suspend fun restoreFromBackupLocked(context: Context, fileName: String): Result<Int> {
        // 1. 读取并校验备份内容：宁可拒绝回退，也不要清空数据后才发现备份损坏
        val jsonStr = readBackupJsonInternal(context, fileName)
            ?: return Result.failure<Int>(IOException("备份不存在或无法读取: $fileName"))
        if (!isValidBackupJson(jsonStr)) {
            return Result.failure<Int>(IllegalArgumentException("备份内容不是合法的备份 JSON: $fileName"))
        }

        // 2. 安全红线：回退前必须先成功创建一份当前数据的保护性备份
        Log.w(TAG, "⚠️ 开始回退到备份: $fileName（先创建 prerestore 保护性备份）")
        val preRestore = createBackupInternal(KIND_PRERESTORE)
            ?: return Result.failure<Int>(IllegalStateException("回退前自动备份失败，已中止回退（本地数据未改动）"))
        Log.i(TAG, "回退前保护性备份已创建: ${preRestore.fileName}")

        // 3. 整体替换（单事务，失败自动回滚）
        val imported = repository.replaceWithBackupJson(jsonStr)
        Log.i(
            TAG,
            "✅ 回退完成: $fileName → 导入 $imported 条消息（回退前数据见 ${preRestore.fileName}）"
        )
        return Result.success(imported)
    }

    private suspend fun mergeFromBackupLocked(context: Context, fileName: String): Result<Int> {
        val jsonStr = readBackupJsonInternal(context, fileName)
            ?: return Result.failure<Int>(IOException("备份不存在或无法读取: $fileName"))
        if (!isValidBackupJson(jsonStr)) {
            return Result.failure<Int>(IllegalArgumentException("备份内容不是合法的备份 JSON: $fileName"))
        }

        Log.i(TAG, "开始合并备份: $fileName（只补本地没有的消息，不删除本地数据）")
        val imported = repository.importBackupJson(jsonStr)
        Log.i(TAG, "✅ 合并完成: $fileName → 新增 $imported 条消息")
        return Result.success(imported)
    }

    private fun listBackupsInternal(context: Context): List<BackupInfo> {
        val dir = backupDir(context)
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(GZ_SUFFIX) }
            ?: return emptyList()
        val result = ArrayList<BackupInfo>(files.size)
        for (gzFile in files) {
            val info = readBackupInfo(gzFile) ?: continue
            result.add(info)
        }
        result.sortWith(compareByDescending { it.createdAt })
        return result
    }

    private fun readBackupInfo(gzFile: File): BackupInfo? {
        if (!gzFile.isFile) return null
        val fallback = BackupInfo(
            fileName = gzFile.name,
            createdAt = gzFile.lastModified(),
            kind = kindFromFileName(gzFile.name),
            conversations = 0,
            messages = 0,
            sizeBytes = gzFile.length()
        )
        val metaFile = File(gzFile.parentFile, "${gzFile.name}$META_SUFFIX")
        if (!metaFile.isFile) return fallback
        return try {
            val meta = JSONObject(metaFile.readText(Charsets.UTF_8))
            BackupInfo(
                // fileName 始终以磁盘上的真实文件名为准，保证列表项一定能被读取/删除
                fileName = gzFile.name,
                createdAt = meta.optLong("createdAt", fallback.createdAt),
                kind = meta.optString("kind", fallback.kind).ifBlank { fallback.kind },
                conversations = meta.optInt("conversations", 0),
                messages = meta.optInt("messages", 0),
                sizeBytes = meta.optLong("sizeBytes", fallback.sizeBytes)
            )
        } catch (e: Exception) {
            Log.w(TAG, "读取备份元数据失败，回退到文件信息: ${metaFile.name}", e)
            fallback
        }
    }

    private fun readBackupJsonInternal(context: Context, fileName: String): String? {
        val gzFile = resolveBackupFile(context, fileName) ?: run {
            Log.w(TAG, "非法的备份文件名，拒绝读取: $fileName")
            return null
        }
        if (!gzFile.isFile) {
            Log.w(TAG, "备份文件不存在: ${gzFile.absolutePath}")
            return null
        }
        return try {
            GZIPInputStream(FileInputStream(gzFile)).use { gzip ->
                gzip.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解压/读取备份失败: $fileName", e)
            null
        }
    }

    private fun deleteBackupInternal(context: Context, fileName: String): Boolean {
        val gzFile = resolveBackupFile(context, fileName) ?: run {
            Log.w(TAG, "非法的备份文件名，拒绝删除: $fileName")
            return false
        }
        val existed = gzFile.isFile
        val deleted = !existed || gzFile.delete()
        val metaFile = File(gzFile.parentFile, "${gzFile.name}$META_SUFFIX")
        if (metaFile.isFile && !metaFile.delete()) {
            Log.w(TAG, "备份元数据删除失败: ${metaFile.absolutePath}")
        }
        if (existed && !deleted) {
            Log.e(TAG, "备份文件删除失败: ${gzFile.absolutePath}")
        }
        return deleted
    }

    private fun pruneOldBackupsInternal(context: Context, maxCount: Int, maxAgeDays: Int): Int {
        val dir = backupDir(context)
        if (!dir.isDirectory) return 0
        cleanStaleTempFiles(dir)

        val backups = listBackupsInternal(context)
        if (backups.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var deletedCount = 0

        // backups 已按 createdAt 倒序（最新在前）：先按年龄删，再按数量删
        val survivors = ArrayList<BackupInfo>(backups.size)
        for (info in backups) {
            val expired = maxAgeDays > 0 && now - info.createdAt > maxAgeDays.toLong() * DAY_MS
            if (expired) {
                if (deleteBackupInternal(context, info.fileName)) {
                    deletedCount++
                    Log.i(TAG, "清理过期备份(maxAgeDays=$maxAgeDays): ${info.fileName}")
                }
            } else {
                survivors.add(info)
            }
        }
        if (maxCount > 0 && survivors.size > maxCount) {
            for (index in maxCount until survivors.size) {
                val info = survivors[index]
                if (deleteBackupInternal(context, info.fileName)) {
                    deletedCount++
                    Log.i(TAG, "清理超额备份(maxCount=$maxCount): ${info.fileName}")
                }
            }
        }
        return deletedCount
    }

    /** 清理超过 1 天仍未改名的残留 .tmp 文件（崩溃 / 取消留下的半成品） */
    private fun cleanStaleTempFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - DAY_MS
        dir.listFiles { file -> file.isFile && file.name.endsWith(TEMP_SUFFIX) && file.lastModified() < cutoff }
            ?.forEach {
                if (it.delete()) {
                    Log.d(TAG, "清理残留临时备份文件: ${it.name}")
                }
            }
    }

    // ==================== 工具函数 ====================

    /**
     * 轻量校验备份 JSON 结构（至少要有 conversations 或 messages 数组）。
     * 校验后立即丢失大对象引用，避免"校验 + 导入"同时驻留多份解析结果、放大内存峰值。
     */
    private fun isValidBackupJson(jsonStr: String): Boolean = try {
        val rootObj = JSONObject(jsonStr)
        rootObj.optJSONArray("conversations") != null || rootObj.optJSONArray("messages") != null
    } catch (e: Exception) {
        Log.e(TAG, "备份内容不是合法 JSON", e)
        false
    }

    private fun backupDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(baseDir, BACKUP_DIR_NAME)
    }

    /** 解析并校验备份文件名，拒绝带路径 / 非 .json.gz 的名字，防止越权读写目录外文件 */
    private fun resolveBackupFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val safeName = File(fileName).name
        if (safeName != fileName) return null
        if (!safeName.endsWith(GZ_SUFFIX)) return null
        return File(backupDir(context), safeName)
    }

    private fun normalizeKind(kind: String): String {
        val normalized = kind.trim().lowercase(Locale.US)
        return when (normalized) {
            KIND_AUTO, KIND_MANUAL, KIND_PRERESTORE -> normalized
            else -> {
                Log.w(TAG, "未知备份类型: $kind，按 manual 处理")
                KIND_MANUAL
            }
        }
    }

    /** 生成同秒内也不会重名的文件名：backup_<kind>_<yyyyMMdd_HHmmss>[_序号].json.gz */
    private fun uniqueBackupFileName(dir: File, kind: String, createdAt: Long): String {
        val stamp = SimpleDateFormat(FILE_NAME_PATTERN, Locale.US).format(Date(createdAt))
        var candidate = "backup_${kind}_$stamp$GZ_SUFFIX"
        var sequence = 1
        while (File(dir, candidate).exists() && sequence < MAX_SAME_SECOND_BACKUPS) {
            candidate = "backup_${kind}_${stamp}_$sequence$GZ_SUFFIX"
            sequence++
        }
        return candidate
    }

    /** 从文件名解析备份类型：backup_<kind>_<时间戳>.json.gz */
    private fun kindFromFileName(fileName: String): String {
        val rest = fileName.removeSuffix(GZ_SUFFIX).removePrefix("backup_")
        return rest.substringBefore('_').ifBlank { KIND_MANUAL }
    }

    /** 日期戳（yyyy-MM-dd），用于"每天最多一次"的日期标记 */
    private fun dayStamp(timeMs: Long): String =
        SimpleDateFormat(DAY_PATTERN, Locale.US).format(Date(timeMs))
}
