package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupRulesCoverageTest {

    private val rulesXml: String by lazy {
        val paths = listOf(
            "app/src/main/res/xml/data_extraction_rules.xml",
            "src/main/res/xml/data_extraction_rules.xml",
        )
        val projectRoot = File(System.getProperty("user.dir"))
        paths.map { File(projectRoot, it) }
            .first { it.exists() }
            .readText()
    }

    @Test
    fun `backup rules exclude encrypted api key file`() {
        assertExcluded("encrypted_api_keys.json")
    }

    @Test
    fun `backup rules exclude tink keyset shared preferences`() {
        assertExcluded("nimbus_api_key_keyset_prefs.xml")
    }

    @Test
    fun `backup rules exclude user preferences datastore`() {
        assertExcluded("nimbus_prefs.preferences_pb")
    }

    @Test
    fun `backup rules exclude widget data datastore`() {
        assertExcluded("nimbus_widget_data.preferences_pb")
    }

    @Test
    fun `backup rules exclude room database and sidecars`() {
        assertExcluded("nimbus.db")
        assertExcluded("nimbus.db-wal")
        assertExcluded("nimbus.db-shm")
    }

    @Test
    fun `backup rules exclude on-this-day location history cache`() {
        assertExcluded("nimbus_on_this_day.xml")
    }

    @Test
    fun `both cloud-backup and device-transfer sections exclude keyset prefs`() {
        val cloudSection = rulesXml.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transferSection = rulesXml.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        val keysetPath = "nimbus_api_key_keyset_prefs.xml"
        assertTrue("cloud-backup must exclude $keysetPath", cloudSection.contains(keysetPath))
        assertTrue("device-transfer must exclude $keysetPath", transferSection.contains(keysetPath))
    }

    @Test
    fun `both cloud-backup and device-transfer sections exclude on-this-day cache`() {
        val cloudSection = rulesXml.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transferSection = rulesXml.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        val onThisDayPath = "nimbus_on_this_day.xml"
        assertTrue("cloud-backup must exclude $onThisDayPath", cloudSection.contains(onThisDayPath))
        assertTrue("device-transfer must exclude $onThisDayPath", transferSection.contains(onThisDayPath))
    }

    private fun assertExcluded(path: String) {
        assertTrue(
            "data_extraction_rules.xml must exclude '$path'",
            rulesXml.contains(path),
        )
    }
}
