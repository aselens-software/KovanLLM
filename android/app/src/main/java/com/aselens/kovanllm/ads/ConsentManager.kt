package com.aselens.kovanllm.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google User Messaging Platform (UMP) consent manager.
 * Gathers user consent for European Economic Area (EEA), UK, and KVKK regulations
 * prior to serving AdMob banner and inline chat advertisements.
 */
class ConsentManager(private val context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    fun requestConsent(activity: Activity, onConsentCompleted: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity
                ) { _ ->
                    onConsentCompleted()
                }
            },
            { _ ->
                onConsentCompleted()
            }
        )

        if (consentInformation.canRequestAds()) {
            onConsentCompleted()
        }
    }

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()
}
