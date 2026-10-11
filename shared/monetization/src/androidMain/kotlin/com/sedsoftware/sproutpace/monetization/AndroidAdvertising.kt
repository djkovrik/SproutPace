package com.sedsoftware.sproutpace.monetization

import android.content.Context
import android.os.Looper
import android.view.View
import com.sedsoftware.sproutpace.data.ProductSettings
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidYandexAdsDriver(context: Context) : AdSdkDriver {
    override val platform = AdPlatform.ANDROID
    override val supported = true
    private val applicationContext = context.applicationContext
    private val banners = linkedSetOf<BannerAdView>()

    override suspend fun setUserConsent(consent: Boolean) = onMainThread {
        YandexAds.setUserConsent(consent)
    }

    override suspend fun initialize(): Unit = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            YandexAds.initialize(applicationContext) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    override suspend fun stopNewRequestsAndDispose() = onMainThread {
        banners.forEach(BannerAdView::destroy)
        banners.clear()
    }

    fun createInlineBanner(
        context: Context,
        adUnitId: String,
        widthDp: Int,
        maxHeightDp: Int,
        session: InlineAdSession,
        isHostDestroyed: () -> Boolean,
        onStateChanged: (InlineAdState) -> Unit = {},
    ): BannerAdView {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Yandex Ads must be used on the main thread" }
        require(AdConfiguration.isProductionAdUnitId(adUnitId) || adUnitId == AdConfiguration.ANDROID_DEMO_BANNER_ID)
        require(widthDp > 0 && maxHeightDp >= MIN_INLINE_BANNER_HEIGHT_DP)
        check(session.beginLoad()) { "Inline ad session cannot start another load" }
        return BannerAdView(context).also { banner ->
            banners += banner
            banner.setAdSize(BannerAdSize.inline(context, widthDp, maxHeightDp))
            banner.setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    if (isHostDestroyed()) {
                        banner.destroy()
                        banners -= banner
                        session.dispose()
                    } else {
                        session.loaded()
                    }
                    onStateChanged(session.state)
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    session.failed()
                    onStateChanged(session.state)
                }

                override fun onAdClicked() = Unit
                override fun onImpression(impressionData: ImpressionData?) = Unit
            })
            onStateChanged(session.state)
            banner.loadAd(AdRequest.Builder(adUnitId).build())
        }
    }

    fun disposeBanner(banner: BannerAdView, session: InlineAdSession) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Yandex Ads must be disposed on the main thread" }
        banner.destroy()
        banners -= banner
        session.dispose()
    }

    private suspend fun onMainThread(block: () -> Unit) = withContext(Dispatchers.Main.immediate) { block() }

    private companion object {
        const val MIN_INLINE_BANNER_HEIGHT_DP = 50
    }
}

class AndroidAdvertisingRuntime(
    context: Context,
    appId: String,
    appVersion: String,
    configuration: AdConfiguration,
    private val productSettings: ProductSettings,
    clock: EpochMillisClock = EpochMillisClock { System.currentTimeMillis() },
    workspaceReady: StateFlow<Boolean> = MutableStateFlow(true),
) : AdvertisingRuntime {
    private val connectivity = AndroidAdConnectivity(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val networkAvailable = combine(connectivity.available, workspaceReady) { connected, ready ->
        connected && ready
    }.stateIn(scope, SharingStarted.Eagerly, false)
    private val httpClient = HttpClient(OkHttp) { configurePrivacyRegionClient() }
    val sdk = AndroidYandexAdsDriver(context)
    val privacy = PrivacyCoordinator(
        api = KtorPrivacyRegionApi(httpClient, clock, "$appId/$appVersion"),
        repository = PrivacyRepository(ProductSettingsPrivacyStore(productSettings)),
        clock = clock,
    )
    val controller = AdvertisingController(privacy, sdk, configuration)
    override val privacyState = productSettings.privacyState

    override suspend fun prepare(request: AdPlacementRequest) = controller.prepare(
        request.copy(offline = request.offline || !networkAvailable.value),
    )
    override suspend fun acceptConsent() = controller.acceptConsent()
    override suspend fun declineConsent() = controller.declineConsent()
    override suspend fun withdrawConsent() = controller.withdrawConsent()

    fun createInlineBanner(
        context: Context,
        adUnitId: String,
        widthDp: Int,
        maxHeightDp: Int,
        session: InlineAdSession,
        isHostDestroyed: () -> Boolean,
        onStateChanged: (InlineAdState) -> Unit,
    ): View = sdk.createInlineBanner(
        context,
        adUnitId,
        widthDp,
        maxHeightDp,
        session,
        isHostDestroyed,
        onStateChanged,
    )

    fun disposeInlineBanner(view: View, session: InlineAdSession) {
        (view as? BannerAdView)?.let { sdk.disposeBanner(it, session) } ?: session.dispose()
    }

    override fun close() {
        connectivity.close()
        scope.cancel()
        httpClient.close()
    }
}
