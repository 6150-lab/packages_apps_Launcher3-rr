/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The lock screen's Flatline on the home screen: a steady heartbeat while a song plays and one
 * bright spike from left to right when a notification lands. While nothing plays the line rests
 * flat and a scan head drifts across now and then, as on the lock screen; Home settings can have
 * the head carry a small beat across, or keep a slow heartbeat scrolling instead.
 */
public class FlatlineTraceView extends View {
  private static final long FRAME_MS = 40;
  private static final long SPIKE_MS = 1100;
  private static final long SCAN_MS = 1400;
  private static final long SCAN_EVERY = 5000;
  /** The values of the Flatline at rest setting, and how tall its two beats are. */
  private static final int REST_DOT = 0;
  private static final int REST_PULSE = 1;
  private static final int REST_BEAT = 2;
  private static final float REST_PULSE_AMP = 0.45f;
  private static final float REST_BEAT_AMP = 0.4f;
  /** What the trace draws: nothing, the beat scrolling by, or one beat travelling with a head. */
  private static final int SHAPE_FLAT = 0;
  private static final int SHAPE_BEAT = 1;
  private static final int SHAPE_TRAVEL = 2;
  private static final float[] GLOW_WIDTHS = {9f, 4f, 1.8f};
  private static final int[] GLOW_ALPHAS = {0x1E, 0x50, 0xFF};
  private static final float[] GRID_ROWS = {0.25f, 0.5f, 0.75f};
  private static final Set<FlatlineTraceView> sAttached =
      Collections.newSetFromMap(new WeakHashMap<>());

  private final float mDensity;
  private final boolean mGrid;
  private final boolean mShowReadout;
  private final Paint mTrace = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Path mPath = new Path();
  private int mColor = Color.WHITE;
  private boolean mPlaying;
  private String mReadoutLeft;
  private String mReadoutRight;
  /** Uptime when the last notification spike began, 0 for none. */
  private long mSpikeAt;
  private long mNextScan;
  private int mRest = REST_DOT;
  private final LauncherPrefChangeListener mRestListener = key -> {
    if (LauncherPrefs.QUICKSPACE_FLATLINE_REST.getSharedPrefKey().equals(key)) readRest();
  };
  /** One frame pending at most, however often the view is invalidated in between. */
  private final Runnable mNextFrame = this::invalidate;

  public FlatlineTraceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlatlineTraceView);
    mGrid = a.getBoolean(R.styleable.FlatlineTraceView_flatlineGrid, false);
    mShowReadout = a.getBoolean(R.styleable.FlatlineTraceView_flatlineReadout, true);
    a.recycle();
    mTrace.setStyle(Paint.Style.STROKE);
    mTrace.setStrokeCap(Paint.Cap.ROUND);
    mTrace.setStrokeJoin(Paint.Join.ROUND);
    mText.setTypeface(Typeface.MONOSPACE);
    mText.setTextSize(10f * mDensity);
    mText.setLetterSpacing(0.12f);
  }

  /** A notification just arrived: every Flatline on screen sends a spike across. */
  public static void pulse() {
    long now = SystemClock.uptimeMillis();
    for (FlatlineTraceView view : sAttached) {
      view.mSpikeAt = now;
      view.invalidate();
    }
  }

  /** The line in the launcher's text color; the readout keeps its shadow off black text. */
  public void setColors(int color, boolean blackText) {
    mColor = color;
    float density = mDensity;
    mText.setShadowLayer(blackText ? 0 : 2.5f * density, 0, 0.5f * density,
        blackText ? Color.TRANSPARENT : Color.argb(96, 0, 0, 0));
    invalidate();
  }

  public void setPlaying(boolean playing) {
    if (mPlaying == playing) return;
    mPlaying = playing;
    invalidate();
  }

  /** What the line is doing on the left, the song or the weather on the right. */
  public void setReadout(String left, String right) {
    if (TextUtils.equals(mReadoutLeft, left) && TextUtils.equals(mReadoutRight, right)) return;
    mReadoutLeft = left;
    mReadoutRight = right;
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Its min height, or less when the cell is short.
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
        resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec));
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    sAttached.add(this);
    LauncherPrefs.get(getContext())
        .addListener(mRestListener, LauncherPrefs.QUICKSPACE_FLATLINE_REST);
    readRest();
    mNextScan = SystemClock.uptimeMillis() + SCAN_EVERY;
    invalidate();
  }

  @Override
  protected void onDetachedFromWindow() {
    sAttached.remove(this);
    LauncherPrefs.get(getContext())
        .removeListener(mRestListener, LauncherPrefs.QUICKSPACE_FLATLINE_REST);
    removeCallbacks(mNextFrame);
    super.onDetachedFromWindow();
  }

  /** The Flatline at rest setting, read when the view appears and whenever it changes. */
  private void readRest() {
    int rest;
    try {
      rest = Integer.parseInt(LauncherPrefs.QUICKSPACE_FLATLINE_REST.get(getContext()));
    } catch (NumberFormatException e) {
      rest = REST_DOT;
    }
    if (rest == mRest) return;
    mRest = rest;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float w = getWidth();
    float h = getHeight();
    if (w <= 0 || h <= 0) return;
    float base = h * 0.52f;
    long now = SystemClock.uptimeMillis();
    boolean spike = mSpikeAt != 0 && now - mSpikeAt < SPIKE_MS;
    boolean restBeat = mRest == REST_BEAT && !mPlaying && !spike;
    boolean scanning = !mPlaying && !spike && !restBeat && now >= mNextScan
        && now < mNextScan + SCAN_MS;

    if (mGrid) {
      mTrace.setStrokeWidth(1f);
      mTrace.setColor(alpha(mColor, 0x16));
      float step = 24f * mDensity;
      for (float gx = step; gx < w; gx += step) canvas.drawLine(gx, 0, gx, h, mTrace);
      for (float f : GRID_ROWS) canvas.drawLine(0, h * f, w, h * f, mTrace);
    }

    // The resting line.
    mTrace.setStrokeWidth(1.2f * mDensity);
    mTrace.setColor(alpha(mColor, 0x3A));
    canvas.drawLine(0, base, w, base, mTrace);

    float amp = h * 0.42f;
    float period = (mPlaying ? 180f : 260f) * mDensity;
    // The beat scrolls left: a little under one period a second with a song, a third of one at
    // rest. Either is a whole number of periods in the 100 s the clock wraps at.
    float shift = (now % 100_000L) / 1000f * period * (mPlaying ? 0.9f : 0.32f);
    float spikeHead = w * (now - mSpikeAt) / SPIKE_MS;
    float scanHead = w * (now - mNextScan) / SCAN_MS;
    int shape = SHAPE_FLAT;
    float head = 0;
    if (mPlaying) {
      shape = SHAPE_BEAT;
    } else if (spike) {
      shape = SHAPE_TRAVEL;
      head = spikeHead;
    } else if (restBeat) {
      shape = SHAPE_BEAT;
      amp *= REST_BEAT_AMP;
    } else if (scanning && mRest == REST_PULSE) {
      shape = SHAPE_TRAVEL;
      head = scanHead;
      amp *= REST_PULSE_AMP;
    }
    mPath.rewind();
    float step = 2f * mDensity;
    for (float x = 0; x <= w; x += step) {
      float y = yAt(x, base, amp, period, shift, shape, head);
      if (x == 0) mPath.moveTo(x, y);
      else mPath.lineTo(x, y);
    }

    // Glow from three strokes, wide and faint to thin and bright.
    for (int i = 0; i < GLOW_WIDTHS.length; i++) {
      mTrace.setStrokeWidth(GLOW_WIDTHS[i] * mDensity);
      mTrace.setColor(alpha(mColor, GLOW_ALPHAS[i]));
      canvas.drawPath(mPath, mTrace);
    }

    // The scan head: a bright dot riding the trace.
    float headX = -1;
    if (spike) headX = spikeHead;
    else if (scanning) headX = scanHead;
    else if (mPlaying || restBeat) headX = w * 0.7f;
    if (headX >= 0) {
      float headY = yAt(headX, base, amp, period, shift, shape, head);
      mFill.setColor(alpha(mColor, 0x55));
      canvas.drawCircle(headX, headY, 7f * mDensity, mFill);
      mFill.setColor(mColor);
      canvas.drawCircle(headX, headY, 2.4f * mDensity, mFill);
    }

    if (mShowReadout) drawReadout(canvas, w, h);

    // Next frame: smooth while something moves, else sleep until the next scan.
    removeCallbacks(mNextFrame);
    if (mPlaying || spike || scanning || restBeat) {
      postDelayed(mNextFrame, FRAME_MS);
    } else {
      if (now >= mNextScan + SCAN_MS) mNextScan = now + SCAN_EVERY;
      postDelayed(mNextFrame, Math.max(16, mNextScan - now));
    }
  }

  /** The trace's height at x: the beat scrolling by, one beat around its head, or flat. */
  private float yAt(float x, float base, float amp, float period, float shift, int shape,
      float head) {
    if (shape == SHAPE_BEAT) return base - amp * beat(((x + shift) % period) / period);
    if (shape == SHAPE_TRAVEL) {
      float d = (x - head) / (46f * mDensity);
      if (d > 0.3f || d < -1.6f) return base;
      return base - amp * beat(0.56f + d * 0.22f);
    }
    return base;
  }

  private void drawReadout(Canvas canvas, float w, float h) {
    mText.setColor(alpha(mColor, 0x99));
    float ty = h - 3f * mDensity;
    if (!TextUtils.isEmpty(mReadoutLeft)) {
      mText.setTextAlign(Paint.Align.LEFT);
      canvas.drawText(mReadoutLeft, 2f * mDensity, ty, mText);
    }
    if (!TextUtils.isEmpty(mReadoutRight)) {
      mText.setTextAlign(Paint.Align.RIGHT);
      CharSequence fitted = TextUtils.ellipsize(mReadoutRight, mText, w * 0.55f,
          TextUtils.TruncateAt.END);
      canvas.drawText(fitted, 0, fitted.length(), w - 2f * mDensity, ty, mText);
    }
  }

  /**
   * One heartbeat over t in 0..1: a small P bump, the sharp QRS spike with its dip, and a rounder
   * T bump, flat the rest of the way. Same shape as the lock screen's Flatline.
   */
  private static float beat(float t) {
    float p = bump(t, 0.32f, 0.05f) * 0.12f;
    float q = -bump(t, 0.47f, 0.015f) * 0.14f;
    float r = bump(t, 0.52f, 0.022f) * 0.85f;
    float s = -bump(t, 0.575f, 0.018f) * 0.32f;
    float tw = bump(t, 0.70f, 0.045f) * 0.2f;
    return p + q + r + s + tw;
  }

  private static float bump(float t, float at, float width) {
    float d = (t - at) / width;
    if (d < -1f || d > 1f) return 0f;
    float v = 0.5f + 0.5f * (float) Math.sin(d * Math.PI / 2 + Math.PI / 2);
    return v * v;
  }

  private static int alpha(int color, int alpha) {
    return (color & 0x00FFFFFF) | (Math.round(Color.alpha(color) * alpha / 255f) << 24);
  }
}
