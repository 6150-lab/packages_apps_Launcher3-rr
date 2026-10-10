/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The Orbit dial: a ring that fills as the day goes by, with a glowing head where it stops.
 */
public class OrbitRingView extends View {
  private final Paint mDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mHeadRing = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mHead = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF mOval = new RectF();
  private final float mMinStroke;
  private float mStroke;
  private float mHeadRadius;
  private float mGlowRadius;
  private float mProgress;
  private int mAccent = Color.WHITE;

  public OrbitRingView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mMinStroke = 3 * getResources().getDisplayMetrics().density;
    mTrack.setStyle(Paint.Style.STROKE);
    mArc.setStyle(Paint.Style.STROKE);
    mArc.setStrokeCap(Paint.Cap.ROUND);
  }

  /** How much of the day has passed, 0 to 1. */
  public void setProgress(float progress) {
    progress = Math.max(0f, Math.min(1f, progress));
    if (mProgress == progress) return;
    mProgress = progress;
    invalidate();
  }

  public void setColors(int disc, int track, int accent, int head) {
    mDisc.setColor(disc);
    mTrack.setColor(track);
    mArc.setColor(accent);
    mHeadRing.setColor(accent);
    mHead.setColor(head);
    mAccent = accent;
    updateGlow();
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    float size = Math.min(w, h);
    mStroke = Math.max(mMinStroke, size * 0.052f);
    mHeadRadius = mStroke * 1.15f;
    mGlowRadius = mHeadRadius * 2.6f;
    mTrack.setStrokeWidth(mStroke);
    mArc.setStrokeWidth(mStroke);
    updateGlow();
  }

  private void updateGlow() {
    if (mGlowRadius <= 0) return;
    mGlow.setShader(new RadialGradient(0, 0, mGlowRadius,
        new int[] {withAlpha(mAccent, 0.75f), withAlpha(mAccent, 0f)}, null,
        Shader.TileMode.CLAMP));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    float radius = Math.min(getWidth(), getHeight()) / 2f - mGlowRadius;
    if (radius <= 0) return;

    canvas.drawCircle(cx, cy, radius, mDisc);
    canvas.drawCircle(cx, cy, radius, mTrack);
    mOval.set(cx - radius, cy - radius, cx + radius, cy + radius);
    float sweep = 360f * mProgress;
    if (sweep > 0.5f) canvas.drawArc(mOval, -90f, sweep, false, mArc);

    double angle = Math.toRadians(sweep - 90f);
    canvas.save();
    canvas.translate(cx + (float) (radius * Math.cos(angle)),
        cy + (float) (radius * Math.sin(angle)));
    canvas.drawCircle(0, 0, mGlowRadius, mGlow);
    canvas.drawCircle(0, 0, mHeadRadius + mStroke * 0.5f, mHeadRing);
    canvas.drawCircle(0, 0, mHeadRadius, mHead);
    canvas.restore();
  }

  private static int withAlpha(int color, float alpha) {
    return Color.argb(Math.round(Color.alpha(color) * alpha),
        Color.red(color), Color.green(color), Color.blue(color));
  }
}
