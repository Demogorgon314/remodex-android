package com.emanueledipietro.remodex.data.threads

import android.content.Context
import androidx.room.Room
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import com.emanueledipietro.remodex.data.connection.SecureStore
import com.emanueledipietro.remodex.data.connection.SecureStoreKeys
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "cached_threads")
data class CachedThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val name: String?,
    val preview: String,
    val projectPath: String,
    val lastUpdatedLabel: String,
    val lastUpdatedEpochMs: Long,
    val isRunning: Boolean,
    val syncState: String,
    val parentThreadId: String?,
    val agentNickname: String?,
    val agentRole: String?,
    val runtimeConfigJson: String,
)

@Entity(
    tableName = "cached_timeline_items",
    indices = [Index("threadId"), Index(value = ["threadId", "orderIndex"])],
)
data class CachedTimelineItemEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val speaker: String,
    val kind: String,
    val text: String,
    val supportingText: String?,
    val turnId: String?,
    val itemId: String?,
    val isStreaming: Boolean,
    val deliveryState: String,
    val createdAtEpochMs: Long?,
    val attachmentsJson: String,
    val planStateJson: String?,
    val subagentActionJson: String?,
    val structuredUserInputRequestJson: String?,
    val assistantChangeSetJson: String?,
    val orderIndex: Long,
)

data class CachedThreadWithTimeline(
    @Embedded val thread: CachedThreadEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "threadId",
    )
    val timelineItems: List<CachedTimelineItemEntity>,
)

@Dao
interface ThreadCacheDao {
    @Transaction
    @Query("SELECT * FROM cached_threads ORDER BY lastUpdatedEpochMs DESC")
    fun observeThreadsWithTimeline(): Flow<List<CachedThreadWithTimeline>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreads(threads: List<CachedThreadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelineItems(items: List<CachedTimelineItemEntity>)

    @Query("DELETE FROM cached_timeline_items")
    suspend fun clearTimelineItems()

    @Query("DELETE FROM cached_threads")
    suspend fun clearThreads()
}

@Database(
    entities = [CachedThreadEntity::class, CachedTimelineItemEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class RemodexThreadCacheDatabase : RoomDatabase() {
    abstract fun threadCacheDao(): ThreadCacheDao
}

private val threadCacheJson = Json {
    ignoreUnknownKeys = true
}

private const val LegacyThreadCacheDatabaseName = "remodex_thread_cache.db"

class RoomThreadCacheStore(
    private val database: RemodexThreadCacheDatabase,
) : ThreadCacheStore {
    override val threads: Flow<List<CachedThreadRecord>> =
        database.threadCacheDao().observeThreadsWithTimeline().map { records ->
            records.map { record ->
                CachedThreadRecord(
                    id = record.thread.id,
                    title = record.thread.title,
                    name = record.thread.name,
                    preview = record.thread.preview,
                    projectPath = record.thread.projectPath,
                    lastUpdatedLabel = record.thread.lastUpdatedLabel,
                    lastUpdatedEpochMs = record.thread.lastUpdatedEpochMs,
                    isRunning = record.thread.isRunning,
                    syncState = com.emanueledipietro.remodex.model.RemodexThreadSyncState.valueOf(record.thread.syncState),
                    parentThreadId = record.thread.parentThreadId,
                    agentNickname = record.thread.agentNickname,
                    agentRole = record.thread.agentRole,
                    activeTurnId = null,
                    latestTurnTerminalState = null,
                    stoppedTurnIds = emptySet(),
                    runtimeConfig = runCatching {
                        threadCacheJson.decodeFromString<RemodexRuntimeConfig>(record.thread.runtimeConfigJson)
                    }.getOrElse {
                        RemodexRuntimeConfig()
                    },
                    timelineItems = record.timelineItems
                        .sortedBy(CachedTimelineItemEntity::orderIndex)
                        .map(CachedTimelineItemEntity::toModel),
                )
            }
        }

    override suspend fun replaceThreads(threads: List<CachedThreadRecord>) {
        val threadEntities = threads.map(CachedThreadRecord::toEntity)
        val timelineEntities = threads.flatMap { thread ->
            thread.timelineItems.map { item ->
                item.toEntity(thread.id)
            }
        }
        database.withTransaction {
            database.threadCacheDao().clearTimelineItems()
            database.threadCacheDao().clearThreads()
            database.threadCacheDao().upsertThreads(threadEntities)
            database.threadCacheDao().upsertTimelineItems(timelineEntities)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileAwareThreadCacheStore(
    private val context: Context,
    private val secureStore: SecureStore,
) : ThreadCacheStore {
    private val activeProfileId = MutableStateFlow<String?>(null)
    private val storesByDatabaseName = linkedMapOf<String, RoomThreadCacheStore>()

    override val threads: Flow<List<CachedThreadRecord>> =
        activeProfileId.flatMapLatest { profileId ->
            val normalizedProfileId = profileId?.trim()?.takeIf(String::isNotBlank)
            if (normalizedProfileId == null) {
                flowOf(emptyList())
            } else {
                storeForProfile(normalizedProfileId).threads
            }
        }

    override fun setActiveProfileId(profileId: String?) {
        activeProfileId.value = profileId?.trim()?.takeIf(String::isNotBlank)
    }

    override suspend fun replaceThreads(threads: List<CachedThreadRecord>) {
        val profileId = activeProfileId.value?.trim()?.takeIf(String::isNotBlank) ?: return
        storeForProfile(profileId).replaceThreads(threads)
    }

    private fun storeForProfile(profileId: String): RoomThreadCacheStore {
        val databaseName = databaseNameForProfile(profileId)
        return storesByDatabaseName.getOrPut(databaseName) {
            val database = Room.databaseBuilder(
                context.applicationContext,
                RemodexThreadCacheDatabase::class.java,
                databaseName,
            ).fallbackToDestructiveMigration(dropAllTables = true).build()
            RoomThreadCacheStore(database)
        }
    }

    private fun databaseNameForProfile(profileId: String): String {
        val legacyProfileId = secureStore.readString(SecureStoreKeys.LEGACY_THREAD_CACHE_PROFILE_ID)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (profileId == legacyProfileId) {
            return LegacyThreadCacheDatabaseName
        }
        val normalizedSuffix = profileId.map { char ->
            if (char.isLetterOrDigit()) {
                char
            } else {
                '_'
            }
        }.joinToString(separator = "")
        return "remodex_thread_cache_$normalizedSuffix.db"
    }
}

private fun CachedThreadRecord.toEntity(): CachedThreadEntity {
    return CachedThreadEntity(
        id = id,
        title = title,
        name = name,
        preview = preview,
        projectPath = projectPath,
        lastUpdatedLabel = lastUpdatedLabel,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
        isRunning = isRunning,
        syncState = syncState.name,
        parentThreadId = parentThreadId,
        agentNickname = agentNickname,
        agentRole = agentRole,
        runtimeConfigJson = threadCacheJson.encodeToString(runtimeConfig),
    )
}

private fun RemodexConversationItem.toEntity(threadId: String): CachedTimelineItemEntity {
    return CachedTimelineItemEntity(
        id = id,
        threadId = threadId,
        speaker = speaker.name,
        kind = kind.name,
        text = text,
        supportingText = supportingText,
        turnId = turnId,
        itemId = itemId,
        isStreaming = isStreaming,
        deliveryState = deliveryState.name,
        createdAtEpochMs = createdAtEpochMs,
        attachmentsJson = threadCacheJson.encodeToString(attachments),
        planStateJson = planState?.let(threadCacheJson::encodeToString),
        subagentActionJson = subagentAction?.let(threadCacheJson::encodeToString),
        structuredUserInputRequestJson = structuredUserInputRequest?.let(threadCacheJson::encodeToString),
        assistantChangeSetJson = assistantChangeSet?.let(threadCacheJson::encodeToString),
        orderIndex = orderIndex,
    )
}

private fun CachedTimelineItemEntity.toModel(): RemodexConversationItem {
    return RemodexConversationItem(
        id = id,
        speaker = ConversationSpeaker.valueOf(speaker),
        kind = ConversationItemKind.valueOf(kind),
        text = text,
        supportingText = supportingText,
        turnId = turnId,
        itemId = itemId,
        isStreaming = isStreaming,
        deliveryState = RemodexMessageDeliveryState.valueOf(deliveryState),
        createdAtEpochMs = createdAtEpochMs,
        attachments = threadCacheJson.decodeFromString(attachmentsJson),
        planState = planStateJson?.let(threadCacheJson::decodeFromString),
        subagentAction = subagentActionJson?.let(threadCacheJson::decodeFromString),
        structuredUserInputRequest = structuredUserInputRequestJson?.let(threadCacheJson::decodeFromString),
        orderIndex = orderIndex,
        assistantChangeSet = assistantChangeSetJson?.let(threadCacheJson::decodeFromString),
    )
}
