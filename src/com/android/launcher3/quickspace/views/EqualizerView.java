/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/**
 * Small bars that bounce while music plays. They step a few times a second instead of every
 * frame, so the home screen doesn't hold the display at a high refresh rate.
 */
public class EqualizerView extends View {
  private static final int BARS = 4;
  private static final long STEP_MS = 140;

  private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] mLevels = {0.45f, 0.8f, 0.6f, 0.35f};
  private final Random mRandom = new Random();
  private final float mBarWidth;
  private final float mGap;
  private boolean mPlaying;

  private final Runnable mStep = new Runnable() {
    @Override
    public void run() {
      for (int i = 0; i < BARS; i++) {
        mLevels[i] = 0.25f + 0.75f * mRandom.nextFloat();
      }
      invalidate();
      if (shouldStep()) postDelayed(this, STEP_MS);
    }
  };

  public EqualizerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    mBarWidth = 3 * density;
    mGap = 2 * density;
  }

  public void setColor(int color) {
    mPaint.setColor(color);
    invalidate();
  }

  public void setPlaying(boolean playing) {
    if (mPlaying == playing) return;
    mPlaying = playing;
    updateStepping();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = Math.round(BARS * mBarWidth + (BARS - 1) * mGap);
    setMeasuredDimension(resolveSize(width, widthMeasureSpec),
        getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float bottom = getHeight();
    float radius = mBarWidth / 2;
    for (int i = 0; i < BARS; i++) {
      float left = i * (mBarWidth + mGap);
      float top = bottom - Math.max(mBarWidth, bottom * mLevels[i]);
      canvas.drawRoundRect(left, top, left + mBarWidth, bottom, radius, radius, mPaint);
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    updateStepping();
  }

  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(mStep);
    super.onDetachedFromWindow();
  }

  @Override
  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    updateStepping();
  }

  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);
    updateStepping();
  }

  private boolean shouldStep() {
    return mPlaying && isAttachedToWindow() && isShown();
  }

  private void updateStepping() {
    removeCallbacks(mStep);
    if (shouldStep()) post(mStep);
  }
}
