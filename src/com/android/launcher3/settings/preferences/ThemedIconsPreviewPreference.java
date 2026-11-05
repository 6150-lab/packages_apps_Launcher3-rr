package com.android.launcher3.settings.preferences;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;
import com.android.launcher3.icons.ThemedIconSettings;

import java.util.ArrayList;
import java.util.List;

/** RR: a row of real apps drawn with the themed icon settings above and below it. */
public class ThemedIconsPreviewPreference extends Preference {

    private static final int ICON_COUNT = 5;
    private static final String[] KEYS = {
            ThemedIconSettings.KEY_CUSTOMIZE,
            ThemedIconSettings.KEY_ICON_SCALE,
            ThemedIconSettings.KEY_COLOR_PRESET,
            ThemedIconSettings.KEY_BACKGROUND_COLOR,
            ThemedIconSettings.KEY_FOREGROUND_COLOR,
    };

    private final List<Drawable> mMonoIcons = new ArrayList<>();
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            notifyChanged();
        }
    };

    public ThemedIconsPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_themed_icons_preview);
        setSelectable(false);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        for (String key : KEYS) {
            getContext().getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(key), false, mObserver);
        }
        THREAD_POOL_EXECUTOR.execute(this::loadIcons);
    }

    @Override
    public void onDetached() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onDetached();
    }

    private void loadIcons() {
        PackageManager pm = getContext().getPackageManager();
        List<Drawable> icons = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (info.loadIcon(pm) instanceof AdaptiveIconDrawable adaptive
                    && adaptive.getMonochrome() != null) {
                icons.add(adaptive.getMonochrome());
                if (icons.size() == ICON_COUNT) break;
            }
        }
        MAIN_EXECUTOR.execute(() -> {
            mMonoIcons.clear();
            mMonoIcons.addAll(icons);
            notifyChanged();
        });
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Context context = getContext();
        LinearLayout row = (LinearLayout) holder.itemView;
        int background = ThemedIconSettings.getBackgroundColor(context);
        int foreground = ThemedIconSettings.getForegroundColor(context);
        float inset = ThemedIconSettings.getIconInsetFraction(context);
        for (int i = 0; i < row.getChildCount() && i < mMonoIcons.size(); i++) {
            Drawable mono = mMonoIcons.get(i).getConstantState().newDrawable().mutate();
            Drawable glyph = new InsetDrawable(mono, inset);
            glyph.setTint(foreground);
            ((ImageView) row.getChildAt(i)).setImageDrawable(
                    new AdaptiveIconDrawable(new ColorDrawable(background), glyph));
        }
    }
}
