/*
 * Copyright (c) 2026. Fyber N.V
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fyber.fairbid.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.fyber.fairbid.ads.ImpressionData
import com.fyber.fairbid.ads.Native
import com.fyber.fairbid.ads.nativead.NativeAd
import com.fyber.fairbid.ads.nativead.NativeAdError
import com.fyber.fairbid.ads.nativead.NativeAdListener
import com.fyber.fairbid.ads.nativead.NativeAdLoadListener
import com.fyber.fairbid.ads.nativead.NativeAdView
import com.fyber.fairbid.utilities.OnScreenCallbacksHelper

private const val NATIVE_FRAGMENT_TAG = "NativeFragment"

/**
 * A Fragment demonstrating how to request, bind, and display a single Native ad slot using the FairBid SDK.
 */
class NativeFragment : Fragment(), OnScreenCallbacksHelper.LogsListener {

    companion object {
        /**
         * The Native placement name - as configured in Fyber console.
         * "3141034" can be used with the sample application configuration.
         * TODO change to your own configured placement.
         */
        private const val NATIVE_PLACEMENT_NAME = "3141034"
    }

    private lateinit var requestButton: View
    private lateinit var showOrDestroyButton: Button
    private lateinit var cleanCallBacks: Button
    private lateinit var nativeContainer: ViewGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var placementIcon: ImageView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var fragmentView: View? = null
    private var nativeAdView: NativeAdView? = null
    private var cachedNativeAd: NativeAd? = null
    private var isAdShowing = false
    private var isRequestInFlight = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.ad_container_fragment, container, false)
            fragmentView?.let {
                initializeUiElements(it)
            }
        }
        return fragmentView
    }

    private fun initializeUiElements(view: View) {
        initLogRecycler(view)
        initTextViews(view)
        initButtons(view)
        nativeContainer = view.findViewById(R.id.banner_container)
        nativeContainer.visibility = View.VISIBLE
        if (isRequestInFlight) {
            startRequestAnimation()
        }
    }

    private fun initLogRecycler(view: View) {
        recyclerView = view.findViewById(R.id.recycler_callbacks)
        OnScreenCallbacksHelper.configureRecycler(recyclerView, requireActivity(), this)
    }

    private fun initTextViews(view: View) {
        val placementName: TextView = view.findViewById(R.id.placement_name_tv)
        placementName.text = NATIVE_PLACEMENT_NAME
        val headerName: TextView = view.findViewById(R.id.fragment_header)
        headerName.text = getString(R.string.native_header_name)
        placementIcon = view.findViewById(R.id.placement_icon)
        placementIcon.setImageResource(R.drawable.fb_ic_native)
    }

    private fun initButtons(view: View) {
        progressBar = view.findViewById(R.id.progress_bar)
        val requestAdText: TextView = view.findViewById(R.id.request_ad)
        requestAdText.text = getString(R.string.request_ad)

        requestButton = view.findViewById(R.id.text_progress_bar)
        requestButton.setOnClickListener {
            requestNativeAd(NATIVE_PLACEMENT_NAME)
        }

        showOrDestroyButton = view.findViewById(R.id.show_ad)
        showOrDestroyButton.isEnabled = false
        showOrDestroyButton.text = getString(R.string.show_ad)
        showOrDestroyButton.setOnClickListener {
            val ad = cachedNativeAd
            if (ad != null) {
                renderNativeAd(ad)
            } else if (isAdShowing) {
                destroyNativeAd()
            }
        }

        val backButton: ImageView = view.findViewById(R.id.back_button)
        backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        cleanCallBacks = view.findViewById(R.id.clean_callback_button)
        cleanCallBacks.setOnClickListener {
            cleanCallBacks.isEnabled = false
            OnScreenCallbacksHelper.clearLog(recyclerView)
        }
    }

    /**
     * Step 1 & 4: Request a Native ad via [Native.request] and attach a [NativeAdListener] in [NativeAdLoadListener.onLoad].
     */
    private fun requestNativeAd(placementId: String) {
        if (isRequestInFlight) return
        Log.v(NATIVE_FRAGMENT_TAG, "requestNativeAd($placementId)")
        isRequestInFlight = true
        cachedNativeAd?.destroy()
        cachedNativeAd = null
        startRequestAnimation()

        Native.request(placementId, object : NativeAdLoadListener {
            override fun onRequestStart(placementId: String, requestId: String) {
                Log.v(NATIVE_FRAGMENT_TAG, "onRequestStart $placementId - $requestId")
                mainHandler.post {
                    if (view == null || !isAdded) return@post
                    OnScreenCallbacksHelper.logAndToast(recyclerView, context, OnScreenCallbacksHelper.ON_REQUEST_START)
                }
            }

            override fun onLoad(placementId: String, nativeAd: NativeAd) {
                Log.v(NATIVE_FRAGMENT_TAG, "onLoad $placementId")
                mainHandler.post {
                    isRequestInFlight = false
                    if (view == null || !isAdded) {
                        nativeAd.destroy()
                        return@post
                    }
                    nativeAd.adListener = createNativeAdListener()
                    cachedNativeAd?.destroy()
                    cachedNativeAd = nativeAd
                    OnScreenCallbacksHelper.logAndToast(recyclerView, context, OnScreenCallbacksHelper.ON_LOAD)
                    onRequestSuccessAnimation()
                }
            }

            override fun onFailedToLoad(placementId: String, error: NativeAdError) {
                Log.v(NATIVE_FRAGMENT_TAG, "onFailedToLoad $placementId, error: ${error.errorMessage}")
                mainHandler.post {
                    isRequestInFlight = false
                    if (view == null || !isAdded) return@post
                    OnScreenCallbacksHelper.logAndToast(
                        recyclerView,
                        context,
                        "${OnScreenCallbacksHelper.ON_FAILED_TO_LOAD}: ${error.errorMessage}"
                    )
                    resetRequestAnimation()
                }
            }
        })
    }

    private fun createNativeAdListener(): NativeAdListener = object : NativeAdListener {
        override fun onShow(placementId: String, impressionData: ImpressionData) {
            Log.v(NATIVE_FRAGMENT_TAG, "onShow $placementId")
            mainHandler.post {
                if (view == null || !isAdded) return@post
                OnScreenCallbacksHelper.logAndToast(recyclerView, context, OnScreenCallbacksHelper.ON_SHOW)
            }
        }

        override fun onClick(placementId: String) {
            Log.v(NATIVE_FRAGMENT_TAG, "onClick $placementId")
            mainHandler.post {
                if (view == null || !isAdded) return@post
                OnScreenCallbacksHelper.logAndToast(recyclerView, context, OnScreenCallbacksHelper.ON_CLICK)
            }
        }

        override fun onError(placementId: String, error: NativeAdError) {
            Log.v(NATIVE_FRAGMENT_TAG, "onError $placementId, error: ${error.errorMessage}")
            mainHandler.post {
                if (view == null || !isAdded) return@post
                OnScreenCallbacksHelper.logAndToast(
                    recyclerView,
                    context,
                    "${OnScreenCallbacksHelper.ON_ERROR}: ${error.errorMessage}"
                )
            }
        }
    }

    /**
     * Step 2: Wire asset sub-views on the inflated [NativeAdView].
     */
    private fun setupNativeAdView(root: View): NativeAdView {
        val adView = root.findViewById<NativeAdView>(R.id.native_ad_view)
        adView.titleView = root.findViewById(R.id.ad_headline)
        adView.bodyView = root.findViewById(R.id.ad_body)
        adView.callToActionView = root.findViewById(R.id.ad_call_to_action)
        adView.iconView = root.findViewById(R.id.ad_app_icon)
        adView.mediaView = root.findViewById(R.id.ad_media)
        adView.adChoicesView = root.findViewById(R.id.ad_choices)
        adView.advertiserView = root.findViewById(R.id.ad_advertiser)
        adView.storeView = root.findViewById(R.id.ad_store)
        adView.priceView = root.findViewById(R.id.ad_price)

        val starRatingBar = root.findViewById<RatingBar>(R.id.ad_stars)
        adView.starRatingViewFactory = NativeAdView.StarRatingViewFactory { rating ->
            starRatingBar.rating = rating.toFloat()
            starRatingBar.visibility = View.VISIBLE
            starRatingBar
        }
        return adView
    }

    /**
     * Step 3: Bind and render the [NativeAd] into a fresh [NativeAdView].
     */
    private fun renderNativeAd(nativeAd: NativeAd) {
        Log.v(NATIVE_FRAGMENT_TAG, "renderNativeAd()")
        val context = requireContext()

        // Clean up any previous bound ad view
        nativeContainer.removeAllViews()
        nativeAdView?.destroy()
        nativeAdView = null

        val adView = setupNativeAdView(
            layoutInflater.inflate(R.layout.native_ad_item, nativeContainer, false)
        )

        // Bind the native ad (ownership of nativeAd transfers to adView)
        cachedNativeAd = null
        adView.setNativeAd(nativeAd)

        // Guard optional assets per documentation (hide UI slots when null/blank)
        adView.iconView?.let {
            it.visibility = if (it.drawable == null) View.GONE else View.VISIBLE
        }
        setVisibleIfNotEmpty(adView.bodyView)
        setVisibleIfNotEmpty(adView.callToActionView)
        setVisibleIfNotEmpty(adView.advertiserView)
        setVisibleIfNotEmpty(adView.storeView)
        setVisibleIfNotEmpty(adView.priceView)

        nativeContainer.addView(adView)

        // Apply media aspect ratio after attaching to the container so width is measured
        nativeAd.mediaAspectRatio?.takeIf { it > 0 }?.let { ratio ->
            adView.mediaView?.let { mediaView ->
                mediaView.post {
                    val w = mediaView.width
                    if (w > 0) {
                        mediaView.updateLayoutParams {
                            height = (w / ratio).toInt()
                        }
                    }
                }
            }
        }

        nativeAdView = adView
        isAdShowing = true

        // Switch button to Destroy state
        showOrDestroyButton.text = getString(R.string.destroy)
        showOrDestroyButton.background = ContextCompat.getDrawable(context, R.drawable.button_effect_banner)
        showOrDestroyButton.isEnabled = true
    }

    /**
     * Step 5: Release the bound [NativeAdView] (and any loaded-but-unbound [NativeAd]).
     */
    private fun destroyNativeAd() {
        Log.v(NATIVE_FRAGMENT_TAG, "destroyNativeAd()")
        nativeContainer.removeAllViews()
        nativeAdView?.destroy() // Also destroys the bound NativeAd
        nativeAdView = null

        // NativeAd loaded but never bound to a view:
        cachedNativeAd?.destroy()
        cachedNativeAd = null
        isAdShowing = false

        context?.let { ctx ->
            showOrDestroyButton.text = getString(R.string.show_ad)
            showOrDestroyButton.background = ContextCompat.getDrawable(ctx, R.drawable.button_effect)
            showOrDestroyButton.isEnabled = false
        }
    }

    private fun setVisibleIfNotEmpty(view: TextView?) {
        view?.let {
            it.visibility = if (it.text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun startRequestAnimation() {
        progressBar.visibility = View.VISIBLE
        requestButton.isEnabled = false
        if (!isAdShowing) {
            showOrDestroyButton.isEnabled = false
        }
    }

    private fun onRequestSuccessAnimation() {
        progressBar.visibility = View.GONE
        requestButton.isEnabled = true
        showOrDestroyButton.text = getString(R.string.show_ad)
        showOrDestroyButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.button_effect)
        showOrDestroyButton.isEnabled = true
    }

    private fun resetRequestAnimation() {
        progressBar.visibility = View.GONE
        requestButton.isEnabled = true
        if (!isAdShowing) {
            showOrDestroyButton.isEnabled = false
        }
    }

    override fun onDestroyView() {
        destroyNativeAd()
        fragmentView = null
        super.onDestroyView()
    }

    override fun onFirstLogLine() {
        cleanCallBacks.isEnabled = true
    }
}
