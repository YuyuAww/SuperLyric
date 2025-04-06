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
package com.hchen.superlyric.hook.base;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.hchen.hooktool.BaseHC;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

public class BaseLyric extends BaseHC {
    public Context mContext;
    public ISuperLyricDistributor mISuperLyricDistributor;

    @Override
    protected void onApplicationAfter(Context context) {
        if (context == null) return;

        this.mContext = context;
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return;
        Bundle bundle = intent.getBundleExtra("super_lyric_info");
        if (bundle == null) return;
        mISuperLyricDistributor = ISuperLyricDistributor.Stub.asInterface(
            bundle.getBinder("super_lyric_binder")
        );

        if (addExempt() != null) {
            try {
                mISuperLyricDistributor.onExempt(addExempt());
            } catch (RemoteException ignore) {
            }
        }
        logD(TAG, "Success get binder: " + mISuperLyricDistributor);
    }

    @Override
    protected void init() {
    }

    @Nullable
    public String addExempt() {
        return null;
    }

    public void sendLyric(String lyric) {
        lyric = lyric.trim();
        if (lyric.isEmpty()) return;
        if (mISuperLyricDistributor == null) return;
        SuperLyricData data = new SuperLyricData();
        data.lyric = lyric;
        data.packageName = mContext.getPackageName();
        data.delay = 0;
        data.base64Icon = "";
        try {
            mISuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE(TAG, "sendLyric: ", e);
        }

        logD(TAG, "Lyric: " + lyric);
    }

    public void sendStop() {
        if (mISuperLyricDistributor == null) return;
        try {
            mISuperLyricDistributor.onStop();
        } catch (RemoteException e) {
            logE(TAG, "sendStop: " + e);
        }

        logD(TAG, "Stop");
    }

    public void sendSuperLyricData(SuperLyricData data) {
        if (mISuperLyricDistributor == null) return;
        try {
            mISuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE(TAG, "sendSuperLyricData: " + e);
        }

        logD(TAG, "SuperLyricData: " + data);
    }

    public void sendMediaMetaData(MediaMetadata metadata) {
        if (metadata == null) return;
        if (mISuperLyricDistributor == null) return;

        SuperLyricData data = new SuperLyricData();
        data.packageName = mContext.getPackageName();
        data.mediaMetadata = metadata;
        try {
            mISuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE(TAG, "sendMediaMetaData: ", e);
        }

        logD(TAG, "MediaMetadata: " + metadata);
    }
}
