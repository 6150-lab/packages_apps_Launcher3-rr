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
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

abstract class BaseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        onWidgetUpdate(context)
        schedulePolling(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePolling(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelPolling()
    }

    /**
     * RR: one loop per provider class, because the system makes a new provider per broadcast. It
     * stays off the main thread and sleeps with the screen.
     */
    private fun schedulePolling(context: Context) {
        cancelPolling()
        val powerManager = context.getSystemService(PowerManager::class.java)
        jobs[javaClass] = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (powerManager.isInteractive) {
                    performScheduledTask(context)
                }
            }
        }
    }

    private fun cancelPolling() {
        jobs.remove(javaClass)?.cancel()
    }

    abstract fun getLayoutId(): Int

    abstract fun onWidgetUpdate(context: Context)

    open suspend fun performScheduledTask(context: Context) {}

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        val jobs = ConcurrentHashMap<Class<*>, Job>()
    }
}
