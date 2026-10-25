package com.aselens.kovanllm.ads

/**
 * AdMob Configuration for KovanLLM.
 *
 * Configured with official AdMob production IDs from Aselens Software.
 */
object AdConfig {
    var isAdsEnabled: Boolean = true

    // Official Google AdMob App ID
    const val APP_ID: String = "ca-app-pub-3011996181892691~4942363076"

    // Official AdMob Unit IDs
    const val PROD_BANNER_ID: String = "ca-app-pub-3011996181892691/3266941444"
    const val PROD_CHAT_INLINE_ID: String = "ca-app-pub-3011996181892691/2811023750"

    // Test IDs for development fallbacks
    const val TEST_BANNER_ID: String = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID: String = "ca-app-pub-3940256099942544/1033173712"

    // Active Ad Unit IDs
    var bannerAdUnitId: String = PROD_BANNER_ID
    var chatInlineAdUnitId: String = PROD_CHAT_INLINE_ID
    var interstitialAdUnitId: String = TEST_INTERSTITIAL_ID

    // Show an inline ad box after every N messages in chat
    const val INLINE_AD_FREQUENCY: Int = 4

    // Frequency of interstitial ads
    const val INTERSTITIAL_INTERVAL: Int = 3
}
