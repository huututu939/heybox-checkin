package com.heybox.checkin

import com.heybox.checkin.core.AppInfo
import com.heybox.checkin.core.DeviceInfo
import com.heybox.checkin.core.HeyboxClient
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.SessionInfo
import org.junit.Assert.assertTrue
import org.junit.Test

/** 联调用：真打一次线上只读接口，确认签名被服务端接受。需要联网，默认跳过，手动去掉 @Ignore 再跑。 */
class HeyboxLiveSmokeTest {

    @Test
    @org.junit.Ignore("联网联调，默认不参与回归")
    fun webSignatureAcceptedByLiveApi() {
        val config = HeyboxConfig(
            session = SessionInfo(heyboxId = "-1", pkey = "", deviceId = "0f0e0d0c-0000-4000-8000-000000000000", tokenId = ""),
            device = DeviceInfo(model = "PKJ110", osVersion = "17"),
            app = AppInfo(),
        )
        val raw = HeyboxClient(config).web("/game/all_recommend/v2")
        println("LIVE_RESPONSE=" + raw.take(240))
        assertTrue("响应应含 status 字段", raw.contains("\"status\""))
        assertTrue("签名被拒会返回 failed", !raw.contains("\"status\":\"failed\""))
    }
}
