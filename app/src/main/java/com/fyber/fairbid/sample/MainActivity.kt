/*
 * Copyright (c) 2022. Fyber N.V
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

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.fyber.FairBid
import com.fyber.fairbid.utilities.MainFragment
import com.fyber.fairbid.utilities.MainFragment.UnitType
import com.fyber.fairbid.utilities.SplashScreenFragment


/**
 * The Main Activity,
 * responsible for starting the FairBid SDK and displaying the different ads - banner, interstitial, rewarded
 */
class MainActivity : MainFragment.FragmentListener, AppCompatActivity() {

    companion object {
        /**
         * The app id provided through the Fyber console
         * "109613" can be used a sample application.
         * TODO replace with your own app id.
         */
        private const val PUBLISHERS_APP_ID = "109613"
    }


    private val bannerFragment = BannerFragment.createInstance(UnitType.Banner)
    private val mrecFragment = BannerFragment.createInstance(UnitType.Mrec)
    private val rewardedFragment = RewardedFragment()
    private val interstitialFragment = InterstitialFragment()
    private val nativeFragment = NativeFragment()
    private val nativeFeedFragment = NativeFeedFragment()
    private val mainFragment = MainFragment()
    private var shouldSplashScreen = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        splashScreen()
        startFairBidSdk(PUBLISHERS_APP_ID)
    }

    /**
     * Helper method for initializing the SDK with the given app id
     * @param appId The app id provided through the Fyber console
     */
    private fun startFairBidSdk(appId: String) {
        val fairBid = FairBid.configureForAppId(appId).enableLogs()
        fairBid.start(this)
    }

    /**
     * Helper method for showing the test suite
     * @param activity The activity provided by the publisher
     */
    private fun showTestSuite(activity: Activity) {
        FairBid.showTestSuite(activity)
    }

    /**
     * Shows the splash screen for 2000 millis
     */
    private fun splashScreen() {
        supportFragmentManager.beginTransaction().add(
            R.id.fragment_container,
            SplashScreenFragment()
        ).commit()
        Handler(Looper.getMainLooper()).postDelayed({
            if (shouldSplashScreen) {
                supportFragmentManager.beginTransaction().setCustomAnimations(androidx.appcompat.R.anim.abc_fade_in, androidx.appcompat.R.anim.abc_fade_out)
                    .replace(R.id.fragment_container, mainFragment).commitAllowingStateLoss()
                shouldSplashScreen = false
            }
        }, 2000)
    }

    /**
     * Invoked by the MainFragment, telling us which fragment to present.
     */
    override fun onButtonClicked(unitType: UnitType) {
        when (unitType) {
            UnitType.Banner -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, bannerFragment)
                    .addToBackStack(null).commit()
            }
            UnitType.Mrec -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, mrecFragment)
                    .addToBackStack(null).commit()
            }
            UnitType.Rewarded -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, rewardedFragment)
                    .addToBackStack(null).commit()
            }
            UnitType.Interstitial -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, interstitialFragment)
                    .addToBackStack(null).commit()
            }
            UnitType.Native -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, nativeFragment)
                    .addToBackStack(null).commit()
            }
            UnitType.NativeFeed -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, nativeFeedFragment)
                    .addToBackStack(null).commit()
            }
            UnitType.TestSuite -> {
                showTestSuite(this)
            }
        }
    }
}
