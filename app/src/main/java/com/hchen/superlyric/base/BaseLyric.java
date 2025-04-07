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
package com.hchen.superlyric.base;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.HashMap;
import java.util.Objects;

public abstract class BaseLyric extends BaseHC {
    public Context mContext;
    public ISuperLyricDistributor mISuperLyricDistributor;

    @Override
    @CallSuper
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

    @Nullable
    public String addExempt() {
        return null;
    }

    public void onTinker() {
        hookMethod("com.tencent.tinker.loader.TinkerLoader",
            "tryLoad", "com.tencent.tinker.loader.app.TinkerApplication",
            new IHook() {
                @Override
                public void after() {
                    Intent intent = (Intent) getResult();
                    Application application = (Application) getArgs(0);
                    int code = intent.getIntExtra("intent_return_code", -2);
                    if (code == 0) {
                        HCInit.setClassLoader(application.getClassLoader());
                    }
                }
            }
        );
    }

    public void openBluetoothA2dp() {
        hookMethod("android.media.AudioManager",
            "isBluetoothA2dpOn",
            returnResult(true)
        );

        hookMethod("android.bluetooth.BluetoothAdapter",
            "isEnabled",
            returnResult(true)
        );
    }

    private String lastLyric;

    public void sendLyric(String lyric) {
        if (lyric == null) return;
        if (mISuperLyricDistributor == null) return;

        try {
            lyric = lyric.trim();
            if (Objects.equals(lyric, lastLyric)) return;
            if (lyric.isEmpty()) return;
            lastLyric = lyric;

            mISuperLyricDistributor.onSuperLyric(new SuperLyricData()
                .setPackageName(mContext.getPackageName())
                .setLyric(lyric)
            );
        } catch (RemoteException e) {
            logE(TAG, "sendLyric: ", e);
        }

        logD(TAG, "Lyric: " + lyric);
    }

    public void sendStop(SuperLyricData data) {
        if (mISuperLyricDistributor == null) return;

        try {
            mISuperLyricDistributor.onStop(data);
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

        try {
            mISuperLyricDistributor.onSuperLyric(
                new SuperLyricData()
                    .setPackageName(mContext.getPackageName())
                    .setMediaMetadata(metadata)
            );
        } catch (RemoteException e) {
            logE(TAG, "sendMediaMetaData: ", e);
        }

        logD(TAG, "MediaMetadata: " + metadata);
    }

    public static class MockFlyme {
        private static final HashMap<String, Integer> flymeFlagMap = new HashMap<>();

        public static void mock() {
            flymeFlagMap.put("FLAG_ALWAYS_SHOW_TICKER", 0x01000000);
            flymeFlagMap.put("FLAG_ONLY_UPDATE_TICKER", 0x02000000);

            setStaticField(Build.class, "BRAND", "meizu");
            setStaticField(Build.class, "MANUFACTURER", "Meizu");
            setStaticField(Build.class, "DEVICE", "m1892");
            setStaticField(Build.class, "DISPLAY", "Flyme");
            setStaticField(Build.class, "PRODUCT", "meizu_16thPlus_CN");
            setStaticField(Build.class, "MODEL", "meizu 16th Plus");

            chain(Class.class, method("getField", String.class)
                .hook(new IHook() {
                    @Override
                    public void after() {
                        String key = (String) getArgs(0);
                        if (flymeFlagMap.get(key) == null) return;
                        setResult(flymeFlagMap.get(key));
                    }
                })

                .method("getDeclaredField", String.class)
                .hook(new IHook() {
                    @Override
                    public void after() {
                        String key = (String) getArgs(0);
                        if (flymeFlagMap.get(key) == null) return;
                        setResult(flymeFlagMap.get(key));
                    }
                })
            );
        }

        public static void notificationLyric(BaseLyric lyric) {
            hookMethod("android.app.NotificationManager",
                "notify",
                String.class, int.class, Notification.class,
                new IHook() {
                    @Override
                    public void after() {
                        Notification notification = (Notification) getArgs(2);
                        if (notification == null) return;

                        boolean isLyric = ((notification.flags & 0x01000000) != 0 || (notification.flags & 0x02000000) != 0);
                        if (isLyric) {
                            if (notification.tickerText != null) {
                                lyric.sendLyric(notification.tickerText.toString());
                            } else {
                                lyric.sendStop(
                                    new SuperLyricData()
                                    .setPackageName(lyric.mContext.getPackageName())
                                );
                            }
                        }
                    }
                }
            );
        }
    }
}
