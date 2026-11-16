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
package com.fyber.fairbid.utilities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyber.FairBid
import com.fyber.fairbid.sample.R

/**
 * Internal class for displaying the sample with multiple choices.
 * This fragment does not contain any sample code for FairBid
 */
class MainFragment : Fragment() {

    /**
     * interface for letting the activity know when the user has made some choice
     */
    interface FragmentListener {
        /**
         * Called when the user has clicked some unit type.
         * @param unitType The clicked unit type
         */
        fun onButtonClicked(unitType: UnitType)
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var fairBidVersionTextView: TextView


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: View = inflater.inflate(R.layout.main_fragment, container, false)
        initializeRecyclerView(view)
        initVersionTextView(view)
        return view
    }

    /**
     * Initializes the recycler view
     * @param view the container view for this fragment
     */
    private fun initializeRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.units_recycler)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = ListAdapter(mUnits, if (context is FragmentListener) context as FragmentListener else null)
        }
    }

    /**
     * Initialize the version text view and set it with the proper text value
     * @param view the container view for this fragment
     */
    private fun initVersionTextView(view: View) {
        fairBidVersionTextView = view.findViewById(R.id.fairbid_version)
        fairBidVersionTextView.text = String.format("%s %s", fairBidVersionTextView.text, FairBid.SDK_VERSION)
    }

    /**
     * The view holder for rows/items in the recycler view
     */
    class UnitRowDataHolder(inflater: LayoutInflater, parent: ViewGroup, var mListener: FragmentListener?) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.item_unit_row, parent, false)) {

        private var unitIcon: ImageView = itemView.findViewById(R.id.row_unit_image)
        private var unitText: TextView = itemView.findViewById(R.id.row_text_unit)
        private var rightArrow: ImageView = itemView.findViewById(R.id.right_unit_arrow)

        fun bind(unitRowData: UnitRowData) {
            unitIcon.background = ContextCompat.getDrawable(itemView.context, unitRowData.resourceImage)
            unitText.text = unitRowData.unitText
            rightArrow.setOnClickListener {
                mListener?.onButtonClicked(unitRowData.unitType)
            }
        }
    }

    /**
     * The view holder for separators/dividers
     */
    class SeparatorViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.item_separator_row, parent, false))

    /**
     * Enum to help differentiate between items and separators
     */
    enum class RowType { Row, Separator }

    /**
     * Enum describing the possible choices in the sample application
     */
    enum class UnitType { Interstitial, Rewarded, Banner, Mrec, Native, NativeFeed, TestSuite }

    /**
     * a model for the displayed items
     * @param unitText the display name for this row
     * @param resourceImage the image to display for this row
     * @param unitType the corresponding {@link UnitType}
     */
    data class UnitRowData(val unitText: String, val resourceImage: Int, val unitType: UnitType)

    /**
     * a model for the rows inside the recycler view
     * @param type the Row Type
     * @property payload the object describing this row, if any
     */
    data class Row(val type: RowType = RowType.Row, val payload: Any? = null)

    /**
     * A simple list to contain the choices in the recycler view
     */
    private val mUnits = listOf(
        Row(
            payload = UnitRowData(
                "Banner",
                R.drawable.fb_ic_banner,
                UnitType.Banner
            )
        ),
        Row(
            payload = UnitRowData(
                "MREC Banner",
                R.drawable.fb_ic_mrec,
                UnitType.Mrec
            )
        ),
        Row(
            payload = UnitRowData(
                "Interstitial",
                R.drawable.fb_ic_interstitial,
                UnitType.Interstitial
            )
        ),
        Row(
            payload = UnitRowData(
                "Rewarded",
                R.drawable.fb_ic_rewarded,
                UnitType.Rewarded
            )
        ),
        Row(
            payload = UnitRowData(
                "Native",
                R.drawable.fb_ic_native,
                UnitType.Native
            )
        ),
        Row(
            payload = UnitRowData(
                "Native Feed",
                R.drawable.fb_ic_native_feed,
                UnitType.NativeFeed
            )
        ),
        Row(type = RowType.Separator),
        Row(
            payload = UnitRowData(
                "Test Suite",
                R.drawable.fb_ic_test_suite,
                UnitType.TestSuite
            )
        )
    )

    /**
     * The adapter for recycler view
     */
    class ListAdapter(private val list: List<Row>, var mListener: FragmentListener?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == RowType.Row.ordinal) {
                UnitRowDataHolder(inflater, parent, mListener)
            } else {
                SeparatorViewHolder(inflater, parent)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return list[position].type.ordinal
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

            if (getItemViewType(position) == RowType.Row.ordinal) {

                val unitRowData: UnitRowData = list[position].payload as UnitRowData
                (holder as UnitRowDataHolder).bind(unitRowData)
                holder.itemView.setOnClickListener {
                    mListener?.onButtonClicked((list[position].payload as UnitRowData).unitType)
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }

}