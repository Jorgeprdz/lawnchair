/*
 * Copyright (C) 2020 The Android Open Source Project
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
 */
package com.android.launcher3;

import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_USER;

import static com.android.launcher3.AbstractFloatingView.TYPE_ICON_SURFACE;
import static com.android.launcher3.Utilities.ATLEAST_Q;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.views.ActivityContext;

import java.lang.ref.WeakReference;

/**
 * Class to encapsulate the handshake protocol between Launcher and gestureNav.
 */
public class GestureNavContract {

    private static final String TAG = "GestureNavContract";

    public static final String EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1";
    public static final String EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position";
    public static final String EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control";
    public static final String EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK";
    public static final String EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback";
    public static final String EXTRA_ENABLE_GESTURE_CONTRACT = "gesture_nav_contract_enable";

    public final ComponentName componentName;
    public final UserHandle user;

    private final Message mCallback;

    public GestureNavContract(ComponentName componentName, UserHandle user, Message callback) {
        this.componentName = componentName;
        this.user = user;
        this.mCallback = callback;
    }

    /** Invalidate the previous gesture's completion before this surface starts laying out. */
    public void begin(ActivityContext context) {
        if (sMessageReceiver == null) {
            sMessageReceiver = new StaticMessageReceiver();
        }
        sMessageReceiver.setCurrentContext(context, this);
    }

    /**
     * Sends valid position information; returns false if the receiver is unavailable.
     */
    public boolean sendEndPosition(RectF position, ActivityContext context,
            @Nullable SurfaceControl surfaceControl) {
        if (position.isEmpty() || !Float.isFinite(position.left) || !Float.isFinite(position.top)
                || !Float.isFinite(position.right) || !Float.isFinite(position.bottom)) {
            return false;
        }
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_ICON_POSITION, position);
        if (ATLEAST_Q) {
            result.putParcelable(EXTRA_ICON_SURFACE, surfaceControl);
        }
        if (sMessageReceiver == null) {
            sMessageReceiver = new StaticMessageReceiver();
        }
        result.putParcelable(EXTRA_ON_FINISH_CALLBACK, sMessageReceiver.setCurrentContext(context, this));

        Message callback = Message.obtain();
        callback.copyFrom(mCallback);
        callback.setData(result);

        try {
            callback.replyTo.send(callback);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending icon position", e);
            return false;
        }
    }

    /**
     * Returns if a {@link GestureNavContract} can be built from the given intent without clearing
     * the contract.
     */
    public static boolean canBuildFromIntent(@NonNull Intent intent) {
        return fromIntent(intent, /* clearGnc= */ false) != null;
    }

    public static boolean isContractEnabled(@NonNull Intent intent) {
        Bundle extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT);

        return extras != null && extras.getBoolean(EXTRA_ENABLE_GESTURE_CONTRACT, true);
    }

    /**
     * Clears and returns the {@link GestureNavContract} if it was present in the intent.
     */
    public static GestureNavContract fromIntent(@NonNull Intent intent) {
        return fromIntent(intent, /* clearGnc= */ true);
    }

    private static GestureNavContract fromIntent(@NonNull Intent intent, boolean clearGnc) {
        Bundle extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT);
        if (extras == null) {
            return null;
        }
        if (clearGnc) {
            intent.removeExtra(EXTRA_GESTURE_CONTRACT);
        }

        ComponentName componentName = extras.getParcelable(EXTRA_COMPONENT_NAME);
        UserHandle userHandle = extras.getParcelable(EXTRA_USER);
        Message callback = extras.getParcelable(EXTRA_REMOTE_CALLBACK);

        if (componentName != null && userHandle != null && callback != null
                && callback.replyTo != null) {
            return new GestureNavContract(componentName, userHandle, callback);
        }
        return null;
    }

    /**
     * Message used for receiving gesture nav contract information. We use a static messenger to
     * avoid leaking too make binders in case the receiving launcher does not handle the contract
     * properly.
     */
    private static StaticMessageReceiver sMessageReceiver = null;

    private static class StaticMessageReceiver implements Handler.Callback {

        private static final int MSG_CLOSE_LAST_TARGET = 0;

        private final Messenger mMessenger =
                new Messenger(new Handler(Looper.getMainLooper(), this));

        private WeakReference<ActivityContext> mLastTarget = new WeakReference<>(null);

        private WeakReference<GestureNavContract> mLastContract = new WeakReference<>(null);
        private int mGeneration;

        public Message setCurrentContext(ActivityContext context, GestureNavContract contract) {
            if (mLastContract.get() != contract) {
                mGeneration++;
                mLastContract = new WeakReference<>(contract);
            }
            mLastTarget = new WeakReference<>(context);

            Message msg = Message.obtain();
            msg.replyTo = mMessenger;
            msg.what = MSG_CLOSE_LAST_TARGET;
            msg.arg1 = mGeneration;
            return msg;
        }

        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (message.what == MSG_CLOSE_LAST_TARGET) {
                // A late completion from a previous gesture must not close the new icon surface.
                if (message.arg1 != mGeneration) return true;
                ActivityContext lastContext = mLastTarget.get();
                if (lastContext != null) {
                    AbstractFloatingView.closeOpenViews(lastContext, false, TYPE_ICON_SURFACE);
                }
                return true;
            }
            return false;
        }
    }
}
