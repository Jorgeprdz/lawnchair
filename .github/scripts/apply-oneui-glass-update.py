from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def write(path: str, content: str):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")

def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Patch anchor not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

write("src/com/android/launcher3/graphics/OneUiGlassPreferences.java", r'''package com.android.launcher3.graphics;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight preferences for One UI glass surfaces shared by dock and folders. */
public final class OneUiGlassPreferences {
    public static final int MODE_OFF = 0;
    public static final int MODE_SOLID = 1;
    public static final int MODE_BLUR = 2;
    public static final int MODE_CRYSTAL = 3;

    private static final String FILE = "oneui_glass_surface";
    private static final String DOCK_BLUR = "dock_blur_intensity";
    private static final String DOCK_CRYSTAL = "dock_crystal_intensity";
    private static final String FOLDER_MODE = "folder_background_mode";
    private static final String FOLDER_BLUR = "folder_blur_intensity";
    private static final String FOLDER_CRYSTAL = "folder_crystal_intensity";

    private static final int DEFAULT_BLUR = 65;
    private static final int DEFAULT_CRYSTAL = 55;

    private OneUiGlassPreferences() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }

    public static int getDockBlurIntensity(Context context) {
        return clamp(prefs(context).getInt(DOCK_BLUR, DEFAULT_BLUR));
    }
    public static void setDockBlurIntensity(Context context, int value) {
        prefs(context).edit().putInt(DOCK_BLUR, clamp(value)).apply();
    }
    public static int getDockCrystalIntensity(Context context) {
        return clamp(prefs(context).getInt(DOCK_CRYSTAL, DEFAULT_CRYSTAL));
    }
    public static void setDockCrystalIntensity(Context context, int value) {
        prefs(context).edit().putInt(DOCK_CRYSTAL, clamp(value)).apply();
    }
    public static int getFolderBackgroundMode(Context context) {
        int mode = prefs(context).getInt(FOLDER_MODE, MODE_SOLID);
        return mode >= MODE_OFF && mode <= MODE_CRYSTAL ? mode : MODE_SOLID;
    }
    public static void setFolderBackgroundMode(Context context, int mode) {
        prefs(context).edit().putInt(FOLDER_MODE,
                Math.max(MODE_OFF, Math.min(MODE_CRYSTAL, mode))).apply();
    }
    public static int getFolderBlurIntensity(Context context) {
        return clamp(prefs(context).getInt(FOLDER_BLUR, DEFAULT_BLUR));
    }
    public static void setFolderBlurIntensity(Context context, int value) {
        prefs(context).edit().putInt(FOLDER_BLUR, clamp(value)).apply();
    }
    public static int getFolderCrystalIntensity(Context context) {
        return clamp(prefs(context).getInt(FOLDER_CRYSTAL, DEFAULT_CRYSTAL));
    }
    public static void setFolderCrystalIntensity(Context context, int value) {
        prefs(context).edit().putInt(FOLDER_CRYSTAL, clamp(value)).apply();
    }
}
''')

write("src/com/android/launcher3/graphics/OneUiGlassSurface.java", r'''package com.android.launcher3.graphics;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

/** Shared Samsung/One UI inspired frosted and crystal glass renderer. */
public final class OneUiGlassSurface {
    private OneUiGlassSurface() { }

    public static Drawable create(View host, boolean crystal, int color,
            float cornerRadius, int intensity) {
        int level = Math.max(0, Math.min(100, intensity));
        if (level == 0) return new ColorDrawable(Color.TRANSPARENT);

        final float t = level / 100f;
        final float density = host.getResources().getDisplayMetrics().density;
        // Frosty blur intentionally spreads much more than Crystal. This produces the
        // milky diffusion used by One UI instead of the sharp acrylic look of stock blur.
        final int blurRadius = Math.round((crystal ? (8f + 18f * t) : (18f + 46f * t)) * density);
        Drawable blur = createPlatformBlur(host, blurRadius, cornerRadius);

        final boolean dark = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final float sourceAlpha = Color.alpha(color) / 255f;
        final float tintFactor = crystal ? (0.08f + 0.10f * t) : (0.14f + 0.18f * t);
        int tintAlpha = Math.round(255f * sourceAlpha * tintFactor);
        GradientDrawable tint = rounded(cornerRadius,
                Color.argb(tintAlpha, Color.red(color), Color.green(color), Color.blue(color)));

        // Neutral haze is the "frost": it lifts blacks and softens wallpaper colour without
        // turning high intensity into an opaque white panel.
        float hazeFactor = crystal ? (0.025f + 0.055f * t) : (0.07f + 0.20f * t);
        if (blur == null) hazeFactor += crystal ? 0.06f : 0.14f;
        int hazeBase = dark ? 0xFF202124 : 0xFFFFFFFF;
        GradientDrawable haze = rounded(cornerRadius,
                Color.argb(Math.round(255f * hazeFactor), Color.red(hazeBase),
                        Color.green(hazeBase), Color.blue(hazeBase)));

        int top = Color.argb(Math.round((crystal ? 40 : 28) * t), 255, 255, 255);
        int middle = Color.argb(Math.round((crystal ? 10 : 7) * t), 255, 255, 255);
        int bottom = Color.argb(Math.round((crystal ? 14 : 8) * t), 0, 0, 0);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {top, middle, Color.TRANSPARENT, bottom});
        sheen.setCornerRadius(cornerRadius);
        sheen.setStroke(Math.max(1, Math.round(density)),
                Color.argb(Math.round((crystal ? 70 : 38) * t), 255, 255, 255));

        Drawable[] layers = blur == null
                ? new Drawable[] {tint, haze, sheen}
                : new Drawable[] {blur, tint, haze, sheen};
        return new LayerDrawable(layers);
    }

    private static GradientDrawable rounded(float corners, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(corners);
        drawable.setColor(color);
        return drawable;
    }

    public static View findActivityRoot(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity activity) {
                return activity.getWindow().getDecorView();
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) break;
            current = base;
        }
        return null;
    }

    private static Drawable createPlatformBlur(View host, int radius, float corners) {
        if (Build.VERSION.SDK_INT < 31 || host == null || !host.isAttachedToWindow()
                || !host.isHardwareAccelerated()) return null;
        Drawable drawable = null;
        try {
            WindowManager manager = host.getContext().getSystemService(WindowManager.class);
            if (manager == null || !manager.isCrossWindowBlurEnabled()) return null;
            Object root = View.class.getMethod("getViewRootImpl").invoke(host);
            if (root == null) return null;
            drawable = (Drawable) root.getClass().getMethod("createBackgroundBlurDrawable")
                    .invoke(root);
            drawable.getClass().getMethod("setBlurRadius", int.class).invoke(drawable, radius);
            drawable.getClass().getMethod("setCornerRadius", float.class).invoke(drawable, corners);
            return drawable;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if (drawable != null) drawable.setVisible(false, false);
            return null;
        }
    }
}
''')

write("src/com/android/launcher3/graphics/OneUiFolderBackgroundDrawable.java", r'''package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** GradientDrawable-compatible folder background that can render shared One UI glass. */
public class OneUiFolderBackgroundDrawable extends GradientDrawable {
    private final Context mContext;
    private int mBaseColor = Color.TRANSPARENT;
    private int mRequestedAlpha = 255;
    private Drawable mGlass;
    private int mLastMode = -1;
    private int mLastIntensity = -1;
    private int mLastColor = 0;

    public OneUiFolderBackgroundDrawable(Context context) {
        mContext = context;
        setShape(RECTANGLE);
    }

    @Override
    public void setColor(int color) {
        mBaseColor = color;
        super.setColor(color);
    }

    @Override
    public void setAlpha(int alpha) {
        mRequestedAlpha = Math.max(0, Math.min(255, alpha));
        super.setAlpha(alpha);
    }

    @Override
    public void draw(Canvas canvas) {
        int mode = OneUiGlassPreferences.getFolderBackgroundMode(mContext);
        if (mode == OneUiGlassPreferences.MODE_OFF) return;
        if (mode == OneUiGlassPreferences.MODE_SOLID) {
            super.draw(canvas);
            return;
        }
        int intensity = mode == OneUiGlassPreferences.MODE_CRYSTAL
                ? OneUiGlassPreferences.getFolderCrystalIntensity(mContext)
                : OneUiGlassPreferences.getFolderBlurIntensity(mContext);
        if (intensity == 0) return;

        int color = Color.argb(mRequestedAlpha, Color.red(mBaseColor),
                Color.green(mBaseColor), Color.blue(mBaseColor));
        if (mGlass == null || mode != mLastMode || intensity != mLastIntensity
                || color != mLastColor) {
            View host = OneUiGlassSurface.findActivityRoot(mContext);
            if (host == null) {
                // A temporary unattached context still gets a translucent frosty fallback.
                super.setAlpha(Math.min(mRequestedAlpha, mode == 3 ? 48 : 92));
                super.draw(canvas);
                super.setAlpha(mRequestedAlpha);
                return;
            }
            mGlass = OneUiGlassSurface.create(host,
                    mode == OneUiGlassPreferences.MODE_CRYSTAL,
                    color, getCornerRadius(), intensity);
            mLastMode = mode;
            mLastIntensity = intensity;
            mLastColor = color;
        }
        mGlass.setBounds(getBounds());
        mGlass.draw(canvas);
    }
}
''')

write("lawnchair/src/app/lawnchair/ui/preferences/components/controls/OneUiGlassPreferencesUi.kt", r'''package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R
import com.android.launcher3.graphics.OneUiGlassPreferences
import kotlin.math.roundToInt

@Composable
private fun GlassIntensityPreference(label: String, initialValue: Int, onChange: (Int) -> Unit) {
    var value by remember(initialValue) { mutableFloatStateOf(initialValue.toFloat()) }
    PreferenceTemplate(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text("${value.roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        description = {
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    onChange(it.roundToInt())
                },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp).height(24.dp),
            )
        },
    )
}

@Composable
fun OneUiDockGlassIntensityPreferences(selectedMode: Int) {
    val context = LocalContext.current
    if (selectedMode == OneUiGlassPreferences.MODE_BLUR) {
        GlassIntensityPreference(
            label = "Blur intensity",
            initialValue = OneUiGlassPreferences.getDockBlurIntensity(context),
            onChange = { OneUiGlassPreferences.setDockBlurIntensity(context, it) },
        )
    } else if (selectedMode == OneUiGlassPreferences.MODE_CRYSTAL) {
        GlassIntensityPreference(
            label = "Crystal intensity",
            initialValue = OneUiGlassPreferences.getDockCrystalIntensity(context),
            onChange = { OneUiGlassPreferences.setDockCrystalIntensity(context, it) },
        )
    }
}

@Composable
fun OneUiFolderGlassPreferences() {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(OneUiGlassPreferences.getFolderBackgroundMode(context)) }
    ListPreference(
        entries = listOf(
            ListPreferenceEntry(0) { stringResource(R.string.dock_background_off) },
            ListPreferenceEntry(1) { stringResource(R.string.dock_background_solid) },
            ListPreferenceEntry(2) { stringResource(R.string.dock_background_blur) },
            ListPreferenceEntry(3) { stringResource(R.string.dock_background_crystal) },
        ),
        value = mode,
        onValueChange = {
            mode = it
            OneUiGlassPreferences.setFolderBackgroundMode(context, it)
        },
        label = "Background style",
    )
    if (mode == OneUiGlassPreferences.MODE_BLUR) {
        GlassIntensityPreference(
            label = "Blur intensity",
            initialValue = OneUiGlassPreferences.getFolderBlurIntensity(context),
            onChange = { OneUiGlassPreferences.setFolderBlurIntensity(context, it) },
        )
    } else if (mode == OneUiGlassPreferences.MODE_CRYSTAL) {
        GlassIntensityPreference(
            label = "Crystal intensity",
            initialValue = OneUiGlassPreferences.getFolderCrystalIntensity(context),
            onChange = { OneUiGlassPreferences.setFolderCrystalIntensity(context, it) },
        )
    }
}
''')

write("src/com/android/launcher3/graphics/DockGlassBackground.java", r'''/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.launcher3.graphics;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Live dock glass drawable backed by the shared One UI frosted/crystal renderer. */
public final class DockGlassBackground {
    private DockGlassBackground() { }

    public static Drawable create(View host, boolean crystal, int color, float cornerRadius) {
        return new LiveDockGlassDrawable(host, crystal, color, cornerRadius);
    }

    private static final class LiveDockGlassDrawable extends Drawable {
        private final View mHost;
        private final boolean mCrystal;
        private final int mColor;
        private final float mCornerRadius;
        private Drawable mDelegate;
        private int mLastIntensity = -1;
        private int mAlpha = 255;

        LiveDockGlassDrawable(View host, boolean crystal, int color, float cornerRadius) {
            mHost = host;
            mCrystal = crystal;
            mColor = color;
            mCornerRadius = cornerRadius;
        }

        private int intensity() {
            return mCrystal
                    ? OneUiGlassPreferences.getDockCrystalIntensity(mHost.getContext())
                    : OneUiGlassPreferences.getDockBlurIntensity(mHost.getContext());
        }

        @Override
        public void draw(Canvas canvas) {
            int intensity = intensity();
            if (mDelegate == null || intensity != mLastIntensity) {
                if (mDelegate != null) mDelegate.setVisible(false, false);
                mDelegate = OneUiGlassSurface.create(mHost, mCrystal, mColor,
                        mCornerRadius, intensity);
                mLastIntensity = intensity;
                mDelegate.setAlpha(mAlpha);
            }
            mDelegate.setBounds(getBounds());
            mDelegate.draw(canvas);
        }

        @Override public void setAlpha(int alpha) {
            mAlpha = alpha;
            if (mDelegate != null) mDelegate.setAlpha(alpha);
            invalidateSelf();
        }
        @Override public void setColorFilter(ColorFilter colorFilter) {
            if (mDelegate != null) mDelegate.setColorFilter(colorFilter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            if (mDelegate != null) mDelegate.setVisible(visible, restart);
            return super.setVisible(visible, restart);
        }
    }
}
''')

replace(
    "lawnchair/src/app/lawnchair/theme/drawable/DrawableTokens.kt",
    "import com.android.launcher3.R\n",
    "import com.android.launcher3.R\nimport com.android.launcher3.graphics.OneUiFolderBackgroundDrawable\n",
)
replace(
    "lawnchair/src/app/lawnchair/theme/drawable/DrawableTokens.kt",
    '''    @JvmField\n    val RoundRectFolder = ResourceDrawableToken<GradientDrawable>(R.drawable.round_rect_folder)\n        .setColor(ColorTokens.FolderBackgroundColor)\n''',
    '''    @JvmField\n    val RoundRectFolder = NewDrawable { context, scheme, uiColorMode ->\n        OneUiFolderBackgroundDrawable(context).apply {\n            cornerRadius = context.resources.getDimension(R.dimen.bg_round_rect_radius)\n            setColor(ColorTokens.FolderBackgroundColor.resolveColor(context, scheme, uiColorMode))\n        }\n    }\n''',
)

replace(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/DockPreferences.kt",
    "import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference\n",
    "import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference\nimport app.lawnchair.ui.preferences.components.controls.OneUiDockGlassIntensityPreferences\n",
)
replace(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/DockPreferences.kt",
    "HotseatBackgroundSettings(prefs, prefs2)",
    "HotseatBackgroundSettings(prefs, prefs2, selectedMode)",
)
replace(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/DockPreferences.kt",
    "fun HotseatBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {\n    DividerColumn {\n        ColorPreference(preference = prefs2.hotseatBackgroundColor)\n",
    "fun HotseatBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2, selectedMode: Int) {\n    DividerColumn {\n        ColorPreference(preference = prefs2.hotseatBackgroundColor)\n        OneUiDockGlassIntensityPreferences(selectedMode)\n",
)

replace(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/FolderPreferences.kt",
    "import app.lawnchair.ui.preferences.components.controls.SliderPreference\n",
    "import app.lawnchair.ui.preferences.components.controls.SliderPreference\nimport app.lawnchair.ui.preferences.components.controls.OneUiFolderGlassPreferences\n",
)
replace(
    "lawnchair/src/app/lawnchair/ui/preferences/destinations/FolderPreferences.kt",
    "            ColorPreference(preference = prefs2.folderColor)\n",
    "            OneUiFolderGlassPreferences()\n            ColorPreference(preference = prefs2.folderColor)\n",
)

replace(
    "src/com/android/launcher3/folder/PreviewBackground.java",
    "import com.android.launcher3.graphics.ShapeDelegate;\n",
    "import com.android.launcher3.graphics.ShapeDelegate;\nimport com.android.launcher3.graphics.OneUiGlassPreferences;\nimport com.android.launcher3.graphics.OneUiGlassSurface;\n",
)
replace(
    "src/com/android/launcher3/folder/PreviewBackground.java",
    '''    public void drawBackground(Canvas canvas) {\n        mPaint.setStyle(Paint.Style.FILL);\n        mPaint.setColor(getBgColor());\n\n        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);\n        drawShadow(canvas);\n    }\n''',
    '''    public void drawBackground(Canvas canvas) {\n        int mode = OneUiGlassPreferences.getFolderBackgroundMode(mContext);\n        int intensity = mode == OneUiGlassPreferences.MODE_CRYSTAL\n                ? OneUiGlassPreferences.getFolderCrystalIntensity(mContext)\n                : OneUiGlassPreferences.getFolderBlurIntensity(mContext);\n        if (mode >= OneUiGlassPreferences.MODE_BLUR && intensity > 0\n                && mInvalidateDelegate != null) {\n            float radius = getScaledRadius();\n            android.graphics.drawable.Drawable glass = OneUiGlassSurface.create(\n                    mInvalidateDelegate, mode == OneUiGlassPreferences.MODE_CRYSTAL,\n                    getBgColor(), radius * 0.32f, intensity);\n            int left = getOffsetX();\n            int top = getOffsetY();\n            glass.setBounds(left, top, left + Math.round(radius * 2f),\n                    top + Math.round(radius * 2f));\n            int save = canvas.save();\n            canvas.clipPath(getClipPath());\n            glass.draw(canvas);\n            canvas.restoreToCount(save);\n        } else if (mode != OneUiGlassPreferences.MODE_OFF) {\n            mPaint.setStyle(Paint.Style.FILL);\n            mPaint.setColor(getBgColor());\n            getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);\n        }\n        drawShadow(canvas);\n    }\n''',
)

print("One UI glass source update applied")
