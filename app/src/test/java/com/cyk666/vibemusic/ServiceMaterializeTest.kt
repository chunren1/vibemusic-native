package com.cyk666.vibemusic

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceMaterializeTest {

    @Test
    fun playCommands_materialize() {
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_PLAY_PAUSE))
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_PREPARE))
    }

    @Test
    fun transportCommands_materialize() {
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_TO_PREVIOUS))
    }

    @Test
    fun seekCommands_materialize() {
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(isServiceMaterializeCommand(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
    }

    @Test
    fun nonTransportCommands_passThrough() {
        assertFalse(isServiceMaterializeCommand(Player.COMMAND_SET_VOLUME))
        assertFalse(isServiceMaterializeCommand(Player.COMMAND_GET_TIMELINE))
        assertFalse(isServiceMaterializeCommand(Player.COMMAND_SET_REPEAT_MODE))
        assertFalse(isServiceMaterializeCommand(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        assertFalse(isServiceMaterializeCommand(Player.COMMAND_INVALID))
    }
}
