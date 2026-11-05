package com.android.launcher3.settings;

import static android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED;

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;

import static com.android.launcher3.BuildConfig.IS_STUDIO_BUILD;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_MULTI_DISPLAY;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;

import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.customization.IconDatabase;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.icons.ThemedIconSettings;
import com.android.launcher3.settings.preferences.ColorPreference;
import com.android.launcher3.settings.preferences.CustomSeekBarPreference;
import com.android.launcher3.icons.pack.IconPackSettingsActivity;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SettingsCache;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

public class SettingsIcons extends CollapsingToolbarBaseActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    @VisibleForTesting
    static final String DEVELOPER_OPTIONS_KEY = "pref_developer_options";

    public static final String FIXED_LANDSCAPE_MODE = "pref_fixed_landscape_mode";

    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    private static final String KEY_THEMED_ICONS = ThemedIconSettings.KEY_CUSTOMIZE;
    private static final String KEY_THEMED_ICON_SCALE = ThemedIconSettings.KEY_ICON_SCALE;
    private static final String KEY_THEMED_ICON_COLOR_PRESET = ThemedIconSettings.KEY_COLOR_PRESET;
    private static final String KEY_THEMED_ICON_BACKGROUND = ThemedIconSettings.KEY_BACKGROUND_COLOR;
    private static final String KEY_THEMED_ICON_FOREGROUND = ThemedIconSettings.KEY_FOREGROUND_COLOR;

    private ContentObserver mThemedIconSettingsObserver;

    private static final String[] THEMED_ICON_SECURE_KEYS = {
            ThemedIconSettings.KEY_CUSTOMIZE,
            ThemedIconSettings.KEY_ICON_SCALE,
            ThemedIconSettings.KEY_COLOR_PRESET,
            ThemedIconSettings.KEY_BACKGROUND_COLOR,
            ThemedIconSettings.KEY_FOREGROUND_COLOR,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        mThemedIconSettingsObserver = new ContentObserver(new Handler(getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                LauncherAppState.INSTANCE.executeIfCreated(app -> app.setNeedsRestart());
            }
        };
        for (String key : THEMED_ICON_SECURE_KEYS) {
            getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(key), false, mThemedIconSettingsObserver);
        }

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            Bundle args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS);
            if (args == null) {
                args = new Bundle();
            }

            String highlight = intent.getStringExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY);
            if (!TextUtils.isEmpty(highlight)) {
                args.putString(EXTRA_FRAGMENT_HIGHLIGHT_KEY, highlight);
            }
            String root = intent.getStringExtra(EXTRA_FRAGMENT_ROOT_KEY);
            if (!TextUtils.isEmpty(root)) {
                args.putString(EXTRA_FRAGMENT_ROOT_KEY, root);
            }

            final FragmentManager fm = getSupportFragmentManager();
            final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(),
                    getString(R.string.icons_settings_fragment_name));
            f.setArguments(args);
            fm.beginTransaction().replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, f).commit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mThemedIconSettingsObserver);
    }

    private boolean startPreference(String fragment, Bundle args, String key) {
        if (getSupportFragmentManager().isStateSaved()) {
            return false;
        }
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(), fragment);
        if (f instanceof DialogFragment) {
            f.setArguments(args);
            ((DialogFragment) f).show(fm, key);
        } else {
            startActivity(new Intent(this, SettingsIcons.class)
                    .putExtra(EXTRA_FRAGMENT_ARGS, args));
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return startPreference(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(ARG_PREFERENCE_ROOT, pref.getKey());
        return startPreference(getString(R.string.icons_settings_fragment_name), args, pref.getKey());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class IconsSettingsFragment extends SettingsBasePreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {
        private @Nullable SafeCloseable mSettingCacheSafeCloseable;

        protected boolean mDeveloperOptionsEnabled = false;

        private boolean mRestartOnResume = false;

        private String mHighLightKey;

        private boolean mPreferenceHighlighted = false;

        private ColorPreference mBackgroundColorPref;
        private ColorPreference mForegroundColorPref;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            if (BuildConfig.IS_DEBUG_DEVICE) {
                Uri devUri = Settings.Global.getUriFor(DEVELOPMENT_SETTINGS_ENABLED);
                SettingsCache settingsCache = SettingsCache.INSTANCE.get(getContext());
                mDeveloperOptionsEnabled = settingsCache.getValue(devUri);
                mSettingCacheSafeCloseable = settingsCache.getListenableRef(devUri).forEach(
                        MAIN_EXECUTOR, (v) -> {
                            if (v != mDeveloperOptionsEnabled) {
                                tryRecreateActivity();
                            }
                            return null;
                        });
            }
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();
            mHighLightKey = args == null ? null : args.getString(EXTRA_FRAGMENT_HIGHLIGHT_KEY);

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.launcher_icons_preferences, rootKey);

            updatePreferences();

            LauncherPrefs.getPrefs(getContext())
                    .registerOnSharedPreferenceChangeListener(this);

            PreferenceScreen screen = getPreferenceScreen();
            initPreferences(screen);

            if (mHighLightKey != null
                    && !isKeyInPreferenceGroup(mHighLightKey, screen)) {
                final PreferenceScreen parentPreferenceScreen =
                        findParentPreference(screen, mHighLightKey);
                if (parentPreferenceScreen != null && getActivity() != null) {
                    if (!TextUtils.isEmpty(parentPreferenceScreen.getTitle())) {
                        getActivity().setTitle(parentPreferenceScreen.getTitle());
                    }
                    setPreferenceScreen(parentPreferenceScreen);
                    return;
                }
            }

            if (getActivity() != null && !TextUtils.isEmpty(getPreferenceScreen().getTitle())) {
                getActivity().setTitle(getPreferenceScreen().getTitle());
            }
        }

        private void updatePreferences() {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) return;
            initPreferences(screen);
        }

        private void initPreferences(PreferenceGroup group) {
            for (int i = group.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = group.getPreference(i);
                if (!initPreference(preference)) {
                    group.removePreference(preference);
                } else if (preference instanceof PreferenceGroup) {
                    initPreferences((PreferenceGroup) preference);
                }
            }
        }

        private boolean isKeyInPreferenceGroup(String targetKey, PreferenceGroup parent) {
            for (int i = 0; i < parent.getPreferenceCount(); i++) {
                Preference pref = parent.getPreference(i);
                if (pref.getKey() != null && pref.getKey().equals(targetKey)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        private PreferenceScreen findParentPreference(PreferenceScreen parent, String targetKey) {
            for (int i = 0; i < parent.getPreferenceCount(); i++) {
                Preference pref = parent.getPreference(i);
                if (pref instanceof PreferenceScreen) {
                    PreferenceScreen foundKey = findParentPreference((PreferenceScreen) pref,
                            targetKey);
                    if (foundKey != null) {
                        return foundKey;
                    }
                } else if (pref.getKey() != null && pref.getKey().equals(targetKey)) {
                    return parent;
                }
            }
            return null;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View listView = getListView();
            final int bottomPadding = listView.getPaddingBottom();
            listView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        bottomPadding + insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            });

            view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            switch (key) {
                case IconDatabase.KEY_ICON_PACK:
                    updatePreferences();
                    break;
                case "pref_themed_icons_overlay":
                    com.android.launcher3.Utilities.restart();
                    break;
                case "pref_neon_ring_color":
                case "pref_neon_glow":
                case "pref_neon_ring":
                case "pref_neon_folders":
                    com.android.launcher3.Utilities.restart();
                    break;
            }
        }

        protected boolean initPreference(Preference preference) {
            String key = preference.getKey();
            if (key == null) {
                return true;
            }
            switch (key) {
                case NOTIFICATION_DOTS_PREFERENCE_KEY:
                    return BuildConfig.NOTIFICATION_DOTS_ENABLED;
                case "pref_icon_style_rr":
                    setupIconStyle((androidx.preference.ListPreference) preference);
                    return true;
                case "pref_neon_category":
                    preference.setVisible(LauncherPrefs.get(getContext())
                            .get(LauncherPrefs.NEON_ICONS));
                    return true;
                case "pref_themed_icons_category": {
                    boolean mono = com.android.launcher3.graphics.theme.ThemePreference
                            .MONO_THEME_VALUE.equals(com.android.launcher3.dagger
                                    .LauncherComponentProvider.get(getContext())
                                    .getThemePreference().getValue());
                    preference.setVisible(mono && !LauncherPrefs.get(getContext())
                            .get(LauncherPrefs.NEON_ICONS));
                    return true;
                }
                case IconDatabase.KEY_ICON_PACK:
                    setupIconPackPreference(preference);
                    return true;
                case KEY_THEMED_ICONS:
                    setupThemedIconsSwitch(preference);
                    return true;
                case KEY_THEMED_ICON_SCALE:
                    setupThemedIconScale(preference);
                    return true;
                case KEY_THEMED_ICON_COLOR_PRESET:
                    setupThemedIconColorPreset(preference);
                    return true;
                case KEY_THEMED_ICON_BACKGROUND:
                    mBackgroundColorPref = (ColorPreference) preference;
                    setupThemedIconColor(preference, true /* background */);
                    return true;
                case KEY_THEMED_ICON_FOREGROUND:
                    mForegroundColorPref = (ColorPreference) preference;
                    setupThemedIconColor(preference, false /* background */);
                    return true;
            }
            return true;
        }

        /**
         * RR: Default, Themed or Neon. Themed and Neon both use the launcher's mono theme; Neon
         * draws its icons as rings.
         */
        private void setupIconStyle(androidx.preference.ListPreference preference) {
            com.android.launcher3.graphics.theme.ThemePreference theme =
                    com.android.launcher3.dagger.LauncherComponentProvider.get(getContext())
                            .getThemePreference();
            boolean mono = com.android.launcher3.graphics.theme.ThemePreference.MONO_THEME_VALUE
                    .equals(theme.getValue());
            boolean neon = LauncherPrefs.get(getContext()).get(LauncherPrefs.NEON_ICONS);
            preference.setValue(neon && mono ? "neon" : mono ? "themed" : "default");
            preference.setOnPreferenceChangeListener((p, value) -> {
                String style = (String) value;
                LauncherPrefs.get(getContext()).putSync(
                        kotlin.TuplesKt.to(LauncherPrefs.NEON_ICONS, "neon".equals(style)));
                theme.setValue("default".equals(style) ? null
                        : com.android.launcher3.graphics.theme.ThemePreference.MONO_THEME_VALUE);
                com.android.launcher3.Utilities.restart();
                return true;
            });
        }

        @Override
        public void onResume() {
            super.onResume();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }

            if (mRestartOnResume) {
                recreateActivityNow();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mSettingCacheSafeCloseable != null) {
                mSettingCacheSafeCloseable.close();
                mSettingCacheSafeCloseable = null;
            }
            if (getContext() != null) {
                LauncherPrefs.getPrefs(getContext())
                        .unregisterOnSharedPreferenceChangeListener(this);
            }
        }

        protected void tryRecreateActivity() {
            if (isResumed()) {
                recreateActivityNow();
            } else {
                mRestartOnResume = true;
            }
        }

        private void recreateActivityNow() {
            Activity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(
                    list, position, screen.findPreference(mHighLightKey))
                    : null;
        }

        private void setupIconPackPreference(Preference preference) {
            final String pkgLabel = IconDatabase.getGlobalLabel(getContext());
            preference.setSummary(pkgLabel);
            preference.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getContext(), IconPackSettingsActivity.class));
                return true;
            });
        }

        private void setupThemedIconsSwitch(Preference preference) {
            ((androidx.preference.TwoStatePreference) preference)
                    .setChecked(ThemedIconSettings.isCustomized(getContext()));
            preference.setOnPreferenceChangeListener((p, newValue) -> {
                Settings.Secure.putInt(getContext().getContentResolver(),
                        ThemedIconSettings.KEY_CUSTOMIZE, ((Boolean) newValue) ? 1 : 0);
                return true;
            });
        }

        private void setupThemedIconScale(Preference preference) {
            CustomSeekBarPreference scalePref = (CustomSeekBarPreference) preference;
            scalePref.setValue(ThemedIconSettings.getIconScale(getContext()));
            scalePref.setOnPreferenceChangeListener((p, newValue) -> {
                Settings.Secure.putInt(getContext().getContentResolver(),
                        ThemedIconSettings.KEY_ICON_SCALE, (Integer) newValue);
                return true;
            });
        }

        private void setupThemedIconColorPreset(Preference preference) {
            ListPreference presetPref = (ListPreference) preference;
            String current = ThemedIconSettings.getColorPreset(getContext());
            presetPref.setValue(current);
            updateColorPrefsVisibility(current);
            presetPref.setOnPreferenceChangeListener((p, newValue) -> {
                String preset = (String) newValue;
                Settings.Secure.putString(getContext().getContentResolver(),
                        ThemedIconSettings.KEY_COLOR_PRESET, preset);
                updateColorPrefsVisibility(preset);
                return true;
            });
        }

        private void updateColorPrefsVisibility(String preset) {
            boolean showCustomColors = ThemedIconSettings.COLOR_PRESET_CUSTOM.equals(preset);
            if (mBackgroundColorPref != null) {
                mBackgroundColorPref.setVisible(showCustomColors);
            }
            if (mForegroundColorPref != null) {
                mForegroundColorPref.setVisible(showCustomColors);
            }
        }

        private void setupThemedIconColor(Preference preference, boolean background) {
            ColorPreference colorPref = (ColorPreference) preference;
            int current = background
                    ? ThemedIconSettings.getBackgroundColor(getContext())
                    : ThemedIconSettings.getForegroundColor(getContext());
            colorPref.setValue(current);
            colorPref.setOnPreferenceChangeListener((p, newValue) -> {
                Settings.Secure.putString(getContext().getContentResolver(),
                        background ? ThemedIconSettings.KEY_BACKGROUND_COLOR
                                : ThemedIconSettings.KEY_FOREGROUND_COLOR,
                        String.valueOf((int) (Integer) newValue));
                Settings.Secure.putString(getContext().getContentResolver(),
                        ThemedIconSettings.KEY_COLOR_PRESET,
                        ThemedIconSettings.COLOR_PRESET_CUSTOM);
                return true;
            });
        }
    }

    public interface OnResumePreferenceCallback {
        void onResume();
    }
}
