/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.launcher3.R;
import java.util.Locale;

/**
 * The lock screen's Flatline faces as At a glance styles: Flatline, the time over the date and a
 * line with a readout; Flatline Vitals, the same in a card with battery and the next alarm; and
 * Flatline Signature, the line running on from the time. One class binds all three layouts and
 * skips the views a layout lacks.
 */
public class FlatlineGlanceView extends GlanceStyleView {
  private static final long REFLOW_MS = 200;

  private final float mDensity;
  private TextView mTime;
  private TextView mDate;
  private View mSeparator;
  private View mWeather;
  private ImageView mWeatherIcon;
  private TextView mTemperature;
  private TextView mPlace;
  private View mCard;
  private TextView mVitalsLabel;
  private TextView mVitals;
  private FlatlineTraceView mTrace;

  public FlatlineGlanceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    mTime = findViewById(R.id.glance_flatline_time);
    mDate = findViewById(R.id.glance_flatline_date);
    mSeparator = findViewById(R.id.glance_flatline_separator);
    mWeather = findViewById(R.id.glance_flatline_weather);
    mWeatherIcon = findViewById(R.id.glance_flatline_weather_icon);
    mTemperature = findViewById(R.id.glance_flatline_temperature);
    mPlace = findViewById(R.id.glance_flatline_place);
    mCard = findViewById(R.id.glance_flatline_card);
    mVitalsLabel = findViewById(R.id.glance_flatline_vitals_label);
    mVitals = findViewById(R.id.glance_flatline_vitals);
    mTrace = findViewById(R.id.glance_flatline_trace);
    bindTap(mDate, TAP_CALENDAR);
    bindTap(mWeather, TAP_WEATHER);
    bindTap(mVitals, TAP_BATTERY);
  }

  @Override
  protected void onBindGlance(Glance glance, boolean animate) {
    if (animate) {
      TransitionManager.beginDelayedTransition(this, new AutoTransition().setDuration(REFLOW_MS));
    }
    Locale locale = getResources().getConfiguration().getLocales().get(0);
    // The card is narrower, so Vitals shortens the date.
    mDate.setText(mCard != null ? glance.shortDateLine : glance.dateLine);

    boolean weather = glance.temperature != null;
    mWeather.setVisibility(weather ? VISIBLE : GONE);
    if (mSeparator != null) mSeparator.setVisibility(weather ? VISIBLE : GONE);
    mWeatherIcon.setImageDrawable(glance.weatherIcon);
    mWeatherIcon.setVisibility(glance.weatherIcon != null ? VISIBLE : GONE);
    mTemperature.setText(glance.temperature);
    String place = join(glance.city, " · ", glance.condition);
    if (mPlace != null) {
      setText(mPlace, place == null ? null : place.toUpperCase(locale));
      bindTap(mPlace, weather ? TAP_WEATHER : TAP_NONE);
    }

    mTrace.setPlaying(glance.nowPlaying);
    String right = glance.nowPlaying ? join(glance.songTitle, " · ", glance.songArtist) : place;
    mTrace.setReadout(getResources().getString(glance.nowPlaying
            ? R.string.quickspace_vertical_now_playing : R.string.glance_flatline_idle)
            .toUpperCase(locale),
        right == null ? null : right.toUpperCase(locale));
    bindTap(mTrace, glance.nowPlaying ? TAP_EVENT_ACTION : TAP_NONE);
  }

  /** Vitals shows the phone's own battery, so a tap opens its battery usage. */
  @Override
  protected void openBattery(View view) {
    try {
      getContext().startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (ActivityNotFoundException e) {
      super.openBattery(view);
    }
  }

  @Override
  protected void onColorsChanged() {
    mTrace.setColors(mTextColor, mBlackText);
    applyText(mTime, mTextColor);
    applyText(mDate, textColor(0.85f));
    if (mSeparator instanceof TextView) applyText((TextView) mSeparator, textColor(0.85f));
    // Vitals sets the temperature as large as the time; the others keep it in the date line.
    applyText(mTemperature, mCard != null ? mTextColor : textColor(0.85f));
    applyText(mPlace, textColor(0.75f));
    applyText(mVitalsLabel, textColor(0.75f));
    applyText(mVitals, textColor(0.75f));
    if (mCard != null) {
      GradientDrawable card = new GradientDrawable();
      card.setColor(textColor(0x14 / 255f));
      card.setStroke(Math.max(1, Math.round(mDensity)), textColor(0x2A / 255f));
      card.setCornerRadius(22 * mDensity);
      mCard.setBackground(card);
    }
    rebind();
  }
}
