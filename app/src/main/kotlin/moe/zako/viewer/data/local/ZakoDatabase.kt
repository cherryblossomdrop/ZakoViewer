package moe.zako.viewer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.local.dao.FavoriteDao
import moe.zako.viewer.data.local.dao.SearchHistoryDao
import moe.zako.viewer.data.local.dao.WatchHistoryDao
import moe.zako.viewer.data.local.dao.WatchLaterDao
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.SearchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity
import java.io.File

@Database(
    entities = [
        FavoriteFolderEntity::class,
        FavoriteItemEntity::class,
        WatchHistoryEntity::class,
        WatchLaterEntity::class,
        DownloadEntity::class,
        SearchHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ZakoDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var instance: ZakoDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN cdnUrlsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, PRIMARY KEY(`query`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN duration TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): ZakoDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    // 兼容存量数据：检查旧版数据库文件 neoviewer.db，若存在且新版数据库未生成，则自动重命名迁移
                    try {
                        val oldDb = appContext.getDatabasePath("neoviewer.db")
                        val newDb = appContext.getDatabasePath("zakoviewer.db")
                        if (oldDb.exists() && !newDb.exists()) {
                            oldDb.renameTo(newDb)
                            val oldWal = File(oldDb.path + "-wal")
                            if (oldWal.exists()) oldWal.renameTo(File(newDb.path + "-wal"))
                            val oldShm = File(oldDb.path + "-shm")
                            if (oldShm.exists()) oldShm.renameTo(File(newDb.path + "-shm"))
                        }
                    } catch (_: Exception) {}

                    Room.databaseBuilder(
                        appContext,
                        ZakoDatabase::class.java,
                        "zakoviewer.db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { instance = it }
                }
            }
        }
    }
}

typealias NeoDatabase = ZakoDatabase
