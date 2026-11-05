package com.android.launcher3.icons.pack;

import static com.android.launcher3.icons.BaseIconFactory.CONFIG_HINT_NO_WRAP;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

import com.android.launcher3.icons.clock.CustomClock;
import com.android.launcher3.icons.mono.NeonIcons;

/** RR: Neon as a per-app icon pack. The app's own glyph, or its first letter, in a neon ring. */
final class IconResolverNeon implements IconResolver {

    static final String PACK = "rr.neon";
    private static final int SIZE = 256;

    private final Context mContext;
    private final String mPackage;

    IconResolverNeon(Context context, String packageName) {
        mContext = context;
        mPackage = packageName;
    }

    @Override
    public boolean isCalendar() {
        return false;
    }

    @Override
    public boolean isClock() {
        return false;
    }

    @Override
    public CustomClock.Metadata clockData() {
        return null;
    }

    @Override
    public Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int color = NeonIcons.ringColor;
        Rect glyph = NeonIcons.drawRing(canvas, new Rect(0, 0, SIZE, SIZE), 255, color);

        Drawable base = fallback.get();
        Drawable mono = base instanceof AdaptiveIconDrawable adaptive
                ? adaptive.getMonochrome() : null;
        if (mono != null) {
            Drawable inset = new InsetDrawable(mono.mutate(),
                    -AdaptiveIconDrawable.getExtraInsetFraction());
            inset.setTint(color);
            inset.setBounds(glyph);
            inset.draw(canvas);
        } else {
            Bitmap letter = NeonIcons.letterBitmap(mContext, mPackage, glyph.width());
            if (letter != null) {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                paint.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_IN));
                canvas.drawBitmap(letter, null, glyph, paint);
            }
        }
        BitmapDrawable icon = new BitmapDrawable(mContext.getResources(), bitmap);
        icon.setChangingConfigurations(
                icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        return icon;
    }
}
