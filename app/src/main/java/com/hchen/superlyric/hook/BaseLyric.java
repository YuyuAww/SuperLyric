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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.HCData;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.helper.MeiZuNotification;
import com.hchen.superlyric.utils.DexKitUtils;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import dalvik.system.PathClassLoader;

/**
 * Super Lyric 基类
 *
 * @author 焕晨HChen
 */
public abstract class BaseLyric extends HCBase {
    private static BaseLyric staticBaseLyric; // 静态实例
    private static ISuperLyricDistributor iSuperLyricDistributor;
    public static AudioManager audioManager;
    public static String packageName;
    public static long versionCode = -1L;
    public static String versionName = "unknown";

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
        Objects.requireNonNull(intentBinder, "Failed to get designated binder intent, can't use SuperLyric!!");

        Bundle bundle = intentBinder.getBundleExtra("super_lyric_info");
        Objects.requireNonNull(bundle, "Failed to get designated binder bundle, please try reboot system!!");

        iSuperLyricDistributor = ISuperLyricDistributor.Stub.asInterface(bundle.getBinder("super_lyric_binder"));

        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = packageInfo.versionName;
            versionCode = packageInfo.getLongVersionCode();
            logI(TAG, "App packageName: " + packageName + ", versionName: " + versionName + ", versionCode: " + versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            logW(TAG, "Failed to get package: [" + packageName + "] version code!!");
        }

        logD(TAG, "Success get binder: " + iSuperLyricDistributor);
    }

    /**
     * Hook 热更新服务，用于更改当前 classloader
     */
    public void hookTencentTinker() {
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
                    observeCall();
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

    private String lastLyric;

    /**
     * 发送歌词
     *
     * @param lyric 歌词
     */
    public void sendLyric(String lyric) {
        sendLyric(lyric, 0);
    }

    /**
     * 发送歌词和当前歌词的持续时间 (ms)
     *
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
     *
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
     *
     * @param data 数据
     */
    public void sendStop(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onStop(data);
        } catch (RemoteException e) {
            logE(TAG, "sendStop: " + e);
        }

        logD(TAG, "Stop: " + data);
    }

    /**
     * 发送数据包
     *
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
    public static class MeizuHelper {
        private static Class<?> meizu;

        /**
         * 模拟魅族
         */
        public static void mockDevice() {
            setStaticField("android.os.Build", "BRAND", "meizu");
            setStaticField("android.os.Build", "MANUFACTURER", "Meizu");
            setStaticField("android.os.Build", "DEVICE", "m1892");
            setStaticField("android.os.Build", "DISPLAY", "Flyme");
            setStaticField("android.os.Build", "PRODUCT", "meizu_16thPlus_CN");
            setStaticField("android.os.Build", "MODEL", "meizu 16th Plus");

            try {
                meizu = findClass("com.hchen.superlyric.helper.MeiZuNotification", classLoader);
            } catch (Throwable ignore) {
                meizu = findClass("com.hchen.superlyric.helper.MeiZuNotification", new PathClassLoader(HCData.getModulePath(), classLoader));
            }
            hookMethod(Class.class, "forName", String.class,
                new IHook() {
                    @Override
                    public void before() {
                        try {
                            if ("android.app.Notification".equals(getArg(0))) {
                                setResult(meizu);
                                return;
                            }
                            Class<?> clazz = (Class<?>) callThisStaticMethod("forName", getArg(0), true, classLoader);
                            setResult(clazz);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            );
        }

        public static void getFlymeNotificationLyric() {
            if (existsClass("androidx.media3.common.util.Util")) {
                hookMethod("androidx.media3.common.util.Util",
                    "setForegroundServiceNotification",
                    Service.class, int.class, Notification.class, int.class, String.class,
                    createNotificationHook()
                );
            } else if (existsClass("androidx.core.app.NotificationManagerCompat")) {
                hookMethod("androidx.core.app.NotificationManagerCompat",
                    "notify",
                    String.class, int.class, Notification.class,
                    createNotificationHook()
                );
            } else if (existsClass("android.app.NotificationManager")) {
                hookMethod("android.app.NotificationManager",
                    "notify",
                    String.class, int.class, Notification.class,
                    createNotificationHook()
                );
            }
        }

        private static IHook createNotificationHook() {
            return new IHook() {
                @Override
                public void before() {
                    Notification notification = (Notification) getArg(2);
                    if (notification == null) return;

                    boolean isLyric = ((notification.flags & MeiZuNotification.FLAG_ALWAYS_SHOW_TICKER) != 0
                        || (notification.flags & MeiZuNotification.FLAG_ONLY_UPDATE_TICKER) != 0);
                    if (isLyric) {
                        if (notification.tickerText != null) {
                            staticBaseLyric.sendLyric(notification.tickerText.toString());
                        } else {
                            staticBaseLyric.sendStop();
                        }
                    }
                }
            };
        }
    }

    /**
     * 模拟为 OPPO 设备
     */
    public static class OPPOHelper {
        public static void mockDevice() {
            hookMethod("android.os.SystemProperties",
                "get",
                String.class, String.class,
                new IHook() {
                    @Override
                    public void after() {
                        setStaticField(Build.class, "BRAND", "oppo");
                        setStaticField(Build.class, "MANUFACTURER", "Oppo");
                        setStaticField(Build.class, "DISPLAY", "Color");
                    }
                }
            );
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

    /**
     * 阻止音乐应用获取屏幕关闭的广播，可能可以使其在息屏状态输出歌词
     */
    public static class ScreenHelper {
        public static void screenOffNotStopLyric(@NonNull String... excludes) {
            try {
                MethodDataList methodDataList = DexKitUtils.getDexKitBridge(classLoader)
                    .findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("android.intent.action.SCREEN_OFF")
                            .returnType(void.class)
                            .name("onReceive")
                            .paramTypes(Context.class, Intent.class)
                        )
                    );

                methodDataList.forEach(new Consumer<MethodData>() {
                    @Override
                    public void accept(MethodData methodData) {
                        String className = methodData.getDeclaredClassName();
                        if (!className.contains("Fragment") && !className.contains("Activity")) {
                            if (Arrays.stream(excludes).noneMatch(className::contains)) {
                                logI("ScreenHelper", "screenOffNotStopLyric class name: " + className);

                                try {
                                    hook(methodData.getMethodInstance(classLoader),
                                        new IHook() {
                                            @Override
                                            public void before() {
                                                Intent intent = (Intent) getArg(1);
                                                if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
                                                    returnNull();
                                                }
                                            }
                                        }
                                    );
                                } catch (NoSuchMethodException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                });
            } catch (Throwable e) {
                logE("ScreenHelper", e);
            }
        }
    }
}
