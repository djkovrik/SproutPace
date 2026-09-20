package com.sedsoftware.sproutpace.monetization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class IosAdsDisabledTest {
    @Test
    fun iosAdvertisingDriverIsUnconditionallyDisabled() {
        assertFalse(IosSafeDisabledAdSdkDriver.supported)
        assertEquals(AdPlatform.IOS, IosSafeDisabledAdSdkDriver.platform)
        assertNull(
            AdConfiguration(
                platform = AdPlatform.IOS,
                hostMode = AdHostMode.PRODUCTION,
                productionAdUnitId = null,
            ).effectiveAdUnitId,
        )
    }
}
