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
package com.hchen.superlyric.hook;

import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hchen.hooktool.HCBase;
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
public abstract class BaseLyric extends HCBase {
    private static BaseLyric staticBaseLyric; // 静态实例
    public static AudioManager audioManager;
    private ISuperLyricDistributor iSuperLyricDistributor;
    public long versionCode = -1L;
    private String packageName;

    @Override
    @CallSuper
    protected void onApplication(@NonNull Context context) {
        if (!isEnabled()) return;
        staticBaseLyric = this;
        packageName = context.getPackageName();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        Intent intent = new Intent("Super_Lyric");
        intent.putExtra("super_lyric_add_package", packageName);
        context.sendBroadcast(intent);

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
            versionCode = context.getPackageManager().getPackageInfo(packageName, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            logW(TAG, "Failed to get package: [" + packageName + "] version code!!");
        }

        logD(TAG, "Success get binder: " + iSuperLyricDistributor);
    }

    /**
     * Hook 热更新服务，用于更改当前 classloader
     */
    public void hookTencentTinker() {
        if (!existsClass("com.tencent.tinker.loader.TinkerLoader")) return;

        hookMethod("com.tencent.tinker.loader.TinkerLoader",
            "tryLoad", "com.tencent.tinker.loader.app.TinkerApplication",
            new IHook() {
                @Override
                public void after() {
                    Intent intent = (Intent) getResult();
                    Application application = (Application) getArg(0);
                    int code = intent.getIntExtra("intent_return_code", -2);
                    if (code == 0) {
                        HCInit.setClassLoader(application.getClassLoader());
                    }
                }
            }
        );
    }

    /**
     * 模拟连接蓝牙
     */
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

    /**
     * 获取 MediaMetadataCompat 中的歌词数据
     */
    public void getMediaMetadataCompatLyric() {
        if (existsClass("android.support.v4.media.MediaMetadataCompat$Builder")) {
            hookMethod("android.support.v4.media.MediaMetadataCompat$Builder",
                "putString",
                String.class, String.class,
                new IHook() {
                    @Override
                    public void after() {
                        if (Objects.equals("android.media.metadata.TITLE", getArg(0))) {
                            String lyric = (String) getArg(1);
                            if (lyric == null) return;
                            sendLyric(lyric);
                        }
                    }
                }
            );
        }
    }

    private String lastLyric;

    /**
     * 发送歌词
     * @param lyric 歌词
     */
    public void sendLyric(String lyric) {
        sendLyric(lyric, 0);
    }

    /**
     * 发送歌词和当前歌词的持续时间 (ms)
     * @param lyric 歌词
     * @param delay 歌词持续时间 (ms)
     */
    public void sendLyric(String lyric, int delay) {
        if (lyric == null) return;
        if (iSuperLyricDistributor == null) return;

        try {
            lyric = lyric.trim();
            if (Objects.equals(lyric, lastLyric)) return;
            if (lyric.isEmpty()) return;
            lastLyric = lyric;

            iSuperLyricDistributor.onSuperLyric(new SuperLyricData()
                .setPackageName(packageName)
                .setLyric(lyric)
                .setDelay(delay)
            );
        } catch (RemoteException e) {
            logE(TAG, "sendLyric: ", e);
        }

        logD(TAG, delay != 0 ? "Lyric: " + lyric + ", Delay: " + delay : "Lyric: " + lyric);
    }

    /**
     * 发送播放状态暂停
     */
    public void sendStop() {
        sendStop(packageName);
    }

    /**
     * 发送播放状态暂停
     * @param packageName 暂停播放的音乐软件包名
     */
    public void sendStop(String packageName) {
        sendStop(
            new SuperLyricData()
                .setPackageName(packageName)
        );
    }

    /**
     * 发送播放状态暂停
     * @param data 数据
     */
    public void sendStop(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onStop(data);
        } catch (RemoteException e) {
            logE(TAG, "sendStop: " + e);
        }

        logD(TAG, "Stop");
    }

    /**
     * 发送数据包
     * @param data 数据
     */
    public void sendSuperLyricData(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE(TAG, "sendSuperLyricData: " + e);
        }

        logD(TAG, "SuperLyricData: " + data);
    }

    /**
     * 超时检查，超时自动发送暂停状态
     */
    public static class Timeout {
        private static Timer timer = new Timer();
        private static boolean isRunning = false;

        public static void start() {
            if (isRunning) return;

            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (audioManager != null && !audioManager.isMusicActive()) {
                        staticBaseLyric.sendStop();
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

    /**
     * 模拟为魅族设备，用于开启魅族状态栏功能
     */
    public static class MockFlyme {
        private static class MeiZuNotification extends Notification {
            public static final int FLAG_ALWAYS_SHOW_TICKER_HOOK = 0x01000000;
            public static final int FLAG_ONLY_UPDATE_TICKER_HOOK = 0x02000000;
            public static final String FLAG_ALWAYS_SHOW_TICKER = "FLAG_ALWAYS_SHOW_TICKER";
            public static final String FLAG_ONLY_UPDATE_TICKER = "FLAG_ONLY_UPDATE_TICKER";
        }

        /**
         * 启动模拟
         */
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

        /**
         * 启动模拟
         * @param iHook 自定义内容
         */
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
                        String key = (String) getArg(0);
                        if (Objects.equals(key, MeiZuNotification.FLAG_ALWAYS_SHOW_TICKER)) {
                            param.setResult(MeiZuNotification.class.getDeclaredField("FLAG_ALWAYS_SHOW_TICKER_HOOK"));
                        } else if (Objects.equals(key, MeiZuNotification.FLAG_ONLY_UPDATE_TICKER)) {
                            param.setResult(MeiZuNotification.class.getDeclaredField("FLAG_ONLY_UPDATE_TICKER_HOOK"));
                        }
                    } catch (Throwable ignore) {
                    }
                }
            };
        }

        public static void getFlymeNotificationLyric() {
            if (existsClass("androidx.media3.common.util.Util")) {
                hookMethod("androidx.media3.common.util.Util",
                    "setForegroundServiceNotification",
                    Service.class, int.class, Notification.class, int.class, String.class,
                    new IHook() {
                        @Override
                        public void before() {
                            Notification notification = (Notification) getArg(2);
                            if (notification == null) return;
                            processNotification(notification);
                        }
                    }
                );
            }
            if (existsClass("androidx.core.app.NotificationManagerCompat")) {
                hookMethod("androidx.core.app.NotificationManagerCompat",
                    "notify",
                    String.class, int.class, Notification.class,
                    new IHook() {
                        @Override
                        public void before() {
                            Notification notification = (Notification) getArg(2);
                            if (notification == null) return;
                            processNotification(notification);
                        }
                    }
                );
            }
            if (existsClass("android.app.NotificationManager")) {
                hookMethod("android.app.NotificationManager",
                    "notify",
                    String.class, int.class, Notification.class,
                    new IHook() {
                        @Override
                        public void before() {
                            Notification notification = (Notification) getArg(2);
                            if (notification == null) return;
                            processNotification(notification);
                        }
                    }
                );
            }
        }

        private static void processNotification(Notification notification) {
            boolean isLyric = ((notification.flags & MeiZuNotification.FLAG_ALWAYS_SHOW_TICKER_HOOK) != 0
                || (notification.flags & MeiZuNotification.FLAG_ONLY_UPDATE_TICKER_HOOK) != 0);
            if (!isLyric) return;
            if (notification.tickerText != null) {
                staticBaseLyric.sendLyric(notification.tickerText.toString());
            } else {
                staticBaseLyric.sendStop();
            }
        }
    }

    /**
     * 获取 QQLite 歌词
     */
    public static class QQLite {
        /**
         * 是否支持 QQLite
         */
        public static boolean isQQLite() {
            return existsClass("com.tencent.qqmusic.core.song.SongInfo");
        }

        public static void init() {
            if (!isQQLite()) return;

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
                        String lyric = (String) getArg(1);
                        if (lyric == null || lyric.isEmpty()) return;
                        if (Objects.equals(lyric, "NEED_NOT_UPDATE_TITLE")) return;

                        staticBaseLyric.sendLyric(lyric);
                    }
                }
            );
        }
    }
}
