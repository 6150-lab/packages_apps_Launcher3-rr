/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Glowing text with a cyan and magenta split behind it, like the RR "Remixed" logo.
 */
public class NeonTextView extends TextView {
  private static final int CYAN = 0xB33DF0FF;
  private static final int MAGENTA = 0xB3FF2E9A;

  private final float mSplit;
  private final float mWideGlow;
  private final float mTightGlow;
  private final float mCoreGlow;
  private int mGlow = Color.WHITE;

  public NeonTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    mSplit = 2 * density;
    mWideGlow = 22 * density;
    mTightGlow = 8 * density;
    mCoreGlow = 2 * density;
  }

  public void setGlowColor(int color) {
    mGlow = color;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    Layout layout = getLayout();
    if (layout == null) {
      super.onDraw(canvas);
      return;
    }
    TextPaint paint = getPaint();
    canvas.save();
    canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());

    paint.clearShadowLayer();
    paint.setColor(CYAN);
    canvas.translate(-mSplit, 0);
    layout.draw(canvas);
    paint.setColor(MAGENTA);
    canvas.translate(2 * mSplit, mSplit / 2);
    layout.draw(canvas);
    canvas.translate(-mSplit, -mSplit / 2);

    paint.setColor(Color.WHITE);
    paint.setShadowLayer(mWideGlow, 0, 0, mGlow);
    layout.draw(canvas);
    paint.setShadowLayer(mTightGlow, 0, 0, mGlow);
    layout.draw(canvas);
    paint.setShadowLayer(mCoreGlow, 0, 0, Color.WHITE);
    layout.draw(canvas);
    paint.clearShadowLayer();
    canvas.restore();
  }
}
