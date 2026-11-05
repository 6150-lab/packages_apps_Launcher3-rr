/*
 * Copyright (C) 2023-2025 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import java.util.concurrent.ConcurrentHashMap
import android.widget.RemoteViews

import kotlinx.coroutines.*

abstract class BaseToggleWidgetProvider : AppWidgetProvider() {

    abstract fun getToggleActionIntent(context: Context, appWidgetId: Int): Intent
    abstract fun isServiceActive(context: Context): Boolean
    abstract fun getLayoutId(): Int
    abstract fun updateWidget(context: Context) 
    abstract fun onUpdate(context: Context) 

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidget(context)
        onUpdate(context)
        schedulePolling(context)
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePolling(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopPolling()
    }

    /** RR: one loop per provider class, because the system makes a new provider per broadcast. */
    protected fun schedulePolling(context: Context) {
        stopPolling()
        val powerManager = context.getSystemService(PowerManager::class.java)
        jobs[javaClass] = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (powerManager.isInteractive) {
                    updateWidget(context)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        jobs.remove(javaClass)?.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3000L
        val jobs = ConcurrentHashMap<Class<*>, Job>()
    }
}
