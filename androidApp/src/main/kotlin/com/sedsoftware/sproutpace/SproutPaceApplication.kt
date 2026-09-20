package com.sedsoftware.sproutpace

import android.app.Application
import com.sedsoftware.sproutpace.compose.AndroidProductionAppGraph
import com.sedsoftware.sproutpace.monetization.AdConfiguration
import com.sedsoftware.sproutpace.monetization.AdHostMode
import com.sedsoftware.sproutpace.monetization.AdPlatform

class SproutPaceApplication : Application() {
    lateinit var graph: AndroidProductionAppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AndroidProductionAppGraph(
            context = applicationContext,
            isDebug = BuildConfig.DEBUG,
            appId = packageName,
            appVersion = BuildConfig.VERSION_NAME,
            adConfiguration = AdConfiguration(
                platform = AdPlatform.ANDROID,
                hostMode = if (BuildConfig.YANDEX_ADS_DEBUG) AdHostMode.DEBUG else AdHostMode.PRODUCTION,
                productionAdUnitId = BuildConfig.YANDEX_AD_UNIT_ID,
            ),
        )
    }

    override fun onTerminate() {
        graph.close()
        super.onTerminate()
    }
}
