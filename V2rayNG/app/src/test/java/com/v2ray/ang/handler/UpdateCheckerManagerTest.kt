package com.v2ray.ang.handler

import com.v2ray.ang.dto.GitHubRelease
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateCheckerManagerTest {

    private fun release(tag: String, pre: Boolean = false) =
        GitHubRelease(tagName = tag, body = "", assets = emptyList(), prerelease = pre)

    // GitHub's /releases list is not ordered by version: on 2026-10-31 it returned b2, b1, then the
    // b3 pre-release, so taking the first entry hid b3 from testers.
    @Test
    fun `newest version wins whatever the list order`() {
        val list = listOf(release("v0.1.4-b2"), release("v0.1.4-b1"), release("v0.1.4-b3", pre = true))
        assertEquals("v0.1.4-b3", UpdateCheckerManager.newestRelease(list)?.tagName)
    }

    @Test
    fun `build number counts after the version`() {
        val list = listOf(release("v0.1.4-b5"), release("v0.1.5-b1"), release("v0.1.10-b1"))
        assertEquals("v0.1.10-b1", UpdateCheckerManager.newestRelease(list)?.tagName)
    }

    @Test
    fun `empty list has no release`() {
        assertEquals(null, UpdateCheckerManager.newestRelease(emptyList()))
    }

    // 2026-11-01: Android's update dialog listed Windows-only changes (Sadeq).
    private val both = "<!-- windows -->\nWin fix\n<!-- /windows -->\n<!-- android -->\nPhone fix\n<!-- /android -->\n" +
        "<!-- windows -->\nرفع ویندوز\n<!-- /windows -->\n<!-- android -->\nرفع گوشی\n<!-- /android -->\nDownloads"
    private val windowsOnly = "<!-- windows -->\nWin fix\n<!-- /windows -->\nAndroid: no change."

    @Test
    fun `android notes keep only the android parts`() {
        assertEquals("Phone fix\n\nرفع گوشی", UpdateCheckerManager.androidNotes(both))
    }

    @Test
    fun `windows-only release has no android notes`() {
        assertEquals(null, UpdateCheckerManager.androidNotes(windowsOnly))
    }

    @Test
    fun `old release without marks shows all`() {
        assertEquals("plain", UpdateCheckerManager.androidNotes("plain"))
    }

    @Test
    fun `windows-only and unrequested pre-releases are skipped`() {
        val list = listOf(
            GitHubRelease(tagName = "v0.1.6-b1", body = windowsOnly, assets = emptyList()),
            GitHubRelease(tagName = "v0.1.6-b2", body = both, assets = emptyList(), prerelease = true),
            GitHubRelease(tagName = "v0.1.4-b4", body = "old", assets = emptyList()),
        )
        assertEquals("v0.1.4-b4", UpdateCheckerManager.newestAndroidRelease(list, includePreRelease = false)?.tagName)
        assertEquals("v0.1.6-b2", UpdateCheckerManager.newestAndroidRelease(list, includePreRelease = true)?.tagName)
    }
}
