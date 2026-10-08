/*
 * Copyright (C) 2026 The Resurrection Remix Project
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
package com.android.quickstep.util;

import android.content.Context;
import android.provider.Settings;

import com.android.quickstep.SystemUiProxy;

/**
 * The Recents choice in Launcher settings. While slim or spin recents is picked, SystemUI shows
 * it where a gesture would open the overview.
 */
public final class CustomRecents {
    /** Settings.System int: 0 launcher overview, 1 slim recents, 2 spin recents. */
    public static final String KEY = "recents_style";

    public static final int LAUNCHER = 0;
    public static final int SLIM = 1;
    public static final int SPIN = 2;

    private CustomRecents() {}

    public static boolean isEnabled(Context context) {
        return style(context) != LAUNCHER;
    }

    public static int style(Context context) {
        int style = Settings.System.getInt(context.getContentResolver(), KEY, -1);
        if (style < LAUNCHER || style > SPIN) {
            // The first wheel used slim_recents_enabled. That wheel is spin now.
            style = Settings.System.getInt(context.getContentResolver(),
                    "slim_recents_enabled", 0) == 1 ? SPIN : LAUNCHER;
        }
        return style;
    }

    public static void show(Context context) {
        SystemUiProxy.INSTANCE.get(context).showCustomRecents();
    }
}
