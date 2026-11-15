/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.launcher3.R;
import java.util.Locale;

/**
 * Neon sign: the date and the day's details in a glowing tube, hanging from two wires.
 */
public class NeonGlanceView extends GlanceStyleView {
  private static final long REFLOW_MS = 200;
  private static final long FLICKER_EVERY_MS = 4000;
  private static final long FLICKER_MS = 70;

  private final Paint mWire = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mMount = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mPlate = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mTube = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF mRect = new RectF();
  private final float mDensity;
  private final float mRadius;
  private int mGlow = Color.WHITE;
  private View mSign;
  private NeonTextView mDate;
  private TextView mMonth;
  private View mInfoRow;
  private TextView mWeather;
  private View mDot;
  private TextView mEvent;
  private View mSongRow;
  private ImageView mSongIcon;
  private TextView mSong;
  private boolean mFlickering;

  private final Runnable mFlicker = new Runnable() {
    @Override
    public void run() {
      if (!shouldFlicker()) {
        mFlickering = false;
        mSongRow.setAlpha(1f);
        return;
      }
      mSongRow.setAlpha(0.55f);
      postDelayed(() -> mSongRow.setAlpha(1f), FLICKER_MS);
      postDelayed(() -> mSongRow.setAlpha(0.55f), FLICKER_MS * 4);
      postDelayed(() -> mSongRow.setAlpha(1f), FLICKER_MS * 5);
      postDelayed(this, FLICKER_EVERY_MS);
    }
  };

  public NeonGlanceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
    mRadius = 28 * mDensity;
    mWire.setStrokeWidth(2 * mDensity);
    mWire.setColor(Color.argb(128, 255, 255, 255));
    mMount.setColor(0xFFE8E2EE);
    mPlate.setColor(Color.argb(84, 12, 2, 22));
    mTube.setStyle(Paint.Style.STROKE);
    mTube.setStrokeCap(Paint.Cap.ROUND);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    mSign = findViewById(R.id.glance_neon_sign);
    mDate = findViewById(R.id.glance_neon_date);
    mMonth = findViewById(R.id.glance_neon_month);
    mInfoRow = findViewById(R.id.glance_neon_info);
    mWeather = findViewById(R.id.glance_neon_weather);
    mDot = findViewById(R.id.glance_neon_dot);
    mEvent = findViewById(R.id.glance_neon_event);
    mSongRow = findViewById(R.id.glance_neon_song_row);
    mSongIcon = findViewById(R.id.glance_neon_song_icon);
    mSong = findViewById(R.id.glance_neon_song);
    bindTap(mDate, TAP_CALENDAR);
    bindTap(mMonth, TAP_CALENDAR);
    bindTap(mSongRow, TAP_EVENT_ACTION);
  }

  @Override
  protected void onBindGlance(Glance glance, boolean animate) {
    if (animate) {
      TransitionManager.beginDelayedTransition(this, new AutoTransition().setDuration(REFLOW_MS));
    }
    Locale locale = getResources().getConfiguration().getLocales().get(0);
    mDate.setText(glance.shortDay + " " + glance.dayOfMonth);
    mMonth.setText(glance.month.toUpperCase(locale));

    String weather = upper(join(glance.temperature, " ", glance.condition), locale);
    String event = upper(join(glance.eventTitle, " ", glance.eventTime), locale);
    if (weather == null && event == null) {
      // Nothing to report: the sign just says hello.
      setText(mWeather, upper(glance.greeting, locale));
      bindTap(mWeather, TAP_NONE);
    } else {
      setText(mWeather, weather);
      bindTap(mWeather, TAP_WEATHER);
    }
    setText(mEvent, event);
    bindTap(mEvent, event != null ? TAP_CALENDAR_EVENT : TAP_NONE);
    mDot.setVisibility(weather != null && event != null ? VISIBLE : GONE);
    mInfoRow.setVisibility(mWeather.getVisibility() == VISIBLE || event != null ? VISIBLE : GONE);

    // The song, or else a followed team's game, glows under the sign.
    boolean score = !glance.nowPlaying && glance.score != null;
    mSongRow.setVisibility(glance.nowPlaying || score ? VISIBLE : GONE);
    if (glance.nowPlaying) {
      mSongIcon.setImageResource(R.drawable.ic_music_note_24dp);
      mSong.setText(upper(join(glance.songTitle, " · ", glance.songArtist), locale));
      bindTap(mSongRow, TAP_EVENT_ACTION);
    } else if (score) {
      mSongIcon.setImageResource(R.drawable.ic_quickspace_sports);
      mSong.setText(scoreText(glance, true));
      bindTap(mSongRow, TAP_SCORE);
    }
    updateFlicker();
  }

  @Override
  protected void onColorsChanged() {
    Context context = getContext();
    mGlow = context.getColor(android.R.color.system_accent1_300);
    int glowText = context.getColor(android.R.color.system_accent1_100);
    mDate.setGlowColor(mGlow);
    glow(mMonth, glowText, mGlow, 8);
    glow(mWeather, Color.WHITE, Color.argb(150, 255, 255, 255), 6);
    glow(mEvent, Color.WHITE, Color.argb(150, 255, 255, 255), 6);
    glow(mSong, glowText, mGlow, 8);
    mSongIcon.setImageTintList(ColorStateList.valueOf(glowText));
    GradientDrawable dot = new GradientDrawable();
    dot.setShape(GradientDrawable.OVAL);
    dot.setColor(mGlow);
    mDot.setBackground(dot);
    invalidate();
  }

  private void glow(TextView view, int color, int glow, float radiusDp) {
    view.setTextColor(color);
    view.setShadowLayer(radiusDp * mDensity, 0, 0, glow);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (mSign != null && mSign.getVisibility() == VISIBLE) {
      float left = mSign.getLeft();
      float top = mSign.getTop();
      float right = mSign.getRight();
      float bottom = mSign.getBottom();
      float inset = (right - left) * 0.2f;
      float mountRadius = 5 * mDensity;
      for (float x : new float[] {left + inset, right - inset}) {
        canvas.drawLine(x, getPaddingTop(), x, top, mWire);
        canvas.drawCircle(x, top, mountRadius, mMount);
      }

      mRect.set(left, top, right, bottom);
      canvas.drawRoundRect(mRect, mRadius, mRadius, mPlate);
      tube(canvas, 0, 22, 0.10f);
      tube(canvas, 0, 13, 0.20f);
      tube(canvas, 0, 7, 0.40f);
      tube(canvas, 5, 8, 0.16f);
      mTube.setColor(Color.WHITE);
      mTube.setStrokeWidth(2.5f * mDensity);
      canvas.drawRoundRect(mRect, mRadius, mRadius, mTube);
    }
    super.dispatchDraw(canvas);
  }

  /** One glow pass: a stroke of the accent, inset into the sign by insetDp. */
  private void tube(Canvas canvas, float insetDp, float widthDp, float alpha) {
    float inset = insetDp * mDensity;
    mTube.setColor(Color.argb(Math.round(255 * alpha),
        Color.red(mGlow), Color.green(mGlow), Color.blue(mGlow)));
    mTube.setStrokeWidth(widthDp * mDensity);
    canvas.drawRoundRect(mRect.left + inset, mRect.top + inset, mRect.right - inset,
        mRect.bottom - inset, mRadius - inset, mRadius - inset, mTube);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    updateFlicker();
  }

  @Override
  protected void onDetachedFromWindow() {
    stopFlicker();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (mSongRow != null) updateFlicker();
  }

  @Override
  public void onDestroy() {
    stopFlicker();
    super.onDestroy();
  }

  private boolean shouldFlicker() {
    return mSongRow != null && mSongRow.getVisibility() == VISIBLE && isAttachedToWindow()
        && isShown();
  }

  private void updateFlicker() {
    if (shouldFlicker()) {
      if (!mFlickering) {
        mFlickering = true;
        postDelayed(mFlicker, FLICKER_EVERY_MS);
      }
    } else {
      stopFlicker();
    }
  }

  private void stopFlicker() {
    mFlickering = false;
    removeCallbacks(mFlicker);
    if (mSongRow != null) mSongRow.setAlpha(1f);
  }

  private static String upper(String text, Locale locale) {
    return TextUtils.isEmpty(text) ? null : text.toUpperCase(locale);
  }
}
