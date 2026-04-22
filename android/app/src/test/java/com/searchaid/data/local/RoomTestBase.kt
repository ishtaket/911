package com.searchaid.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
abstract class RoomTestBase {

    protected lateinit var db: SearchAidDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SearchAidDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }
}
