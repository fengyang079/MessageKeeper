package com.messagekeeper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.messagekeeper.data.db.dao.ConversationDao
import com.messagekeeper.data.db.dao.DeletedMessageDao
import com.messagekeeper.data.db.dao.MessageDao
import com.messagekeeper.data.db.dao.SpecialAlertDao
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.DeletedMessage
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.data.db.entity.SpecialAlert

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversations ADD COLUMN is_system INTEGER NOT NULL DEFAULT 0")
    }
}

// 消息列表按对话+时间排序、去重查询都按 (conversation_id, timestamp) 过滤，加复合索引
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversation_id_timestamp ON messages(conversation_id, timestamp)")
    }
}

// 会话类型（自动/群聊/私信），长按会话手动指定
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN chat_type INTEGER NOT NULL DEFAULT 0")
    }
}

// 历史删除消息表（回收站）
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deleted_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `conversation_key` TEXT NOT NULL,
                `conversation_name` TEXT NOT NULL,
                `chat_type` INTEGER NOT NULL DEFAULT 0,
                `is_system` INTEGER NOT NULL DEFAULT 0,
                `sender_name` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `platform` TEXT NOT NULL,
                `notification_id` TEXT NOT NULL,
                `is_recalled` INTEGER NOT NULL DEFAULT 0,
                `deleted_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_messages_deleted_at` ON `deleted_messages` (`deleted_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_messages_conversation_key` ON `deleted_messages` (`conversation_key`)")
    }
}

@Database(
    entities = [Conversation::class, Message::class, SpecialAlert::class, DeletedMessage::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun specialAlertDao(): SpecialAlertDao
    abstract fun deletedMessageDao(): DeletedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "message_keeper.db"
            )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}


