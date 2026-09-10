package com.cyk666.vibemusic

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser

/**
 * Pins that every backup-rules file excludes precisely the AuthStore
 * DataStore file (datastore/<DATASTORE_NAME>.preferences_pb), so auth
 * tokens never leave the device via backup or device transfer.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {

    private fun excludedPathsBySection(resId: Int): Map<String, List<String>> {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(resId)
        val stack = ArrayDeque<String>()
        val found = mutableMapOf<String, MutableList<String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "exclude") {
                        val path = parser.getAttributeValue(null, "path")
                        val section = stack.lastOrNull {
                            it == "full-backup-content" || it == "cloud-backup" || it == "device-transfer"
                        }
                        if (path != null && section != null) {
                            found.getOrPut(section) { mutableListOf() }.add(path)
                        }
                    } else {
                        stack.addLast(parser.name)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name != "exclude") stack.removeLastOrNull()
                }
            }
            event = parser.next()
        }
        return found
    }

    @Test
    fun backupRules_excludesAuthStoreFile() {
        val expected = "datastore/${AuthStore.DATASTORE_NAME}.preferences_pb"
        val bySection = excludedPathsBySection(R.xml.backup_rules)
        val paths = bySection["full-backup-content"].orEmpty()
        assertTrue(
            "backup_rules.xml must exclude AuthStore file $expected, found $paths",
            expected in paths
        )
    }

    @Test
    fun dataExtractionRules_excludesAuthStoreFile() {
        val expected = "datastore/${AuthStore.DATASTORE_NAME}.preferences_pb"
        val bySection = excludedPathsBySection(R.xml.data_extraction_rules)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val paths = bySection[section].orEmpty()
            assertTrue(
                "data_extraction_rules.xml [$section] must exclude AuthStore file $expected, found $paths",
                expected in paths
            )
        }
    }
}
