package moe.zako.viewer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import moe.zako.viewer.data.local.dao.DownloadDao
import moe.zako.viewer.data.local.dao.FavoriteDao
import moe.zako.viewer.data.local.dao.WatchHistoryDao
import moe.zako.viewer.data.local.dao.WatchLaterDao
import moe.zako.viewer.data.local.entities.DownloadEntity
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.data.local.entities.WatchHistoryEntity
import moe.zako.viewer.data.local.entities.WatchLaterEntity

@Database(
    entities = [
        FavoriteFolderEntity::class,
        FavoriteItemEntity::class,
        WatchHistoryEntity::class,
        WatchLaterEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NeoDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: NeoDatabase? = null

        fun getInstance(context: Context): NeoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeoDatabase::class.java,
                    "neoviewer.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
