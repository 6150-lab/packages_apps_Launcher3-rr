/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Glass: a frosted card with the greeting, weather and date over two tiles for the next
 * event, what's playing, battery or a message.
 */
public class GlassGlanceView extends GlanceStyleView {
  private static final long REFLOW_MS = 200;
  private static final int TILE_EVENT = 0;
  private static final int TILE_SONG = 1;
  private static final int TILE_BATTERY = 2;
  private static final int TILE_PSA = 3;

  private final float mDensity;
  private View mCard;
  private TextView mGreeting;
  private View mWeatherChip;
  private ImageView mWeatherIcon;
  private TextView mTemperature;
  private TextView mDate;
  private View mTiles;
  private Tile mTileA;
  private Tile mTileB;
  private int mIconColor = Color.WHITE;
  private int mFillColor = Color.WHITE;

  /** One of the two tiles under the date. */
  private final class Tile {
    final View root;
    final ImageView icon;
    final EqualizerView equalizer;
    final TextView title;
    final TextView subtitle;
    final View levelRow;
    final View level;
    final TextView levelText;

    Tile(int rootId, int iconId, int equalizerId, int titleId, int subtitleId, int levelRowId,
        int levelId, int levelTextId) {
      root = findViewById(rootId);
      icon = findViewById(iconId);
      equalizer = findViewById(equalizerId);
      title = findViewById(titleId);
      subtitle = findViewById(subtitleId);
      levelRow = findViewById(levelRowId);
      level = findViewById(levelId);
      levelText = findViewById(levelTextId);
    }

    void bind(int type, Glance glance) {
      boolean song = type == TILE_SONG;
      equalizer.setPlaying(song);
      equalizer.setVisibility(song ? VISIBLE : GONE);
      icon.setVisibility(song ? GONE : VISIBLE);
      levelRow.setVisibility(type == TILE_BATTERY ? VISIBLE : GONE);
      title.setMaxLines(type == TILE_PSA ? 3 : 1);
      switch (type) {
        case TILE_EVENT:
          icon.setImageResource(R.drawable.ic_quickspace_calendar);
          setText(title, glance.eventTitle);
          setText(subtitle, glance.eventTime);
          bindTap(root, TAP_CALENDAR_EVENT);
          break;
        case TILE_SONG:
          setText(title, glance.songTitle != null ? glance.songTitle : glance.songArtist);
          setText(subtitle, glance.songTitle != null ? glance.songArtist : null);
          bindTap(root, TAP_EVENT_ACTION);
          break;
        case TILE_BATTERY:
          icon.setImageResource(glance.batteryAudio
              ? R.drawable.ic_quickspace_minimal_headphones
              : R.drawable.ic_quickspace_minimal_battery);
          setText(title, glance.batteryName);
          setText(subtitle, null);
          levelText.setText(getResources().getString(R.string.glance_battery_level,
              glance.batteryLevel));
          Drawable bar = level.getBackground();
          if (bar != null) bar.setLevel(Math.max(0, Math.min(100, glance.batteryLevel)) * 100);
          bindTap(root, TAP_BATTERY);
          break;
        default:
          icon.setImageResource(R.drawable.ic_quickspace_minimal_psa);
          setText(title, glance.psa);
          setText(subtitle, null);
          bindTap(root, TAP_EVENT_ACTION);
          break;
      }
    }

    void applyColors(int tileColor) {
      root.setBackground(rounded(tileColor, 20, 0));
      icon.setImageTintList(ColorStateList.valueOf(mIconColor));
      equalizer.setColor(mFillColor);
      applyText(title, mTextColor);
      applyText(subtitle, textColor(0.8f));
      applyText(levelText, textColor(0.85f));
      ClipDrawable fill = new ClipDrawable(rounded(mFillColor, 3, 0), Gravity.START,
          ClipDrawable.HORIZONTAL);
      LayerDrawable bar = new LayerDrawable(new Drawable[] {rounded(textColor(0.22f), 3, 0), fill});
      level.setBackground(bar);
    }
  }

  public GlassGlanceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    mCard = findViewById(R.id.glance_glass_card);
    mGreeting = findViewById(R.id.glance_glass_greeting);
    mWeatherChip = findViewById(R.id.glance_glass_weather);
    mWeatherIcon = findViewById(R.id.glance_glass_weather_icon);
    mTemperature = findViewById(R.id.glance_glass_temperature);
    mDate = findViewById(R.id.glance_glass_date);
    mTiles = findViewById(R.id.glance_glass_tiles);
    mTileA = new Tile(R.id.glance_glass_tile_a, R.id.glance_glass_tile_a_icon,
        R.id.glance_glass_tile_a_equalizer, R.id.glance_glass_tile_a_title,
        R.id.glance_glass_tile_a_subtitle, R.id.glance_glass_tile_a_level_row,
        R.id.glance_glass_tile_a_level, R.id.glance_glass_tile_a_level_text);
    mTileB = new Tile(R.id.glance_glass_tile_b, R.id.glance_glass_tile_b_icon,
        R.id.glance_glass_tile_b_equalizer, R.id.glance_glass_tile_b_title,
        R.id.glance_glass_tile_b_subtitle, R.id.glance_glass_tile_b_level_row,
        R.id.glance_glass_tile_b_level, R.id.glance_glass_tile_b_level_text);
    bindTap(mDate, TAP_CALENDAR);
    bindTap(mWeatherChip, TAP_WEATHER);
  }

  @Override
  protected void onBindGlance(Glance glance, boolean animate) {
    if (animate) {
      TransitionManager.beginDelayedTransition(this, new AutoTransition().setDuration(REFLOW_MS));
    }
    setText(mGreeting, glance.greeting);
    boolean weather = glance.temperature != null;
    mWeatherChip.setVisibility(weather ? VISIBLE : GONE);
    mWeatherIcon.setImageDrawable(glance.weatherIcon);
    mWeatherIcon.setVisibility(glance.weatherIcon != null ? VISIBLE : GONE);
    mTemperature.setText(glance.temperature);
    mDate.setText(glance.dateLine);

    List<Integer> tiles = new ArrayList<>();
    if (glance.eventTitle != null) tiles.add(TILE_EVENT);
    if (glance.nowPlaying) tiles.add(TILE_SONG);
    if (glance.batteryLevel >= 0) tiles.add(TILE_BATTERY);
    if (glance.psa != null) tiles.add(TILE_PSA);
    mTiles.setVisibility(tiles.isEmpty() ? GONE : VISIBLE);
    mTileA.root.setVisibility(tiles.size() > 0 ? VISIBLE : GONE);
    mTileB.root.setVisibility(tiles.size() > 1 ? VISIBLE : GONE);
    if (tiles.size() > 0) mTileA.bind(tiles.get(0), glance);
    if (tiles.size() > 1) mTileB.bind(tiles.get(1), glance);
    mTileA.equalizer.setPlaying(tiles.size() > 0 && tiles.get(0) == TILE_SONG);
    mTileB.equalizer.setPlaying(tiles.size() > 1 && tiles.get(1) == TILE_SONG);
  }

  @Override
  protected void onColorsChanged() {
    Context context = getContext();
    mIconColor = context.getColor(mBlackText
        ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
    mFillColor = context.getColor(mBlackText
        ? android.R.color.system_accent1_600 : android.R.color.system_accent1_300);
    int card = mBlackText ? Color.argb(140, 255, 255, 255) : Color.argb(34, 255, 255, 255);
    int edge = mBlackText ? Color.argb(90, 255, 255, 255) : Color.argb(52, 255, 255, 255);
    GradientDrawable highlight = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] {Color.argb(mBlackText ? 40 : 28, 255, 255, 255), Color.TRANSPARENT});
    highlight.setCornerRadius(28 * mDensity);
    mCard.setBackground(new LayerDrawable(new Drawable[] {rounded(card, 28, edge), highlight}));
    int inner = mBlackText ? Color.argb(110, 255, 255, 255) : Color.argb(56, 0, 0, 0);
    mWeatherChip.setBackground(rounded(inner, 14, 0));
    applyText(mGreeting, textColor(0.9f));
    applyText(mTemperature, mTextColor);
    applyText(mDate, mTextColor);
    mTileA.applyColors(inner);
    mTileB.applyColors(inner);
    rebind();
  }

  private GradientDrawable rounded(int color, float radiusDp, int strokeColor) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(radiusDp * mDensity);
    if (strokeColor != 0) shape.setStroke(Math.max(1, Math.round(mDensity)), strokeColor);
    return shape;
  }
}
