package com.aselens.kovanllm.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdMobInterstitialManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var actionCounter = 0

    fun loadAd(context: Context) {
        if (!AdConfig.isAdsEnabled || interstitialAd != null || isLoading) return

        isLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdConfig.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Checks frequency counter and shows ad if due.
     */
    fun showOnTransition(activity: Activity, onFinished: (() -> Unit)? = null) {
        actionCounter++
        if (actionCounter >= AdConfig.INTERSTITIAL_INTERVAL) {
            actionCounter = 0
            showAd(activity, onFinished)
        } else {
            onFinished?.invoke()
        }
    }

    fun showAd(activity: Activity, onFinished: (() -> Unit)? = null) {
        val ad = interstitialAd
        if (ad != null && AdConfig.isAdsEnabled) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadAd(activity)
                    onFinished?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadAd(activity)
                    onFinished?.invoke()
                }
            }
            ad.show(activity)
        } else {
            loadAd(activity)
            onFinished?.invoke()
        }
    }
}
