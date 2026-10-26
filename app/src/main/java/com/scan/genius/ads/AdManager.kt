package com.scan.genius.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd

object AdManager {

    // Google Mobile Ads Official Test Ad Unit IDs
    const val BANNER_TEST_ID = "ca-app-pub-9771662959477951/3333003598"
    const val INTERSTITIAL_TEST_ID = "ca-app-pub-9771662959477951/3700669258"
    const val NATIVE_TEST_ID = "ca-app-pub-9771662959477951/5137661976"

    private var interstitialAd: InterstitialAd? = null
    private var scanCounter = 0

    fun init(context: Context) {
        preloadInterstitial(context)
    }

    /**
     * Creates and loads a standard 320x50 / adaptive banner ad.
     */
    fun createBannerAd(context: Context): AdView {
        val adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_TEST_ID
        }
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        return adView
    }

    /**
     * Loads a Native Advanced Ad with a listener callback.
     */
    fun loadNativeAd(context: Context, onAdLoaded: (NativeAd) -> Unit) {
        val adLoader = AdLoader.Builder(context, NATIVE_TEST_ID)
            .forNativeAd { nativeAd ->
                onAdLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Ad failed to load; gracefully ignored
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Preloads interstitial ad for instant display.
     */
    private fun preloadInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_TEST_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Called whenever a scan succeeds. Shows interstitial ad every 3rd scan.
     */
    fun onScanCompleted(activity: Activity) {
        scanCounter++
        if (scanCounter % 3 == 0) {
            interstitialAd?.let { ad ->
                ad.show(activity)
                interstitialAd = null
                preloadInterstitial(activity.applicationContext)
            } ?: run {
                preloadInterstitial(activity.applicationContext)
            }
        }
    }
}
