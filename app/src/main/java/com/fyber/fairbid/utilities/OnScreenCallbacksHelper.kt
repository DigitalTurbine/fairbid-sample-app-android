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

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyber.fairbid.sample.R
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Utility helper, displays callback events on screen using a recycler view
 */
class OnScreenCallbacksHelper {

    /**
     * Interface for listeners which wants to be notified when the log is displaying something
     */
    interface LogsListener {
        /**
         * Invoked when the logs became non-empty
         */
        fun onFirstLogLine()
    }

    companion object {
        //Callbacks
        const val ON_SHOW = "onShow()"
        const val ON_CLICK = "onClick()"
        const val ON_HIDE = "onHide()"
        const val ON_REQUEST_START = "onRequestStart()"
        const val ON_SHOW_FAILURE = "onShowFailure()"
        const val ON_AVAILABLE = "onAvailable()"
        const val ON_UNAVAILABLE = "onUnavailable()"
        const val ON_AUDIO_START = "onAudioStart()"
        const val ON_AUDIO_FINISH = "onAudioFinish()"
        const val ON_COMPLETION = "onCompletion()"
        const val ON_ERROR = "onError()"
        const val ON_LOAD = "onLoad()"
        const val ON_FAILED_TO_LOAD = "onFailedToLoad()"

        /**
         * Configures the supplied recycler view to display supplied events.
         * @param recyclerView The target recycler view
         * @param activity the hosting activity for context
         * @param listener a listener for callbacks
         */
        fun configureRecycler(recyclerView: RecyclerView, activity: Activity, listener: LogsListener) {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(activity)
                val emptyLogRow = ArrayList<String>()
                adapter = LogsAdapter(emptyLogRow, listener)
            }
        }

        /**
         * Adds the supplied log string to the recycler view and presents a toast.
         * @param recyclerView The target recycler view
         * @param context the hosting context
         * @param log the relevant line to log
         */
        fun logAndToast(recyclerView: RecyclerView, context: Context?, log: String) {
            context?.let {
                Toast.makeText(it, log, Toast.LENGTH_SHORT).show()
            }
            val recyclerViewAdapter: LogsAdapter = recyclerView.adapter as LogsAdapter
            recyclerViewAdapter.addLog(log)
        }

        /**
         * Clears the recycler view from any presented item
         * @param recyclerView the target recycler view
         */
        fun clearLog(recyclerView: RecyclerView) {
            val recyclerViewAdapter: LogsAdapter = recyclerView.adapter as LogsAdapter
            recyclerViewAdapter.clearList()
        }
    }

    /**
     * A view holder for log lines
     */
    class LogDataHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.item_log_row, parent, false)) {

        private var logTextView: TextView = itemView.findViewById(R.id.row_log)

        fun bind(text: String) {
            logTextView.text = text
        }
    }

    /**
     * An adapter for the log lines.
     */
    class LogsAdapter(private val logsList: ArrayList<String>, private var listener: LogsListener) : RecyclerView.Adapter<LogDataHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogDataHolder {
            val inflater = LayoutInflater.from(parent.context)
            return LogDataHolder(inflater, parent)
        }

        override fun onBindViewHolder(holder: LogDataHolder, position: Int) {
            holder.bind(logsList[position])
        }

        override fun getItemCount(): Int = logsList.size

        fun addLog(log: String) {
            if (logsList.isEmpty()) {
                listener.onFirstLogLine()
            }
            logsList.add("${getCurrentTime()} - $log")
            notifyDataSetChanged()
        }

        fun clearList() {
            logsList.clear()
            notifyDataSetChanged()
        }

        private fun getCurrentTime(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val current = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            current.format(formatter)
        } else {
            val date = Date();
            val formatter = SimpleDateFormat("HH:mm:ss")
            formatter.format(date)
        }
    }
}
