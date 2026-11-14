package com.reelvault.app

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// ---------- Room ----------
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val folder: String,
    val favorite: Boolean = false,
    val liked: Boolean = false,
    val caption: String = "",
    val positionMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos") fun observeAll(): Flow<List<VideoEntity>>
    @Query("SELECT * FROM videos") suspend fun getAll(): List<VideoEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(items: List<VideoEntity>)
    @Query("UPDATE videos SET name=:name, durationMs=:dur, size=:size, dateAdded=:date, folder=:folder WHERE uri=:uri")
    suspend fun updateMeta(uri: String, name: String, dur: Long, size: Long, date: Long, folder: String)
    @Query("DELETE FROM videos WHERE uri IN (:uris)") suspend fun delete(uris: List<String>)
    @Query("UPDATE videos SET favorite=:v WHERE uri=:uri") suspend fun setFavorite(uri: String, v: Boolean)
    @Query("UPDATE videos SET liked=:v WHERE uri=:uri") suspend fun setLiked(uri: String, v: Boolean)
    @Query("UPDATE videos SET caption=:c WHERE uri=:uri") suspend fun setCaption(uri: String, c: String)
    @Query("UPDATE videos SET positionMs=:p WHERE uri=:uri") suspend fun setPosition(uri: String, p: Long)
    @Query("UPDATE videos SET hidden=:h WHERE uri IN (:uris)") suspend fun setHidden(uris: List<String>, h: Boolean)
    @Query("UPDATE videos SET favorite=:v WHERE uri IN (:uris)") suspend fun setFavoriteMany(uris: List<String>, v: Boolean)
}

@Database(entities = [VideoEntity::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): VideoDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "reelvault.db")
                .addMigrations(object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE videos ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                    }
                })
                .build().also { instance = it }
        }
    }
}

// ---------- DataStore ----------
private val Context.store by preferencesDataStore("reelvault_settings")

object K {
    val theme = intPreferencesKey("theme")            // 0 system, 1 dark, 2 light
    val grid = booleanPreferencesKey("grid")
    val sort = intPreferencesKey("sort")              // 0 newest, 1 oldest, 2 name
    val auto = booleanPreferencesKey("auto_scan")
    val remember = booleanPreferencesKey("remember_last")
    val last = stringPreferencesKey("last_uri")
    val speed = floatPreferencesKey("speed")
    val loop = booleanPreferencesKey("loop")
    val folders = stringSetPreferencesKey("folders")
    val name = stringPreferencesKey("profile_name")
    val handle = stringPreferencesKey("profile_handle")
    val bio = stringPreferencesKey("profile_bio")
    val avatar = stringPreferencesKey("profile_avatar")
    val reelsRandom = booleanPreferencesKey("reels_random")
    val autoUpdate = booleanPreferencesKey("auto_update")
    val updateRepo = stringPreferencesKey("update_repo")
    val instagram = stringPreferencesKey("profile_instagram")
}

data class Prefs(
    val theme: Int = 0,
    val grid: Boolean = true,
    val sort: Int = 0,
    val auto: Boolean = true,
    val remember: Boolean = true,
    val last: String = "",
    val speed: Float = 1f,
    val loop: Boolean = true,
    val folders: Set<String> = emptySet(),
    val name: String = "",
    val handle: String = "",
    val bio: String = "",
    val avatar: String = "",
    val reelsRandom: Boolean = true,   // direct Reels button: random order (true) or in order (false)
    val autoUpdate: Boolean = true,
    val updateRepo: String = "",
    val instagram: String = "",
)

class SettingsRepo(private val ctx: Context) {
    val prefs: Flow<Prefs> = ctx.store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            Prefs(
                theme = p[K.theme] ?: 0, grid = p[K.grid] ?: true, sort = p[K.sort] ?: 0,
                auto = p[K.auto] ?: true, remember = p[K.remember] ?: true, last = p[K.last] ?: "",
                speed = p[K.speed] ?: 1f, loop = p[K.loop] ?: true, folders = p[K.folders] ?: emptySet(),
                name = p[K.name] ?: "", handle = p[K.handle] ?: "", bio = p[K.bio] ?: "",
                avatar = p[K.avatar] ?: "",
                reelsRandom = p[K.reelsRandom] ?: true, autoUpdate = p[K.autoUpdate] ?: true,
                updateRepo = p[K.updateRepo] ?: "",
                instagram = p[K.instagram] ?: "",
            )
        }

    suspend fun edit(block: (MutablePreferences) -> Unit) { ctx.store.edit { block(it) } }
}
