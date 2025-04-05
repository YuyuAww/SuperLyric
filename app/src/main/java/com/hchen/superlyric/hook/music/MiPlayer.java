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
package com.hchen.superlyric.hook.music;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;

import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.Objects;

public class MiPlayer extends BaseHC {
    private Context mContext;
    private ISuperLyricDistributor mISuperLyricDistributor;

    @Override
    protected void onApplicationAfter(Context context) {
        this.mContext = context;
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return;
        Bundle bundle = intent.getBundleExtra("super_lyric_info");
        if (bundle == null) return;
        mISuperLyricDistributor = ISuperLyricDistributor.Stub.asInterface(
            bundle.getBinder("super_lyric_binder")
        );
        logD(TAG, "Success get binder: " + mISuperLyricDistributor);
    }

    @Override
    protected void init() {
        hookAllMethod("com.tencent.qqmusiccommon.util.music.RemoteLyricController",
            "BluetoothA2DPConnected",
            returnResult(true)
        );

        hookAllMethod("com.tencent.qqmusiccommon.util.music.RemoteControlManager",
            "updataMetaData",
            new IHook() {
                @Override
                public void before() {
                    if (getArgs(1) == null) return;
                    String lyric = (String) getArgs(1);
                    if (Objects.equals("NEED_NOT_UPDATE_TITLE", lyric)) return;
                    sendLyric(lyric);
                }
            }
        );
    }

    private void sendLyric(String lyric) {
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
            logE(TAG, e);
        }

        logD(TAG, "Lyric: " + lyric);
    }
}
