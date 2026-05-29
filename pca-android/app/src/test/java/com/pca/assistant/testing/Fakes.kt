package com.pca.assistant.testing

import com.pca.assistant.data.db.dao.DaySummaryDao
import com.pca.assistant.data.db.dao.HourSummaryDao
import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.LlmHealthDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.PlaceDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.DaySummaryEntity
import com.pca.assistant.data.db.entity.HourSummaryEntity
import com.pca.assistant.data.db.entity.InterventionEntity
import com.pca.assistant.data.db.entity.LlmHealthEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.data.db.entity.OwnerProfileEntity
import com.pca.assistant.data.db.entity.PlaceEntity
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.data.db.entity.WindowEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure-JVM fakes for the Room DAOs. They mimic the production semantics
 * closely enough that the rest of the business logic can be exercised
 * without spinning up an in-memory Room database (which would require
 * Robolectric and balloon the test classpath).
 */

class FakeOwnerDao : OwnerDao {
    private val flow = MutableStateFlow<OwnerProfileEntity?>(null)
    override suspend fun get(): OwnerProfileEntity? = flow.value
    override fun observe(): Flow<OwnerProfileEntity?> = flow
    override suspend fun upsert(entity: OwnerProfileEntity) { flow.value = entity.copy(id = 1) }
    override suspend fun wipe() { flow.value = null }
}

class FakeTranscriptDao : TranscriptDao {
    private val seq = AtomicLong(0)
    val store = MutableStateFlow<List<TranscriptEntity>>(emptyList())
    override suspend fun insert(entity: TranscriptEntity): Long {
        val id = seq.incrementAndGet()
        store.value = store.value + entity.copy(id = id)
        return id
    }
    override suspend fun between(fromTs: Long, toTs: Long): List<TranscriptEntity> =
        store.value.filter { it.ts in fromTs until toTs }.sortedBy { it.ts }
    override fun observeRecent(sinceTs: Long, limit: Int): Flow<List<TranscriptEntity>> =
        store.map { it.filter { e -> e.ts >= sinceTs }.sortedByDescending { e -> e.ts }.take(limit) }
    override suspend fun purgeOlderThan(olderThan: Long) {
        store.value = store.value.filter { it.ts >= olderThan }
    }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakeWindowDao : WindowDao {
    private val seq = AtomicLong(0)
    val store = MutableStateFlow<List<WindowEntity>>(emptyList())
    override suspend fun insert(entity: WindowEntity): Long {
        val id = seq.incrementAndGet()
        store.value = store.value + entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: WindowEntity) {
        store.value = store.value.map { if (it.id == entity.id) entity else it }
    }
    override suspend fun byId(id: Long): WindowEntity? = store.value.firstOrNull { it.id == id }
    override fun observeRecent(limit: Int): Flow<List<WindowEntity>> =
        store.map { it.sortedByDescending { e -> e.startTs }.take(limit) }
    override suspend fun between(fromTs: Long, toTs: Long): List<WindowEntity> =
        store.value.filter { it.startTs in fromTs until toTs }.sortedBy { it.startTs }
    override fun countSince(sinceTs: Long): Flow<Int> =
        store.map { it.count { e -> e.startTs >= sinceTs } }
    override suspend fun lastSent(): WindowEntity? =
        store.value.filter { it.sentToLlm }.maxByOrNull { it.startTs }
    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }
    override suspend fun purgeOlderThan(olderThan: Long) {
        store.value = store.value.filter { it.startTs >= olderThan }
    }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakeInterventionDao : InterventionDao {
    private val seq = AtomicLong(0)
    val store = MutableStateFlow<List<InterventionEntity>>(emptyList())
    override suspend fun insert(entity: InterventionEntity): Long {
        val id = seq.incrementAndGet()
        store.value = store.value + entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: InterventionEntity) {
        store.value = store.value.map { if (it.id == entity.id) entity else it }
    }
    override suspend fun byId(id: Long): InterventionEntity? =
        store.value.firstOrNull { it.id == id }
    override fun observeRecent(limit: Int): Flow<List<InterventionEntity>> =
        store.map { it.sortedByDescending { e -> e.ts }.take(limit) }
    override fun countSince(sinceTs: Long): Flow<Int> =
        store.map { it.count { e -> e.ts >= sinceTs } }
    override suspend fun last(): InterventionEntity? = store.value.maxByOrNull { it.ts }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakeOpenThreadDao : OpenThreadDao {
    val store = MutableStateFlow<List<OpenThreadEntity>>(emptyList())
    override suspend fun upsert(entity: OpenThreadEntity) {
        store.value = store.value.filterNot { it.id == entity.id } + entity
    }
    override suspend fun upsertAll(items: List<OpenThreadEntity>) {
        items.forEach { upsert(it) }
    }
    override suspend fun openThreads(): List<OpenThreadEntity> =
        store.value.filter { it.status == "open" }
    override fun observeOpen(): Flow<List<OpenThreadEntity>> =
        store.map { it.filter { e -> e.status == "open" } }
    override suspend fun close(ids: List<String>) {
        store.value = store.value.map { if (it.id in ids) it.copy(status = "closed") else it }
    }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakeHourSummaryDao : HourSummaryDao {
    val store = MutableStateFlow<List<HourSummaryEntity>>(emptyList())
    override suspend fun upsert(entity: HourSummaryEntity) {
        store.value = store.value.filterNot { it.hourStart == entity.hourStart } + entity
    }
    override suspend fun between(fromTs: Long, toTs: Long): List<HourSummaryEntity> =
        store.value.filter { it.hourStart in fromTs until toTs }.sortedBy { it.hourStart }
    override suspend fun recent(limit: Int): List<HourSummaryEntity> =
        store.value.sortedByDescending { it.hourStart }.take(limit)
    override suspend fun purgeOlderThan(olderThan: Long) {
        store.value = store.value.filter { it.hourStart >= olderThan }
    }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakeDaySummaryDao : DaySummaryDao {
    val store = MutableStateFlow<List<DaySummaryEntity>>(emptyList())
    override suspend fun upsert(entity: DaySummaryEntity) {
        store.value = store.value.filterNot { it.date == entity.date } + entity
    }
    override fun observeRecent(limit: Int): Flow<List<DaySummaryEntity>> =
        store.map { it.sortedByDescending { e -> e.date }.take(limit) }
    override suspend fun byDate(date: String): DaySummaryEntity? =
        store.value.firstOrNull { it.date == date }
    override suspend fun purgeOlderThan(olderThan: String) {
        store.value = store.value.filter { it.date >= olderThan }
    }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakePlaceDao : PlaceDao {
    private val seq = AtomicLong(0)
    val store = MutableStateFlow<List<PlaceEntity>>(emptyList())
    override suspend fun upsert(entity: PlaceEntity): Long {
        val withId = if (entity.id == 0L) entity.copy(id = seq.incrementAndGet()) else entity
        store.value = store.value.filterNot { it.id == withId.id } + withId
        return withId.id
    }
    override suspend fun all(): List<PlaceEntity> = store.value
    override fun observeAll(): Flow<List<PlaceEntity>> = store
    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }
    override suspend fun wipe() { store.value = emptyList() }
}

class FakeLlmHealthDao : LlmHealthDao {
    private val seq = AtomicLong(0)
    val store = MutableStateFlow<List<LlmHealthEntity>>(emptyList())
    override suspend fun insert(entity: LlmHealthEntity): Long {
        val id = seq.incrementAndGet()
        store.value = store.value + entity.copy(id = id)
        return id
    }
    override suspend fun recentForProvider(provider: String, limit: Int): List<LlmHealthEntity> =
        store.value.filter { it.provider == provider }.sortedByDescending { it.ts }.take(limit)
    override suspend fun purgeOlderThan(olderThan: Long) {
        store.value = store.value.filter { it.ts >= olderThan }
    }
}
