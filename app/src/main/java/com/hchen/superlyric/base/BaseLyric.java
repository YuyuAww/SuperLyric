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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Super Lyric 基类
 *
 * @author 焕晨HChen
 */
public abstract class BaseLyric extends BaseHC {
    private ISuperLyricDistributor iSuperLyricDistributor;
    public static AudioManager audioManager;
    public long versionCode = -1L;
    public Context context;

    @Override
    @CallSuper
    protected void onApplicationAfter(Context context) {
        if (!enabled()) return;

        if (context == null) {
            logW(TAG, "Failed to get context!!");
            return;
        }

        this.context = context;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        Intent intent = new Intent("Super_Lyric");
        intent.putExtra("super_lyric_add_package", this.context.getPackageName());
        this.context.sendBroadcast(intent);

        Intent intentBinder = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intentBinder == null) {
            logW(TAG, "Failed to get intent!!");
            return;
        }

        Bundle bundle = intentBinder.getBundleExtra("super_lyric_info");
        if (bundle == null) {
            logW(TAG, "Failed to get bundle!!");
            return;
        }

        iSuperLyricDistributor = ISuperLyricDistributor.Stub.asInterface(
            bundle.getBinder("super_lyric_binder")
        );

        try {
            versionCode = this.context.getPackageManager().getPackageInfo(this.context.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            logW(TAG, "Failed to get package: [" + this.context.getPackageName() + "] version code!!");
        }

        logD(TAG, "Success get binder: " + iSuperLyricDistributor);
    }

    public void onTinker() {
        if (!existsClass("com.tencent.tinker.loader.TinkerLoader")) return;

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

    public void mediaMetadataCompatLyric() {
        if (existsClass("android.support.v4.media.MediaMetadataCompat$Builder")) {
            hookMethod("android.support.v4.media.MediaMetadataCompat$Builder",
                "putString",
                String.class, String.class,
                new IHook() {
                    @Override
                    public void after() {
                        if (Objects.equals("android.media.metadata.TITLE", getArgs(0))) {
                            String lyric = (String) getArgs(1);
                            if (lyric == null) return;
                            sendLyric(lyric);
                        }
                    }
                }
            );
        }
    }

    private String lastLyric;

    public void sendLyric(String lyric) {
        sendLyric(lyric, 0);
    }

    public void sendLyric(String lyric, Integer delay) {
        if (lyric == null) return;
        if (iSuperLyricDistributor == null) return;

        try {
            lyric = lyric.trim();
            if (Objects.equals(lyric, lastLyric)) return;
            if (lyric.isEmpty()) return;
            lastLyric = lyric;

            iSuperLyricDistributor.onSuperLyric(new SuperLyricData()
                .setPackageName(context.getPackageName())
                .setLyric(lyric)
                .setDelay(delay)
            );
        } catch (RemoteException e) {
            logE(TAG, "sendLyric: ", e);
        }

        logD(TAG, delay != 0 ? "Lyric: " + lyric + ", Delay: " + delay : "Lyric: " + lyric);
    }

    public void sendStop(SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onStop(data);
        } catch (RemoteException e) {
            logE(TAG, "sendStop: " + e);
        }

        logD(TAG, "Stop");
    }

    public void sendSuperLyricData(SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE(TAG, "sendSuperLyricData: " + e);
        }

        logD(TAG, "SuperLyricData: " + data);
    }

    public void sendMediaMetaData(MediaMetadata metadata) {
        if (metadata == null) return;
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onSuperLyric(
                new SuperLyricData()
                    .setPackageName(context.getPackageName())
                    .setMediaMetadata(metadata)
            );
        } catch (RemoteException e) {
            logE(TAG, "sendMediaMetaData: ", e);
        }

        logD(TAG, "MediaMetadata: " + metadata);
    }

    public static class Timeout {
        private static Timer timer = new Timer();
        private static boolean isRunning = false;

        public static void start(BaseLyric lyric) {
            if (isRunning) return;

            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (audioManager != null && !audioManager.isMusicActive()) {
                        lyric.sendStop(
                            new SuperLyricData()
                                .setPackageName(lyric.context.getPackageName())
                        );
                        stop();
                    }
                }
            }, 0, 1000);
            isRunning = true;
        }

        public static void stop() {
            if (timer == null || !isRunning) return;

            timer.cancel();
            timer = null;
            isRunning = false;
        }
    }

    public static class MockFlyme {
        private static class MeiZuNotification extends Notification {
            public static final int FLAG_ALWAYS_SHOW_TICKER_HOOK = 0x01000000;
            public static final int FLAG_ONLY_UPDATE_TICKER_HOOK = 0x02000000;
            public static final String FLAG_ALWAYS_SHOW_TICKER = "FLAG_ALWAYS_SHOW_TICKER";
            public static final String FLAG_ONLY_UPDATE_TICKER = "FLAG_ONLY_UPDATE_TICKER";
        }

        public static void mock() {
            hookMethod("java.lang.Class", "getField", String.class, createHook());
            hookMethod("java.lang.Class", "getDeclaredField", String.class, createHook());

            hookMethod("android.os.SystemProperties",
                "get",
                String.class, String.class,
                new IHook() {
                    @Override
                    public void after() {
                        setStaticField(Build.class, "BRAND", "meizu");
                        setStaticField(Build.class, "MANUFACTURER", "Meizu");
                        setStaticField(Build.class, "DEVICE", "m1892");
                        setStaticField(Build.class, "DISPLAY", "Flyme");
                        setStaticField(Build.class, "PRODUCT", "meizu_16thPlus_CN");
                        setStaticField(Build.class, "MODEL", "meizu 16th Plus");
                    }
                }
            );
        }

        public static void mock(@NonNull IHook iHook) {
            hookMethod("java.lang.Class", "getField", String.class, createHook());
            hookMethod("java.lang.Class", "getDeclaredField", String.class, createHook());

            hookMethod("android.os.SystemProperties",
                "get",
                String.class, String.class,
                iHook
            );
        }

        private static IHook createHook() {
            return new IHook() {
                @Override
                public void before() {
                    try {
                        String key = (String) getArgs(0);
                        if (Objects.equals(key, "FLAG_ALWAYS_SHOW_TICKER")) {
                            param.setResult(MeiZuNotification.class.getDeclaredField("FLAG_ALWAYS_SHOW_TICKER_HOOK"));
                        } else if (Objects.equals(key, "FLAG_ONLY_UPDATE_TICKER")) {
                            param.setResult(MeiZuNotification.class.getDeclaredField("FLAG_ONLY_UPDATE_TICKER_HOOK"));
                        }
                    } catch (Throwable e) {
                        logE(TAG, e);
                    }
                }
            };
        }

        public static void notificationLyric(BaseLyric baseLyric) {
            if (existsClass("android.app.NotificationManager")) {
                hookMethod("android.app.NotificationManager",
                    "notify",
                    String.class, int.class, Notification.class,
                    new IHook() {
                        @Override
                        public void after() {
                            Notification notification = (Notification) getArgs(2);
                            if (notification == null) return;
                            processNotification(baseLyric, notification);
                        }
                    }
                );
            }
            if (existsClass("androidx.media3.common.util.Util")) {
                hookMethod("androidx.media3.common.util.Util",
                    "setForegroundServiceNotification",
                    Service.class, int.class, Notification.class, int.class, String.class,
                    new IHook() {
                        @Override
                        public void before() {
                            Notification notification = (Notification) getArgs(2);
                            if (notification == null) return;
                            processNotification(baseLyric, notification);
                        }
                    }
                );
            }
        }

        private static void processNotification(BaseLyric baseLyric, Notification notification) {
            boolean isLyric = ((notification.flags & 0x01000000) != 0 || (notification.flags & 0x02000000) != 0);
            if (!isLyric) return;
            if (notification.tickerText != null) {
                baseLyric.sendLyric(notification.tickerText.toString());
            } else {
                baseLyric.sendStop(
                    new SuperLyricData().
                        setPackageName(baseLyric.context.getPackageName())
                );
            }
        }
    }

    public static class QQLite {
        public static boolean isQQLite() {
            return existsClass("com.tencent.qqmusic.core.song.SongInfo");
        }

        public static void init(BaseLyric baseLyric) {
            hookMethod("com.tencent.qqmusiccommon.util.music.RemoteLyricController",
                "BluetoothA2DPConnected",
                returnResult(true)
            );

            hookMethod("com.tencent.qqmusiccommon.util.music.RemoteControlManager",
                "updataMetaData",
                "com.tencent.qqmusic.core.song.SongInfo", String.class,
                new IHook() {
                    @Override
                    public void before() {
                        String lyric = (String) getArgs(1);
                        if (lyric == null || lyric.isEmpty()) return;
                        if (Objects.equals(lyric, "NEED_NOT_UPDATE_TITLE")) return;

                        baseLyric.sendLyric(lyric);
                    }
                }
            );
        }
    }
}
