/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util;

/** Setting keys the RR framework doesn't declare. */
public final class RrSettings {
    // Settings.System
    public static final String GESTURE_NAVBAR_LENGTH_MODE = "gesture_navbar_length_mode";
    public static final String GESTURE_NAVBAR_HEIGHT_MODE = "gesture_navbar_height_mode";
    public static final String GESTURE_NAVBAR_AUTO_HIDE = "gesture_navbar_auto_hide";
    public static final String RECENTS_LOCKED_TASKS = "recents_locked_tasks";

    // Settings.Secure
    public static final String NAVBAR_LAYOUT_MODE = "navbar_layout_mode";

    private RrSettings() {}
}
