package com.heartguard.watch.service

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DataLayerListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "DataLayerListenerWatch"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                Log.d(TAG, "Data changed on path: $path")

                when (path) {
                    "/settings_sync" -> handleSettingsUpdate(event.dataItem)
                }
            }
        }
    }

    private fun handleSettingsUpdate(dataItem: com.google.android.gms.wearable.DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        Log.d(TAG, "Settings updated from phone")
    }
}
