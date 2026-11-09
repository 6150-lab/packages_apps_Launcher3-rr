/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The Flatline Vitals readout: the phone's battery, a bolt while charging, and the next alarm. */
public class FlatlineVitalsView extends TextView {
  private Intent mBattery;
  private boolean mRegistered;

  private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) mBattery = intent;
      update();
    }
  };

  public FlatlineVitalsView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!mRegistered) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Intent.ACTION_BATTERY_CHANGED);
      filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
      // The alarm's time follows the 24-hour switch, the zone and the language.
      filter.addAction(Intent.ACTION_TIME_CHANGED);
      filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      filter.addAction(Intent.ACTION_LOCALE_CHANGED);
      mBattery = getContext().registerReceiver(mReceiver, filter,
          Context.RECEIVER_NOT_EXPORTED);
      mRegistered = true;
    }
    update();
  }

  @Override
  protected void onDetachedFromWindow() {
    if (mRegistered) {
      getContext().unregisterReceiver(mReceiver);
      mRegistered = false;
    }
    super.onDetachedFromWindow();
  }

  private void update() {
    Context context = getContext();
    List<String> parts = new ArrayList<>(2);
    if (mBattery != null) {
      int level = mBattery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100
          / Math.max(1, mBattery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
      boolean plugged = mBattery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
      if (level >= 0) parts.add(level + (plugged ? "%↯" : "%"));
    }
    AlarmManager alarms = context.getSystemService(AlarmManager.class);
    AlarmManager.AlarmClockInfo alarm = alarms == null ? null : alarms.getNextAlarmClock();
    if (alarm != null) {
      parts.add(context.getString(R.string.glance_flatline_alarm,
          DateFormat.getTimeFormat(context).format(new Date(alarm.getTriggerTime()))));
    }
    Locale locale = getResources().getConfiguration().getLocales().get(0);
    String text = TextUtils.join(" · ", parts).toUpperCase(locale);
    if (!TextUtils.equals(getText(), text)) setText(text);
  }
}
