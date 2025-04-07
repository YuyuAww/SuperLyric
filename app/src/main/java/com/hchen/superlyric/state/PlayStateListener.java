/*
 * This file is part of SuperLyric.

 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HChenX
 */
package com.hchen.superlyric.state;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;

import androidx.annotation.Nullable;

import com.hchen.superlyric.binder.SuperLyricService;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PlayStateListener {
    private final Context mContext;
    private static ISuperLyricDistributor mISuperLyricDistributor;
    private final MediaSessionManager mMediaSessionManager;
    private static final ConcurrentHashMap<MediaController, MediaControllerCallback> mCallbackHashMap = new ConcurrentHashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener mListener = controllers -> {
        if (controllers == null) return;

        mCallbackHashMap.forEach(MediaController::unregisterCallback);
        controllers.forEach(this::registerMediaControllerCallback);
    };

    public PlayStateListener(Context context, ISuperLyricDistributor iSuperLyricDistributor) {
        mContext = context;
        mISuperLyricDistributor = iSuperLyricDistributor;
        mMediaSessionManager = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void start() {
        List<MediaController> list = mMediaSessionManager.getActiveSessions(new ComponentName(mContext, NotificationListenerService.class));
        list.forEach(this::registerMediaControllerCallback);
        mMediaSessionManager.addOnActiveSessionsChangedListener(mListener, new ComponentName(mContext, NotificationListenerService.class));
    }

    private void registerMediaControllerCallback(MediaController controller) {
        if (SuperLyricService.mSelfControlSet.contains(controller.getPackageName()))
            return; // 不监听自我控制的应用

        if (mCallbackHashMap.get(controller) != null) {
            controller.unregisterCallback(mCallbackHashMap.get(controller));
            mCallbackHashMap.remove(controller);
        }

        MediaControllerCallback controllerCallback = new MediaControllerCallback(controller);
        controller.registerCallback(controllerCallback);
        mCallbackHashMap.put(controller, controllerCallback);
    }

    private static class MediaControllerCallback extends MediaController.Callback {
        private final MediaController mController;

        private MediaControllerCallback(MediaController controller) {
            mController = controller;
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            super.onPlaybackStateChanged(state);
            if (state == null) return;
            if (unregisterCallbackIfNeed(mController, this))
                return;

            switch (state.getState()) {
                case PlaybackState.STATE_BUFFERING, PlaybackState.STATE_PAUSED,
                     PlaybackState.STATE_STOPPED -> {
                    if (mISuperLyricDistributor != null) {
                        try {
                            mISuperLyricDistributor.onStop();
                        } catch (RemoteException ignore) {
                        }
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) return;
            if (unregisterCallbackIfNeed(mController, this))
                return;

            if (mISuperLyricDistributor != null) {
                try {
                    SuperLyricData data = new SuperLyricData();
                    data.packageName = mController.getPackageName();
                    data.mediaMetadata = metadata;
                    mISuperLyricDistributor.onSuperLyric(data);
                } catch (RemoteException ignore) {
                }
            }
        }
    }

    private static boolean unregisterCallbackIfNeed(MediaController controller, MediaController.Callback callback) {
        if (SuperLyricService.mSelfControlSet.contains(controller.getPackageName())) {
            if (callback != null) {
                controller.unregisterCallback(callback);
                mCallbackHashMap.remove(controller);
                return true;
            }
        }
        return false;
    }
}
