package com.cyk666.vibemusic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QueueStoreTest {

    private fun context(): Context =
        RuntimeEnvironment.getApplication()

    @Suppress("UNCHECKED_CAST")
    private fun playbackStore(context: Context): DataStore<Preferences> {
        val facade = Class.forName("com.cyk666.vibemusic.PlaybackStateStoreKt")
        val getter = facade.getDeclaredMethod("getPlaybackDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }

    @Test
    fun saveLoad_roundTripPreservesFieldsAndIndex(): Unit = runBlocking {
        val songs = listOf(
            Song("1895330088", "予以", "队长", "予以", "https://cover.example/x.jpg", 231, "netease"),
            Song("2", "n2", "a2", "al2", "", 0, "qq")
        )
        QueueStore.saveQueue(context(), songs, 1)
        val (loaded, index) = QueueStore.loadQueue(context())
        assertEquals(2, loaded.size)
        assertEquals(songs, loaded)
        assertEquals(1, index)
    }

    @Test
    fun load_corruptJsonReturnsEmptyWithoutThrowing(): Unit = runBlocking {
        playbackStore(context()).edit { p ->
            p[stringPreferencesKey("queue_json")] = "{not valid json!!!"
        }
        val (loaded, index) = QueueStore.loadQueue(context())
        assertTrue(loaded.isEmpty())
        assertEquals(0, index)
    }
}
