package com.cyk666.vibemusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AuthStoreTest {

    private fun context(): Context =
        RuntimeEnvironment.getApplication()

    @Test
    fun saveLoad_roundTrip(): Unit = runBlocking {
        AuthStore.save(context(), "tok123", "alice", "Alice")
        val snap = AuthStore.load(context())
        assertEquals("tok123", snap.token)
        assertEquals("alice", snap.username)
        assertEquals("Alice", snap.nickname)
    }

    @Test
    fun clear_wipesTokenAndUsername(): Unit = runBlocking {
        AuthStore.save(context(), "tok123", "alice", "Alice")
        AuthStore.clear(context())
        val snap = AuthStore.load(context())
        assertEquals("", snap.token)
        assertEquals("", snap.username)
        assertEquals("", snap.nickname)
    }

    @Test
    fun load_populatesAuthTokenHolder(): Unit = runBlocking {
        AuthStore.save(context(), "tok999", "bob", "Bob")
        AuthToken.token = ""
        AuthToken.username = ""
        AuthStore.load(context())
        assertEquals("tok999", AuthToken.token)
        assertEquals("bob", AuthToken.username)
    }
}
