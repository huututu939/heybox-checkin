package com.heybox.checkin

import com.heybox.checkin.data.UpdateSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSourcesTest {

    private val apk = "https://github.com/huututu939/heybox-checkin/releases/download/v1.5.1/app-release.apk"

    @Test
    fun `直连排第一，后面跟镜像`() {
        val list = UpdateSources.candidates(apk)
        assertEquals(apk, list.first())
        assertTrue(list.size > 1)
        list.drop(1).forEach { assertTrue(it.endsWith(apk)) }
    }

    @Test
    fun `每一条都是同一条资产地址`() {
        val list = UpdateSources.candidates(apk)
        list.forEach { assertTrue(it.contains("/releases/download/v1.5.1/app-release.apk")) }
        assertEquals(list.size, list.distinct().size)
    }

    @Test
    fun `非 GitHub 地址原样返回`() {
        val other = "https://example.com/update/app.apk"
        assertEquals(listOf(other), UpdateSources.candidates(other))
    }

    @Test
    fun `线路名取域名`() {
        assertEquals("gh-proxy.com", UpdateSources.label("https://gh-proxy.com/https://github.com/a/b.apk"))
        assertEquals("github.com", UpdateSources.label(apk))
    }
}
