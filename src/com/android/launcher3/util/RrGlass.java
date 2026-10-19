/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Liquid Glass (Remixer): which Launcher surfaces may blur. */
public final class RrGlass {
    /** Folders and the long-press popup. */
    public static final String HOME = "rr_glass_home";
    /** Overview and the app drawer. */
    public static final String RECENTS = "rr_glass_recents";

    private RrGlass() {}

    public static boolean isOn(Context context, String key) {
        return Settings.System.getInt(context.getContentResolver(), key, 1) != 0;
    }

    /** Runs {@code callback} on the main thread when either switch changes. */
    public static SafeCloseable observe(Context context, Runnable callback) {
        final ContentResolver resolver = context.getContentResolver();
        final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                callback.run();
            }
        };
        resolver.registerContentObserver(Settings.System.getUriFor(HOME), false, observer);
        resolver.registerContentObserver(Settings.System.getUriFor(RECENTS), false, observer);
        return () -> resolver.unregisterContentObserver(observer);
    }
}
