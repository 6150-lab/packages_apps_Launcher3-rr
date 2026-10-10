/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.quickspace.QuickBatteryController;
import com.android.launcher3.quickspace.QuickCalendarController;
import com.android.launcher3.quickspace.QuickEventsController;
import com.android.launcher3.quickspace.QuickSpaceStyleBinder;
import com.android.launcher3.quickspace.QuickspaceController;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Base for the RR At a glance styles. Collects what every style shows, keeps the date fresh and
 * owns the shared tap actions, so a style only lays out and colors its own views.
 */
public abstract class GlanceStyleView extends FrameLayout implements QuickSpaceStyleBinder {

  /** One snapshot of the glance. Null text and a negative battery level mean hidden. */
  protected static final class Glance {
    public String day;
    public String shortDay;
    public String dayOfMonth;
    public String month;
    public String shortMonth;
    public String dateLine;
    public String greeting;
    public String temperature;
    public String condition;
    public Drawable weatherIcon;
    public String eventTitle;
    public String eventTime;
    public boolean nowPlaying;
    public String songTitle;
    public String songArtist;
    public String psa;
    public String batteryName;
    public int batteryLevel = -1;
    public boolean batteryAudio;
    public boolean charging;
  }

  protected static final int TAP_NONE = 0;
  protected static final int TAP_CALENDAR = 1;
  protected static final int TAP_WEATHER = 2;
  protected static final int TAP_CALENDAR_EVENT = 3;
  protected static final int TAP_EVENT_ACTION = 4;
  protected static final int TAP_BATTERY = 5;

  protected QuickspaceController mController;
  protected int mTextColor = Color.WHITE;
  protected boolean mBlackText;
  private QuickSpaceActionReceiver mActionReceiver;
  private final Map<View, Integer> mTaps = new HashMap<>();
  private Glance mGlance;
  private Object[] mLastValues;
  private boolean mFirstBind = true;
  private boolean mReceiverRegistered;
  private Runnable mMidnightRefresh;
  private long mNextMidnightRefresh;
  private Locale mFormatLocale;
  private String mFormatZone;
  private boolean mFormat24Hour;
  private DateFormat mDayFormat;
  private DateFormat mShortDayFormat;
  private DateFormat mDayOfMonthFormat;
  private DateFormat mMonthFormat;
  private DateFormat mShortMonthFormat;
  private DateFormat mDateLineFormat;
  private DateFormat mTimeFormat;
  private DateFormat mEventDayFormat;

  private final BroadcastReceiver mClockReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
        onMinuteTick();
        return;
      }
      if (mMidnightRefresh != null) removeCallbacks(mMidnightRefresh);
      mNextMidnightRefresh = 0;
      if (mController != null && mController.getCalendarController() != null) {
        mController.getCalendarController().refresh();
      }
      bind(false);
    }
  };

  public GlanceStyleView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setFocusable(true);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  /** Lays the snapshot out. {@code animate} is false for the first bind and size changes. */
  protected abstract void onBindGlance(Glance glance, boolean animate);

  /** Recolors the views from {@link #mTextColor}, {@link #mBlackText} and the accent. */
  protected abstract void onColorsChanged();

  /** Styles that draw the time of day get a call every minute while attached. */
  protected boolean wantsMinuteTicks() {
    return false;
  }

  protected void onMinuteTick() {}

  @Override
  public void onBind(QuickspaceController controller) {
    if (controller != null) mController = controller;
    bind(false);
  }

  @Override
  public void onDataUpdated(boolean allowAnimation) {
    Object[] values = captureValues();
    if (!mFirstBind && Arrays.equals(mLastValues, values)) return;
    bind(allowAnimation && !mFirstBind && isLaidOut(), values);
  }

  @Override
  public void applyColors(ColorStateList colors, boolean useBlackText) {
    if (colors == null) return;
    mTextColor = colors.getDefaultColor();
    mBlackText = useBlackText;
    onColorsChanged();
  }

  @Override
  public void onDestroy() {
    unregisterReceiver();
    if (mMidnightRefresh != null) removeCallbacks(mMidnightRefresh);
    mNextMidnightRefresh = 0;
    mController = null;
    mActionReceiver = null;
    mTaps.clear();
    mGlance = null;
    mLastValues = null;
    mFirstBind = true;
    mFormatLocale = null;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!mReceiverRegistered) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Intent.ACTION_DATE_CHANGED);
      filter.addAction(Intent.ACTION_TIME_CHANGED);
      filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      filter.addAction(Intent.ACTION_LOCALE_CHANGED);
      if (wantsMinuteTicks()) filter.addAction(Intent.ACTION_TIME_TICK);
      getContext().registerReceiver(mClockReceiver, filter);
      mReceiverRegistered = true;
    }
    bind(false);
  }

  @Override
  protected void onDetachedFromWindow() {
    unregisterReceiver();
    if (mMidnightRefresh != null) removeCallbacks(mMidnightRefresh);
    mNextMidnightRefresh = 0;
    super.onDetachedFromWindow();
  }

  /** The last snapshot, or null before the first bind. */
  protected Glance getGlance() {
    return mGlance;
  }

  /** Lays the last snapshot out again, for size and color changes. */
  protected void rebind() {
    if (mGlance != null) onBindGlance(mGlance, false);
  }

  private void unregisterReceiver() {
    if (!mReceiverRegistered) return;
    getContext().unregisterReceiver(mClockReceiver);
    mReceiverRegistered = false;
  }

  private void bind(boolean animate) {
    bind(animate, captureValues());
  }

  private void bind(boolean animate, Object[] values) {
    if (mController == null) return;
    mGlance = collect();
    onBindGlance(mGlance, animate);
    hideChildrenFromAccessibility(this);
    setContentDescription(describe(mGlance));
    mLastValues = values;
    mFirstBind = false;
    scheduleMidnightRefresh();
  }

  private Glance collect() {
    Context context = getContext();
    Locale locale = getResources().getConfiguration().getLocales().get(0);
    ensureFormats(locale);
    long now = System.currentTimeMillis();
    Glance glance = new Glance();
    glance.day = mDayFormat.format(now);
    glance.shortDay = mShortDayFormat.format(now).toUpperCase(locale);
    glance.dayOfMonth = mDayOfMonthFormat.format(now);
    glance.month = mMonthFormat.format(now);
    glance.shortMonth = mShortMonthFormat.format(now).toUpperCase(locale);
    glance.dateLine = mDateLineFormat.format(now);

    QuickEventsController events = mController.getEventController();
    if (events != null) {
      glance.greeting = events.getGreetings();
      glance.nowPlaying = LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(context)
          && events.isNowPlaying();
      if (glance.nowPlaying) {
        glance.songTitle = emptyToNull(events.getNowPlayingTitle());
        glance.songArtist = emptyToNull(events.getNowPlayingArtist());
        glance.nowPlaying = glance.songTitle != null || glance.songArtist != null;
      }
      glance.psa = emptyToNull(events.getPSAMessage());
    }

    if (mController.isWeatherAvailable()) {
      glance.temperature = emptyToNull(mController.getWeatherTemperature());
      if (glance.temperature != null) {
        glance.weatherIcon = mController.getWeatherIcon();
        if (LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(context)) {
          glance.condition = emptyToNull(mController.getWeatherCondition());
        }
      }
    }

    QuickCalendarController calendar = mController.getCalendarController();
    QuickCalendarController.CalendarEvent event = calendar == null ? null : calendar.getEvent();
    if (event != null && !TextUtils.isEmpty(event.getTitle())) {
      glance.eventTitle = event.getTitle();
      glance.eventTime = formatEventTime(event, locale);
    }

    QuickBatteryController battery = mController.getBatteryController();
    if (LauncherPrefs.SHOW_QUICKSPACE_BATTERY.get(context) && battery != null
        && battery.getBatteryLevel() >= 0) {
      glance.batteryName = battery.getDeviceName();
      glance.batteryLevel = battery.getBatteryLevel();
      glance.batteryAudio = battery.isAudioDevice();
      glance.charging = battery.isCharging();
    }
    return glance;
  }

  private String formatEventTime(QuickCalendarController.CalendarEvent event, Locale locale) {
    long now = System.currentTimeMillis();
    if (event.isAllDay()) {
      mEventDayFormat.setTimeZone(android.icu.util.TimeZone.getTimeZone("UTC"));
      String day = mEventDayFormat.format(event.getStartTime());
      mEventDayFormat.setTimeZone(android.icu.util.TimeZone.getDefault());
      return day.equals(mEventDayFormat.format(now)) ? null : day;
    }
    String time = mTimeFormat.format(event.getStartTime());
    String day = mEventDayFormat.format(event.getStartTime());
    return day.equals(mEventDayFormat.format(now)) ? time : day + " · " + time;
  }

  private void ensureFormats(Locale locale) {
    android.icu.util.TimeZone zone = android.icu.util.TimeZone.getDefault();
    boolean use24Hour = android.text.format.DateFormat.is24HourFormat(getContext());
    if (locale.equals(mFormatLocale) && zone.getID().equals(mFormatZone)
        && use24Hour == mFormat24Hour && mDayFormat != null) return;
    mFormatLocale = locale;
    mFormatZone = zone.getID();
    mFormat24Hour = use24Hour;
    mDayFormat = format("EEEE", locale, zone);
    mShortDayFormat = format("EEE", locale, zone);
    mDayOfMonthFormat = format("d", locale, zone);
    mMonthFormat = format("MMMM", locale, zone);
    mShortMonthFormat = format("MMM", locale, zone);
    mDateLineFormat = format("EEEEMMMd", locale, zone);
    mTimeFormat = format(use24Hour ? "Hm" : "hm", locale, zone);
    mEventDayFormat = format("MMMd", locale, zone);
  }

  private static DateFormat format(String skeleton, Locale locale,
      android.icu.util.TimeZone zone) {
    DateFormat format = DateFormat.getInstanceForSkeleton(skeleton, locale);
    format.setTimeZone(zone);
    format.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
    return format;
  }

  private Object[] captureValues() {
    Calendar today = Calendar.getInstance();
    Context context = getContext();
    QuickEventsController events = mController == null ? null : mController.getEventController();
    QuickCalendarController calendar =
        mController == null ? null : mController.getCalendarController();
    QuickCalendarController.CalendarEvent event = calendar == null ? null : calendar.getEvent();
    QuickBatteryController battery =
        mController == null ? null : mController.getBatteryController();
    boolean weather = mController != null && mController.isWeatherAvailable();
    return new Object[] {
        getResources().getConfiguration().getLocales().get(0),
        today.getTimeZone().getID(),
        today.get(Calendar.YEAR),
        today.get(Calendar.DAY_OF_YEAR),
        android.text.format.DateFormat.is24HourFormat(context),
        LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(context),
        LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(context),
        LauncherPrefs.SHOW_QUICKSPACE_BATTERY.get(context),
        events == null ? null : events.getGreetings(),
        events != null && events.isNowPlaying(),
        events == null ? null : events.getNowPlayingTitle(),
        events == null ? null : events.getNowPlayingArtist(),
        events == null ? null : events.getPSAMessage(),
        weather ? mController.getWeatherTemperature() : null,
        weather ? mController.getWeatherCondition() : null,
        weather ? Objects.hashCode(mController.getWeatherIcon()) : 0,
        event == null ? -1L : event.getId(),
        event == null ? null : event.getTitle(),
        event == null ? 0L : event.getStartTime(),
        event != null && event.isAllDay(),
        battery == null ? null : battery.getDeviceName(),
        battery == null ? -1 : battery.getBatteryLevel(),
        battery != null && battery.isCharging()
    };
  }

  private void scheduleMidnightRefresh() {
    if (mMidnightRefresh == null) {
      mMidnightRefresh = () -> {
        mNextMidnightRefresh = 0;
        bind(false);
      };
    }
    if (mNextMidnightRefresh > System.currentTimeMillis()) return;
    Calendar next = Calendar.getInstance();
    next.add(Calendar.DAY_OF_YEAR, 1);
    next.set(Calendar.HOUR_OF_DAY, 0);
    next.set(Calendar.MINUTE, 0);
    next.set(Calendar.SECOND, 1);
    next.set(Calendar.MILLISECOND, 0);
    mNextMidnightRefresh = next.getTimeInMillis();
    postDelayed(mMidnightRefresh,
        Math.max(1000, mNextMidnightRefresh - System.currentTimeMillis()));
  }

  /** Points a view's tap at one of the shared actions; TAP_NONE clears it. */
  protected void bindTap(View view, int tap) {
    if (view == null) return;
    mTaps.put(view, tap);
    view.setOnClickListener(tap == TAP_NONE ? null : this::onTap);
    view.setClickable(tap != TAP_NONE);
  }

  private void onTap(View view) {
    Integer tap = mTaps.get(view);
    if (tap == null) return;
    switch (tap) {
      case TAP_CALENDAR:
        openCalendar(view);
        break;
      case TAP_WEATHER:
        openWeather(view);
        break;
      case TAP_CALENDAR_EVENT:
        openCalendarEvent(view);
        break;
      case TAP_EVENT_ACTION:
        openEventAction(view);
        break;
      case TAP_BATTERY:
        openBattery(view);
        break;
      default:
        break;
    }
  }

  private View findTap(int tap) {
    for (Map.Entry<View, Integer> entry : mTaps.entrySet()) {
      if (entry.getValue() == tap && entry.getKey().isShown()) return entry.getKey();
    }
    return null;
  }

  private static int actionId(int tap) {
    switch (tap) {
      case TAP_CALENDAR:
        return R.id.quickspace_vertical_a11y_calendar;
      case TAP_WEATHER:
        return R.id.quickspace_vertical_a11y_weather;
      case TAP_CALENDAR_EVENT:
        return R.id.quickspace_vertical_a11y_calendar_event;
      case TAP_EVENT_ACTION:
        return R.id.quickspace_vertical_a11y_event;
      case TAP_BATTERY:
        return R.id.quickspace_vertical_a11y_battery;
      default:
        return View.NO_ID;
    }
  }

  private int actionLabel(int tap) {
    switch (tap) {
      case TAP_CALENDAR:
        return R.string.quickspace_vertical_a11y_calendar;
      case TAP_WEATHER:
        return R.string.quickspace_vertical_a11y_weather;
      case TAP_CALENDAR_EVENT:
        return R.string.quickspace_vertical_a11y_calendar_event;
      case TAP_EVENT_ACTION:
        return mGlance != null && mGlance.nowPlaying
            ? R.string.quickspace_vertical_a11y_event : R.string.quickspace_vertical_a11y_note;
      default:
        return R.string.quickspace_vertical_a11y_battery;
    }
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    for (int tap = TAP_CALENDAR; tap <= TAP_BATTERY; tap++) {
      if (findTap(tap) == null) continue;
      info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
          actionId(tap), getResources().getString(actionLabel(tap))));
    }
  }

  @Override
  public boolean performAccessibilityAction(int action, Bundle arguments) {
    for (int tap = TAP_CALENDAR; tap <= TAP_BATTERY; tap++) {
      if (actionId(tap) != action) continue;
      View view = findTap(tap);
      return view != null && view.performClick();
    }
    return super.performAccessibilityAction(action, arguments);
  }

  private static void hideChildrenFromAccessibility(ViewGroup parent) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      View child = parent.getChildAt(i);
      child.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      if (child instanceof ViewGroup) hideChildrenFromAccessibility((ViewGroup) child);
    }
  }

  private String describe(Glance glance) {
    StringBuilder text = new StringBuilder(
        getResources().getString(R.string.glance_content_description))
        .append(": ").append(glance.dateLine);
    if (glance.temperature != null) {
      text.append(", ").append(join(glance.temperature, " ", glance.condition));
    }
    if (glance.eventTitle != null) {
      text.append(", ").append(join(glance.eventTitle, " ", glance.eventTime));
    }
    if (glance.nowPlaying) {
      text.append(", ").append(join(glance.songTitle, " — ", glance.songArtist));
    }
    if (glance.batteryLevel >= 0) {
      text.append(", ").append(getResources().getString(
          R.string.quickspace_vertical_battery_value,
          glance.batteryName == null ? "" : glance.batteryName, glance.batteryLevel));
    }
    return text.toString();
  }

  private QuickSpaceActionReceiver receiver() {
    if (mActionReceiver == null) mActionReceiver = new QuickSpaceActionReceiver(getContext());
    return mActionReceiver;
  }

  protected void openCalendar(View view) {
    receiver().getCalendarAction().onClick(view);
  }

  protected void openWeather(View view) {
    receiver().getWeatherAction(hasGoogleApp()).onClick(view);
  }

  protected void openCalendarEvent(View view) {
    if (mController == null) return;
    QuickCalendarController calendar = mController.getCalendarController();
    QuickCalendarController.CalendarEvent event = calendar == null ? null : calendar.getEvent();
    if (event != null) receiver().getCalendarEventAction(event.getId()).onClick(view);
  }

  /** Opens the media app while music plays, else runs the PSA action. */
  protected void openEventAction(View view) {
    if (mController == null || mController.getEventController() == null) return;
    View.OnClickListener action = mController.getEventController().getAction();
    if (action != null) action.onClick(view);
  }

  protected void openBattery(View view) {
    if (mController != null && mController.getBatteryController() != null) {
      mController.getBatteryController().launchBatterySettings();
    }
  }

  private boolean hasGoogleApp() {
    try {
      return getContext().getPackageManager()
          .getApplicationInfo("com.google.android.googlequicksearchbox", 0).enabled;
    } catch (Exception e) {
      return false;
    }
  }

  /** Text color at an alpha fraction of {@link #mTextColor}. */
  protected int textColor(float alpha) {
    return Color.argb(Math.round(Color.alpha(mTextColor) * alpha),
        Color.red(mTextColor), Color.green(mTextColor), Color.blue(mTextColor));
  }

  /** The launcher's text shadow, or none with black text. */
  protected void applyText(TextView view, int color) {
    if (view == null) return;
    view.setTextColor(color);
    float density = getResources().getDisplayMetrics().density;
    view.setShadowLayer(mBlackText ? 0 : 2.5f * density, 0, 0.5f * density,
        mBlackText ? Color.TRANSPARENT : Color.argb(96, 0, 0, 0));
  }

  protected static void setText(TextView view, CharSequence text) {
    if (view == null) return;
    view.setText(text);
    view.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
  }

  protected static String join(String first, String separator, String second) {
    if (TextUtils.isEmpty(first)) return second;
    if (TextUtils.isEmpty(second)) return first;
    return first + separator + second;
  }

  private static String emptyToNull(String text) {
    return TextUtils.isEmpty(text) ? null : text;
  }
}
