/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications copyright 2025, Lawnchair
 */

package com.android.launcher3.qsb;

import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER;

import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.BuildConfigs;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.graphics.FragmentWithPreview;
import com.android.launcher3.widget.util.WidgetSizes;

/**
 * A frame layout which contains a QSB. This internally uses fragment to bind the view, which
 * allows it to contain the logic for {@link Fragment#startActivityForResult(Intent, int)}.
 *
 * Note: WidgetManagerHelper can be disabled using FeatureFlags. In QSB, we should use
 * AppWidgetManager directly, so that it keeps working in that case.
 */
public class QsbContainerView extends FrameLayout {

    public static final String SEARCH_ENGINE_SETTINGS_KEY = "selected_search_engine";

    /**
     * Returns the package name for user configured search provider or from searchManager
     * @param context
     * @return String
     */
    @WorkerThread
    @Nullable
    public static String getSearchWidgetPackageName(@NonNull Context context) {
        String providerPkg = Settings.Secure.getString(context.getContentResolver(),
                SEARCH_ENGINE_SETTINGS_KEY);
        if (providerPkg == null) {
            SearchManager searchManager = context.getSystemService(SearchManager.class);
            ComponentName componentName = searchManager.getGlobalSearchActivity();
            if (componentName != null) {
                providerPkg = searchManager.getGlobalSearchActivity().getPackageName();
            }
        }
        return providerPkg;
    }

    /**
     * returns it's AppWidgetProviderInfo using package name from getSearchWidgetPackageName
     * @param context
     * @return AppWidgetProviderInfo
     */
    @WorkerThread
    @Nullable
    public static AppWidgetProviderInfo getSearchWidgetProviderInfo(@NonNull Context context) {
        return getSearchWidgetProviderInfo(context, getSearchWidgetPackageName(context));
    }

    public static AppWidgetProviderInfo getSearchWidgetProviderInfo(@NonNull Context context, String providerPkg) {
        if (providerPkg == null) {
            return null;
        }

        AppWidgetProviderInfo defaultWidgetForSearchPackage = null;
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (AppWidgetProviderInfo info :
                appWidgetManager.getInstalledProvidersForPackage(providerPkg, null)) {
            if (info.provider.getPackageName().equals(providerPkg) && info.configure == null) {
                if ((info.widgetCategory
                        & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                    return info;
                } else if (defaultWidgetForSearchPackage == null) {
                    defaultWidgetForSearchPackage = info;
                }
            }
        }
        return defaultWidgetForSearchPackage;
    }

    /**
     * returns componentName for searchWidget if package name is known.
     */
    @WorkerThread
    @Nullable
    public static ComponentName getSearchComponentName(@NonNull  Context context) {
        AppWidgetProviderInfo providerInfo =
                QsbContainerView.getSearchWidgetProviderInfo(context);
        if (providerInfo != null) {
            return providerInfo.provider;
        } else {
            String pkgName = QsbContainerView.getSearchWidgetPackageName(context);
            if (pkgName != null) {
                //we don't know the class name yet. we'll put the package name as placeholder
                return new ComponentName(pkgName, pkgName);
            }
            return null;
        }
    }

    public QsbContainerView(Context context) {
        super(context);
    }

    public QsbContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QsbContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    protected void setPaddingUnchecked(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    /**
     * A fragment to display the QSB.
     */
    public static class QsbFragment extends FragmentWithPreview {

        public static final int QSB_WIDGET_HOST_ID = 1026;
        private static final int REQUEST_BIND_QSB = 1;
        private static final int REQUEST_CONFIGURE_QSB = 2;

        protected String mKeyWidgetId = "qsb_widget_id";
        private QsbWidgetHost mQsbWidgetHost;
        protected AppWidgetProviderInfo mWidgetInfo;
        private QsbWidgetHostView mQsb;

        // We need to store the orientation here, due to a bug (b/64916689) that results in widgets
        // being inflated in the wrong orientation.
        private int mOrientation;

        @Override
        public void onInit(Bundle savedInstanceState) {
            mQsbWidgetHost = createHost();
            mOrientation = getContext().getResources().getConfiguration().orientation;
        }

        protected QsbWidgetHost createHost() {
            return new QsbWidgetHost(getContext(), QSB_WIDGET_HOST_ID,
                    (c) -> new QsbWidgetHostView(c), this::rebindFragment);
        }

        private FrameLayout mWrapper;

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            mWrapper = createWrapper(getContext());
            // Only add the view when enabled
            if (isQsbEnabled()) {
                mWrapper.addView(createQsb(mWrapper));
            }
            return mWrapper;
        }

        @NonNull
        protected FrameLayout createWrapper(@NonNull Context context) {
            return new FrameLayout(context);
        }

        private View createQsb(ViewGroup container) {
            mQsb = null;
            try {
                return createQsbInternal(container);
            } catch (RuntimeException unavailable) {
                android.util.Log.w("QsbContainerView", "Search widget unavailable", unavailable);
                return getDefaultView(container, false);
            }
        }

        private View createQsbInternal(ViewGroup container) {
            if (isInPreviewMode()) return getDefaultView(container, false);
            mWidgetInfo = getSearchWidgetProvider();
            if (mWidgetInfo == null) {
                // Provider removal cleans only this QSB's IDs, never another widget host.
                if (!isInPreviewMode()) clearWidgetIds();
                return getDefaultView(container, false /* show setup icon */);
            }
            if (getPendingWidgetId() >= 0) return getDefaultView(container, true);
            Bundle opts = createBindOptions();
            Context context = getContext();
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);

            int widgetId = LauncherPrefs.getPrefs(context).getInt(mKeyWidgetId, -1);
            AppWidgetProviderInfo widgetInfo = widgetManager.getAppWidgetInfo(widgetId);
            boolean isWidgetBound = (widgetInfo != null) &&
                    widgetInfo.provider.equals(mWidgetInfo.provider);

            int oldWidgetId = widgetId;
            if (!isWidgetBound && !isInPreviewMode()) {
                if (widgetId > -1) {
                    mQsbWidgetHost.deleteAppWidgetId(widgetId);
                    saveWidgetId(-1);
                }

                widgetId = mQsbWidgetHost.allocateAppWidgetId();
                try {
                    isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(
                            widgetId, mWidgetInfo.getProfile(), mWidgetInfo.provider, opts);
                } finally {
                    if (!isWidgetBound) {
                        mQsbWidgetHost.deleteAppWidgetId(widgetId);
                        widgetId = -1;
                    }
                }

                if (oldWidgetId != widgetId) {
                    saveWidgetId(widgetId);
                }
            }

            if (isWidgetBound) {
                if (!isInPreviewMode() && needsConfiguration(widgetId)) {
                    return getDefaultView(container, true);
                }
                mQsb = (QsbWidgetHostView) mQsbWidgetHost.createView(context, widgetId,
                        mWidgetInfo);
                mQsb.setId(R.id.qsb_widget);
                onWidgetCreated(mQsb);

                if (!isInPreviewMode()) {
                    if (!containsAll(AppWidgetManager.getInstance(context)
                            .getAppWidgetOptions(widgetId), opts)) {
                        mQsb.updateAppWidgetOptions(opts);
                    }
                }
                return mQsb;
            }

            // Return a default widget with setup icon.
            return getDefaultView(container, true /* show setup icon */);
        }

        private void saveWidgetId(int widgetId) {
            LauncherPrefs.getPrefs(getContext()).edit().putInt(mKeyWidgetId, widgetId).apply();
        }

        private int getPendingWidgetId() {
            return LauncherPrefs.getPrefs(getContext()).getInt(mKeyWidgetId + "_pending", -1);
        }

        private void savePendingWidgetId(int id) {
            LauncherPrefs.getPrefs(getContext()).edit().putInt(mKeyWidgetId + "_pending", id).apply();
        }

        private boolean needsConfiguration(int id) {
            return mWidgetInfo.configure != null
                    && LauncherPrefs.getPrefs(getContext()).getInt(mKeyWidgetId + "_configured", -1) != id;
        }

        private void clearWidgetIds() {
            int bound = LauncherPrefs.getPrefs(getContext()).getInt(mKeyWidgetId, -1);
            int pending = getPendingWidgetId();
            if (bound >= 0) mQsbWidgetHost.deleteAppWidgetId(bound);
            if (pending >= 0 && pending != bound) mQsbWidgetHost.deleteAppWidgetId(pending);
            saveWidgetId(-1);
            savePendingWidgetId(-1);
            LauncherPrefs.getPrefs(getContext()).edit().remove(mKeyWidgetId + "_configured").apply();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            try {
                handleWidgetResult(requestCode, resultCode, data);
            } catch (RuntimeException unavailable) {
                // Retain tracked IDs for cleanup/retry if the widget service is temporarily down.
                android.util.Log.w("QsbContainerView", "Search widget result unavailable", unavailable);
                rebindFragment();
            }
        }

        private void handleWidgetResult(int requestCode, int resultCode, Intent data) {
            if (requestCode != REQUEST_BIND_QSB && requestCode != REQUEST_CONFIGURE_QSB) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            // Some providers return RESULT_OK without an Intent. The allocated ID is authoritative.
            int id = getPendingWidgetId();
            if (id < 0) return;
            AppWidgetProviderInfo bound = id < 0 ? null
                    : AppWidgetManager.getInstance(getContext()).getAppWidgetInfo(id);
            mWidgetInfo = getSearchWidgetProvider();
            if (resultCode == Activity.RESULT_OK && bound != null && mWidgetInfo != null
                    && bound.provider.equals(mWidgetInfo.provider)) {
                saveWidgetId(id);
                if (requestCode == REQUEST_BIND_QSB && needsConfiguration(id)) {
                    requestConfiguration(id);
                    return;
                }
                LauncherPrefs.getPrefs(getContext()).edit()
                        .putInt(mKeyWidgetId + "_configured", id).apply();
                savePendingWidgetId(-1);
            } else {
                clearWidgetIds();
            }
            rebindFragment();
        }

        private void requestConfiguration(int id) {
            savePendingWidgetId(id);
            try {
                startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(mWidgetInfo.configure).putExtra(EXTRA_APPWIDGET_ID, id),
                        REQUEST_CONFIGURE_QSB);
            } catch (RuntimeException unavailable) {
                clearWidgetIds();
                rebindFragment();
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            if (!isInPreviewMode() && isQsbEnabled()) {
                try {
                    mQsbWidgetHost.startListening();
                } catch (RuntimeException unavailable) {
                    android.util.Log.w("QsbContainerView", "Unable to listen to search widget", unavailable);
                    rebindFragment();
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            mOrientation = getContext().getResources().getConfiguration().orientation;
            if (mQsb != null && mQsb.isReinflateRequired(mOrientation)) {
                rebindFragment();
            }
        }

        @Override
        public void onStop() {
            try {
                if (!isInPreviewMode()) mQsbWidgetHost.stopListening();
            } finally {
                super.onStop();
            }
        }

        private void rebindFragment() {
            // Exit if the embedded qsb is disabled
            if (!isQsbEnabled()) {
                return;
            }

            if (mWrapper != null && getContext() != null) {
                mWrapper.removeAllViews();
                mWrapper.addView(createQsb(mWrapper));
            }
        }

        public boolean isQsbEnabled() {
            return BuildConfigs.QSB_ON_FIRST_SCREEN;
        }

        protected void onWidgetCreated(QsbWidgetHostView host) { }

        protected Bundle createBindOptions() {
            InvariantDeviceProfile idp = LauncherAppState.getIDP(getContext());
            return WidgetSizes.getWidgetSizeOptions(getContext(), mWidgetInfo.provider,
                    idp.numColumns, 1);
        }

        protected View getDefaultView(ViewGroup container, boolean showSetupIcon) {
            // Return a default widget with setup icon.
            View v = QsbWidgetHostView.getDefaultView(container);
            // pE-TODO(??): Why are we using isInPreviewMode() check to prevent crash?
            if (showSetupIcon && !isInPreviewMode()) {
                View setupButton = v.findViewById(R.id.btn_qsb_setup);
                setupButton.setVisibility(View.VISIBLE);
                setupButton.setOnClickListener((v2) -> requestQsbCreate());
            }
            return v;
        }

        protected void requestQsbCreate() {
            if (isInPreviewMode() || getContext() == null) return;
            try {
                mWidgetInfo = getSearchWidgetProvider();
                if (mWidgetInfo == null) return;
                int bound = LauncherPrefs.getPrefs(getContext()).getInt(mKeyWidgetId, -1);
                if (bound >= 0 && needsConfiguration(bound)) {
                    requestConfiguration(bound);
                    return;
                }
                int previous = getPendingWidgetId();
                if (previous >= 0) mQsbWidgetHost.deleteAppWidgetId(previous);
                int id = mQsbWidgetHost.allocateAppWidgetId();
                savePendingWidgetId(id);
                startActivityForResult(new Intent(ACTION_APPWIDGET_BIND)
                        .putExtra(EXTRA_APPWIDGET_ID, id)
                        .putExtra(EXTRA_APPWIDGET_PROVIDER, mWidgetInfo.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, mWidgetInfo.getProfile())
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, createBindOptions()),
                        REQUEST_BIND_QSB);
            } catch (RuntimeException unavailable) {
                clearWidgetIds();
                rebindFragment();
            }
        }


        /**
         * Returns a widget with category {@link AppWidgetProviderInfo#WIDGET_CATEGORY_SEARCHBOX}
         * provided by the package from getSearchProviderPackageName
         * If widgetCategory is not supported, or no such widget is found, returns the first widget
         * provided by the package.
         */
        @WorkerThread
        protected AppWidgetProviderInfo getSearchWidgetProvider() {
            return getSearchWidgetProviderInfo(getContext());
        }
    }

    /** The QSB host has its own restored ID map, separate from workspace widget rows. */
    public static void restoreWidgetIds(Context context, int[] oldIds, int[] newIds) {
        if (oldIds == null || newIds == null || oldIds.length != newIds.length) return;
        var prefs = LauncherPrefs.getPrefs(context);
        var editor = prefs.edit();
        for (String key : new String[]{"qsb_widget_id", "pixel_search_widget_id"}) {
            for (String suffix : new String[]{"", "_pending", "_configured"}) {
                int old = prefs.getInt(key + suffix, -1);
                if (old < 0) continue;
                for (int index = 0; index < oldIds.length; index++) {
                    if (old == oldIds[index] && newIds[index] >= 0) {
                        editor.putInt(key + suffix, newIds[index]);
                        break;
                    }
                }
            }
        }
        editor.apply();
    }

    public static class QsbWidgetHost extends AppWidgetHost {

        private final WidgetViewFactory mViewFactory;
        private final WidgetProvidersUpdateCallback mWidgetsUpdateCallback;

        public QsbWidgetHost(Context context, int hostId, WidgetViewFactory viewFactory,
                WidgetProvidersUpdateCallback widgetProvidersUpdateCallback) {
            super(context, hostId);
            mViewFactory = viewFactory;
            mWidgetsUpdateCallback = widgetProvidersUpdateCallback;
        }

        public QsbWidgetHost(Context context, int hostId, WidgetViewFactory viewFactory) {
            this(context, hostId, viewFactory, null);
        }

        @Override
        protected AppWidgetHostView onCreateView(
                Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
            return mViewFactory.newView(context);
        }

        @Override
        protected void onProvidersChanged() {
            super.onProvidersChanged();
            if (mWidgetsUpdateCallback != null) {
                mWidgetsUpdateCallback.onProvidersUpdated();
            }
        }
    }

    public interface WidgetViewFactory {

        QsbWidgetHostView newView(Context context);
    }

    /**
     * Callback interface for packages list update.
     */
    @FunctionalInterface
    public interface WidgetProvidersUpdateCallback {
        /**
         * Gets called when widget providers list changes
         */
        void onProvidersUpdated();
    }

    /**
     * Returns true if {@param original} contains all entries defined in {@param updates} and
     * have the same value.
     * The comparison uses {@link Object#equals(Object)} to compare the values.
     */
    private static boolean containsAll(Bundle original, Bundle updates) {
        for (String key : updates.keySet()) {
            Object value1 = updates.get(key);
            Object value2 = original.get(key);
            if (value1 == null) {
                if (value2 != null) {
                    return false;
                }
            } else if (!value1.equals(value2)) {
                return false;
            }
        }
        return true;
    }

}
