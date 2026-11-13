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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyber.fairbid.ads.ImpressionData
import com.fyber.fairbid.ads.Native
import com.fyber.fairbid.ads.nativead.NativeAd
import com.fyber.fairbid.ads.nativead.NativeAdError
import com.fyber.fairbid.ads.nativead.NativeAdListener
import com.fyber.fairbid.ads.nativead.NativeAdLoadListener
import com.fyber.fairbid.ads.nativead.NativeAdView

/**
 * Demonstrates integrating FairBid Native Ads into a scrollable list ([RecyclerView]).
 *
 * Key Integration Patterns:
 * 1. Keep One Ready Ad per Placement: Request an ad for each placement when the screen opens
 *    and hold the loaded ad in [readyAds] until an ad row needs it.
 * 2. Request on Impression: Take the ready ad when binding a row ([takeAdView]) and request
 *    the next ad only from [NativeAdListener.onShow] — never from [RecyclerView.Adapter.onBindViewHolder]
 *    or scroll events. Retry no-fill failures with exponential backoff.
 * 3. Destroy on Recycle: When a row is recycled ([RecyclerView.Adapter.onViewRecycled]),
 *    destroy its [NativeAdView] (which also destroys the bound [NativeAd]). Never reuse or
 *    re-bind a [NativeAd] or [NativeAdView] across rows.
 * 4. Clean Teardown: In [onDestroyView], detach the adapter (recycling all rows and destroying
 *    their bound ads) and destroy any ready ads in [readyAds] that were never bound.
 */
class NativeFeedFragment : Fragment() {

    companion object {
        private const val PLACEMENT_ID = "2965083"
        private const val AD_FREQUENCY = 8 // Space ad slots apart so two slots for the same placement aren't on screen together
        private const val TOTAL_ITEMS = 40
        private const val INITIAL_RETRY_DELAY_MS = 2_000L
        private const val MAX_RETRY_DELAY_MS = 32_000L
    }

    private var recyclerView: RecyclerView? = null
    private var statusPlacementTv: TextView? = null
    private var statusReadyTv: TextView? = null
    private var feedAdapter: NativeFeedAdapter? = null

    // One loaded ad per placement
    private val readyAds = mutableMapOf<String, NativeAd>()
    private val inFlightPlacements = mutableSetOf<String>()
    private val retryDelaysMs = mutableMapOf<String, Long>()
    private val impressedAdViews = mutableSetOf<NativeAdView>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_native_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.feed_back_button).setOnClickListener {
            activity?.onBackPressed()
        }

        statusPlacementTv = view.findViewById(R.id.feed_status_placement)
        statusReadyTv = view.findViewById(R.id.feed_status_ready)
        statusPlacementTv?.text = "Placement: $PLACEMENT_ID"

        val rv = view.findViewById<RecyclerView>(R.id.feed_recycler_view)
        recyclerView = rv
        rv.layoutManager = LinearLayoutManager(context).apply {
            // Bind ad rows only when they enter the screen so off-screen prefetch doesn't consume readyAds
            isItemPrefetchEnabled = false
        }

        val feedItems = generateFeedItems(TOTAL_ITEMS)
        feedAdapter = NativeFeedAdapter(
            items = feedItems,
            takeAdView = { placementId -> takeAdView(placementId) },
            onRecycleAdHolder = { holder -> recycleAdHolder(holder) }
        )
        rv.adapter = feedAdapter

        updateReadyStatus()

        // Prime the placement when the screen opens
        requestAd(PLACEMENT_ID)
    }

    private fun requestAd(placementId: String) {
        // A request owns its placement until it finishes; keep at most one ready ad per placement
        if (placementId in inFlightPlacements || readyAds.containsKey(placementId)) return
        inFlightPlacements.add(placementId)

        Native.request(placementId, object : NativeAdLoadListener {
            override fun onLoad(placementId: String, nativeAd: NativeAd) {
                mainHandler.post {
                    inFlightPlacements.remove(placementId)
                    if (view == null || !isAdded) {
                        nativeAd.destroy()
                        return@post
                    }
                    retryDelaysMs.remove(placementId)
                    readyAds.put(placementId, nativeAd)?.destroy()
                    updateReadyStatus()
                    bindVisibleWaitingRow(placementId)
                }
            }

            override fun onFailedToLoad(placementId: String, error: NativeAdError) {
                mainHandler.post {
                    inFlightPlacements.remove(placementId)
                    if (view == null || !isAdded) return@post
                    // No fill. Retry with exponential backoff (2s, 4s, 8s, 16s, 32s), not from a scroll event.
                    val delayMs = retryDelaysMs[placementId] ?: INITIAL_RETRY_DELAY_MS
                    retryDelaysMs[placementId] = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    mainHandler.postDelayed({
                        if (view != null && isAdded) requestAd(placementId)
                    }, delayMs)
                }
            }

            override fun onRequestStart(placementId: String, requestId: String) {}
        })
    }

    /**
     * Called from [NativeFeedAdapter.onBindViewHolder]: binds the ready ad to a fresh [NativeAdView],
     * or returns null if no ad is ready yet.
     */
    fun takeAdView(placementId: String): NativeAdView? {
        val nativeAd = readyAds.remove(placementId) ?: return null
        updateReadyStatus()

        val adView = makeAdView()
        nativeAd.adListener = object : NativeAdListener {
            override fun onShow(placementId: String, impressionData: ImpressionData) {
                mainHandler.post {
                    if (view == null || !isAdded) return@post
                    impressedAdViews.add(adView)
                    context?.let {
                        Toast.makeText(it, "Native Feed onShow ($placementId)", Toast.LENGTH_SHORT).show()
                    }
                    // Request the next ad only after this one is seen
                    requestAd(placementId)
                }
            }

            override fun onClick(placementId: String) {
                mainHandler.post {
                    if (view == null || !isAdded) return@post
                    context?.let {
                        Toast.makeText(it, "Native Feed onClick ($placementId)", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(placementId: String, error: NativeAdError) {
                mainHandler.post {
                    if (view == null || !isAdded) return@post
                    context?.let {
                        Toast.makeText(it, "Native Feed onError: ${error.errorMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        adView.setNativeAd(nativeAd)
        applyOptionalVisibility(adView)

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
        return adView
    }

    private fun recycleAdHolder(holder: NativeAdViewHolder) {
        val recycledView = holder.adView
        val placementId = holder.placementId
        holder.destroyAdView()
        if (recycledView != null) {
            val wasImpressed = impressedAdViews.remove(recycledView)
            val isTearingDown = view == null || recyclerView?.adapter == null
            if (!wasImpressed && !isTearingDown && placementId != null) {
                // Row was flung past and destroyed before onShow fired; request a fresh ad
                requestAd(placementId)
            }
        }
    }

    private fun makeAdView(): NativeAdView {
        val root = layoutInflater.inflate(R.layout.native_ad_item, recyclerView, false)
        return setupNativeAdView(root)
    }

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

    private fun applyOptionalVisibility(adView: NativeAdView) {
        adView.iconView?.let {
            it.visibility = if (it.drawable == null) View.GONE else View.VISIBLE
        }
        adView.bodyView?.visibility =
            if (adView.bodyView?.text.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.callToActionView?.visibility =
            if (adView.callToActionView?.text.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.advertiserView?.visibility =
            if (adView.advertiserView?.text.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.storeView?.visibility =
            if (adView.storeView?.text.isNullOrBlank()) View.GONE else View.VISIBLE
        adView.priceView?.visibility =
            if (adView.priceView?.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun bindVisibleWaitingRow(placementId: String) {
        val rv = recyclerView ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        val adapter = feedAdapter ?: return
        for (i in first..last) {
            val item = adapter.items.getOrNull(i)
            if (item is FeedItem.AdSlot && item.placementId == placementId) {
                val holder = rv.findViewHolderForAdapterPosition(i) as? NativeAdViewHolder
                if (holder != null && holder.adView == null) {
                    val adView = takeAdView(placementId) ?: return
                    holder.attachAdView(adView, placementId)
                    return
                }
            }
        }
    }

    private fun updateReadyStatus() {
        statusReadyTv?.text = "Ready Ads: ${readyAds.size}"
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null)
        recyclerView?.adapter = null             // Recycles every row, which destroys its ad
        readyAds.values.forEach { it.destroy() } // Loaded but never bound
        readyAds.clear()
        inFlightPlacements.clear()
        impressedAdViews.clear()
        retryDelaysMs.clear()

        recyclerView = null
        feedAdapter = null
        statusPlacementTv = null
        statusReadyTv = null
        super.onDestroyView()
    }

    private fun generateFeedItems(count: Int): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        var adSlotIndex = 0
        val sampleHeadlines = listOf(
            "FairBid Adds Native Ads Mediation Support",
            "How Programmatic Header Bidding Shapes In-App Monetization",
            "Top UX Best Practices for Mobile In-Feed Advertising",
            "Impression-Driven Native Ad Chaining in Scrollable Lists",
            "Mobile Ad Trends: Interactive Creatives Drive Higher CTR",
            "Digital Turbine Enhances Mediation Platform for Global Publishers",
            "Understanding Waterfall Auctions and Hybrid Bidding Workflows",
            "Clean View Lifecycle Management in Android RecyclerView Feeds"
        )
        val sampleSnippets = listOf(
            "Explore how publishers can dynamically integrate native ads seamlessly matching their app layout.",
            "Real-time unified auctions reduce latency and maximize publisher eCPM yields.",
            "Retain user engagement by balancing ad frequency and consistent design themes.",
            "Keep one ready ad per placement and request the next ad only after an impression is recorded.",
            "Higher user satisfaction with non-intrusive in-feed native sponsored placements.",
            "Industry leading ad infrastructure trusted by thousands of mobile gaming studios.",
            "How header bidding complements programmatic mediation networks.",
            "Destroy bound NativeAdViews when rows are recycled and clean up unbound ads on screen exit."
        )

        for (i in 1..count) {
            if (i >= 2 && (i - 2) % AD_FREQUENCY == 0) {
                items.add(FeedItem.AdSlot(slotId = adSlotIndex++, placementId = PLACEMENT_ID))
            } else {
                val headline = sampleHeadlines[(i - 1) % sampleHeadlines.size]
                val snippet = sampleSnippets[(i - 1) % sampleSnippets.size]
                items.add(FeedItem.Content(id = i, title = headline, snippet = snippet, time = "${i * 3}m ago"))
            }
        }
        return items
    }

    sealed class FeedItem {
        data class Content(val id: Int, val title: String, val snippet: String, val time: String) : FeedItem()
        data class AdSlot(val slotId: Int, val placementId: String) : FeedItem()
    }

    private class NativeFeedAdapter(
        val items: List<FeedItem>,
        private val takeAdView: (placementId: String) -> NativeAdView?,
        private val onRecycleAdHolder: (NativeAdViewHolder) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_CONTENT = 0
            private const val TYPE_AD = 1
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is FeedItem.Content -> TYPE_CONTENT
            is FeedItem.AdSlot -> TYPE_AD
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_CONTENT) {
                ContentViewHolder(inflater.inflate(R.layout.item_feed_content, parent, false))
            } else {
                NativeAdViewHolder(inflater.inflate(R.layout.item_feed_ad, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is FeedItem.Content -> (holder as ContentViewHolder).bind(item)
                is FeedItem.AdSlot -> {
                    val adHolder = holder as NativeAdViewHolder
                    if (adHolder.adView == null) {
                        // No ready ad: collapse the row smoothly (do not request here).
                        val adView = takeAdView(item.placementId)
                        if (adView != null) {
                            adHolder.attachAdView(adView, item.placementId)
                        } else {
                            adHolder.collapse()
                        }
                    }
                }
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            if (holder is NativeAdViewHolder) {
                onRecycleAdHolder(holder)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTv: TextView = itemView.findViewById(R.id.content_title)
        private val snippetTv: TextView = itemView.findViewById(R.id.content_snippet)
        private val timeTv: TextView = itemView.findViewById(R.id.content_time)

        fun bind(item: FeedItem.Content) {
            titleTv.text = item.title
            snippetTv.text = item.snippet
            timeTv.text = item.time
        }
    }

    class NativeAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val adContainer: FrameLayout = itemView.findViewById(R.id.feed_ad_container)
        private val sponsoredLabel: TextView = itemView.findViewById(R.id.feed_ad_sponsored_label)
        var adView: NativeAdView? = null
            private set
        var placementId: String? = null
            private set

        fun attachAdView(adView: NativeAdView, placementId: String) {
            itemView.visibility = View.VISIBLE
            sponsoredLabel.visibility = View.VISIBLE
            this.adView = adView
            this.placementId = placementId
            adContainer.removeAllViews()
            adContainer.addView(adView)
        }

        fun collapse() {
            adContainer.removeAllViews()
            sponsoredLabel.visibility = View.GONE
            itemView.visibility = View.GONE
        }

        fun destroyAdView() {
            adContainer.removeAllViews()
            adView?.destroy() // Also destroys the bound NativeAd
            adView = null
            placementId = null
        }
    }
}
