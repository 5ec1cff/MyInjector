package io.github.a13e300.myinjector.ui.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsModelTest {
    @Test fun searchRanksTitleBeforeSummaryAndKeyAndRestoresGroups() {
        val settings = SettingsGroup().apply {
            group("隐私") {
                toggle("隐藏号码", "phone", "只对自己隐藏")
                toggle("隐藏头像", "avatar")
            }
            action("其他", "隐藏_key", "隐藏的选项")
        }
        assertEquals(listOf("phone", "avatar", "隐藏_key"), settings.search(" 隐藏 ").map { it.key })
        assertEquals(listOf("phone"), settings.search("PHONE").map { it.key })
        assertTrue(settings.search("不存在").isEmpty())
        assertEquals(4, settings.search("").size)
        assertEquals("隐私", settings.search("").first().title)
        assertEquals("只对自己隐藏", settings.find("phone").summary)
    }

    @Test fun rejectedChangesAndDisabledItemsNeverMutateOrPersist() {
        val setting = ToggleSetting("enabled", "总开关")
        assertFalse(setting.requestChange(true) { false })
        assertFalse(setting.isChecked)
        setting.isEnabled = false
        var writes = 0
        assertFalse(setting.requestChange(true) { writes++; true })
        assertEquals(0, writes)
    }

    @Test fun acceptedChangesPersistOnceAndDriveDependentSetting() {
        val settings = SettingsGroup().apply {
            toggle("隐藏号码", "hidePhoneNumber")
            toggle("仅对自己隐藏", "hidePhoneNumberForSelfOnly") { isEnabled = false }
        }
        val parent = settings.find("hidePhoneNumber") as ToggleSetting
        val child = settings.find("hidePhoneNumberForSelfOnly")
        var writes = 0
        val persist: (Boolean) -> Boolean = { value -> writes++; child.isEnabled = value; true }
        assertTrue(parent.requestChange(true, persist))
        assertTrue(child.isEnabled)
        assertFalse(parent.requestChange(true, persist))
        assertEquals(1, writes)
        assertTrue(parent.requestChange(false, persist))
        assertFalse(child.isEnabled)
    }
}
