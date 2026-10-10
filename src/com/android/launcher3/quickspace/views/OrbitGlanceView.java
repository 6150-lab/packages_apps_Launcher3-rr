/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.launcher3.R;
import java.util.Calendar;

/**
 * Orbit: the date inside a ring that fills as the day goes by, with weather, the next event and
 * one more line (music, battery or a message) beside it.
 */
public class OrbitGlanceView extends GlanceStyleView {
  private static final long REFLOW_MS = 200;

  private final int mDialMax;
  private final int mDialMin;
  private View mDial;
  private OrbitRingView mRing;
  private TextView mDayNumber;
  private TextView mShortDay;
  private View mWeatherRow;
  private ImageView mWeatherIcon;
  private TextView mTemperature;
  private TextView mCondition;
  private View mEventRow;
  private ImageView mEventIcon;
  private TextView mEventText;
  private View mLineRow;
  private EqualizerView mEqualizer;
  private ImageView mLineIcon;
  private TextView mLineText;
  private int mArtistColor = Color.WHITE;

  public OrbitGlanceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    mDialMax = Math.round(116 * density);
    mDialMin = Math.round(64 * density);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    mDial = findViewById(R.id.glance_orbit_dial);
    mRing = findViewById(R.id.glance_orbit_ring);
    mDayNumber = findViewById(R.id.glance_orbit_day_number);
    mShortDay = findViewById(R.id.glance_orbit_short_day);
    mWeatherRow = findViewById(R.id.glance_orbit_weather);
    mWeatherIcon = findViewById(R.id.glance_orbit_weather_icon);
    mTemperature = findViewById(R.id.glance_orbit_temperature);
    mCondition = findViewById(R.id.glance_orbit_condition);
    mEventRow = findViewById(R.id.glance_orbit_event);
    mEventIcon = findViewById(R.id.glance_orbit_event_icon);
    mEventText = findViewById(R.id.glance_orbit_event_text);
    mLineRow = findViewById(R.id.glance_orbit_line);
    mEqualizer = findViewById(R.id.glance_orbit_equalizer);
    mLineIcon = findViewById(R.id.glance_orbit_line_icon);
    mLineText = findViewById(R.id.glance_orbit_line_text);
    bindTap(mDial, TAP_CALENDAR);
    bindTap(mWeatherRow, TAP_WEATHER);
    bindTap(mEventRow, TAP_CALENDAR_EVENT);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
        ? mDialMax
        : MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
    int size = Math.max(mDialMin, Math.min(mDialMax, available));
    ViewGroup.LayoutParams lp = mDial.getLayoutParams();
    if (lp.width != size || lp.height != size) {
      lp.width = size;
      lp.height = size;
      mDayNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.36f);
      mShortDay.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.105f);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected boolean wantsMinuteTicks() {
    return true;
  }

  @Override
  protected void onMinuteTick() {
    mRing.setProgress(dayProgress());
  }

  @Override
  protected void onBindGlance(Glance glance, boolean animate) {
    if (animate) {
      TransitionManager.beginDelayedTransition(this, new AutoTransition().setDuration(REFLOW_MS));
    }
    mDayNumber.setText(glance.dayOfMonth);
    mShortDay.setText(glance.shortDay);
    mRing.setProgress(dayProgress());

    boolean weather = glance.temperature != null;
    mWeatherRow.setVisibility(weather ? VISIBLE : GONE);
    mWeatherIcon.setImageDrawable(glance.weatherIcon);
    mWeatherIcon.setVisibility(glance.weatherIcon != null ? VISIBLE : GONE);
    mTemperature.setText(glance.temperature);
    setText(mCondition, glance.condition);

    boolean event = glance.eventTitle != null;
    mEventRow.setVisibility(event ? VISIBLE : GONE);
    mEventText.setText(join(glance.eventTitle, " · ", glance.eventTime));

    mEqualizer.setPlaying(glance.nowPlaying);
    mEqualizer.setVisibility(glance.nowPlaying ? VISIBLE : GONE);
    mLineIcon.setVisibility(glance.nowPlaying ? GONE : VISIBLE);
    mLineText.setMaxLines(1);
    if (glance.nowPlaying) {
      mLineText.setText(songText(glance));
      bindTap(mLineRow, TAP_EVENT_ACTION);
    } else if (glance.batteryLevel >= 0) {
      mLineIcon.setImageResource(glance.batteryAudio
          ? R.drawable.ic_quickspace_minimal_headphones : R.drawable.ic_quickspace_minimal_battery);
      mLineText.setText(getResources().getString(R.string.quickspace_vertical_battery_value,
          glance.batteryName == null ? "" : glance.batteryName, glance.batteryLevel));
      bindTap(mLineRow, TAP_BATTERY);
    } else if (glance.psa != null) {
      mLineIcon.setImageResource(R.drawable.ic_quickspace_minimal_psa);
      mLineText.setText(glance.psa);
      mLineText.setMaxLines(event ? 1 : 2);
      bindTap(mLineRow, TAP_EVENT_ACTION);
    } else {
      mLineText.setText(null);
      bindTap(mLineRow, TAP_NONE);
    }
    mLineRow.setVisibility(mLineText.length() > 0 ? VISIBLE : GONE);
  }

  private CharSequence songText(Glance glance) {
    SpannableStringBuilder text = new SpannableStringBuilder();
    if (glance.songTitle != null) {
      text.append(glance.songTitle, new StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (glance.songArtist != null) {
      if (text.length() > 0) text.append(" · ");
      text.append(glance.songArtist, new ForegroundColorSpan(mArtistColor),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return text;
  }

  @Override
  protected void onColorsChanged() {
    Context context = getContext();
    int accent = context.getColor(mBlackText
        ? android.R.color.system_accent1_600 : android.R.color.system_accent1_300);
    int accentText = context.getColor(mBlackText
        ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
    int disc = mBlackText ? Color.argb(80, 255, 255, 255) : Color.argb(46, 10, 4, 16);
    mRing.setColors(disc, textColor(0.22f), accent, Color.WHITE);
    applyText(mDayNumber, mTextColor);
    applyText(mShortDay, accentText);
    applyText(mTemperature, mTextColor);
    applyText(mCondition, textColor(0.85f));
    applyText(mEventText, mTextColor);
    applyText(mLineText, mTextColor);
    mArtistColor = textColor(0.8f);
    ColorStateList iconTint = ColorStateList.valueOf(accentText);
    mEventIcon.setImageTintList(iconTint);
    mLineIcon.setImageTintList(iconTint);
    mEqualizer.setColor(accent);
    rebind();
  }

  private static float dayProgress() {
    Calendar now = Calendar.getInstance();
    return (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 1440f;
  }
}
