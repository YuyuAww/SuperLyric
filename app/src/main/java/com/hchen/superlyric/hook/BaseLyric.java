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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.HCData;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.Objects;

/**
 * Super Lyric 基类
 *
 * @author 焕晨HChen
 */
public abstract class BaseLyric extends HCBase {
    private static ISuperLyricDistributor iSuperLyricDistributor;
    public static AudioManager audioManager;
    public static String packageName;
    public static long versionCode = -1L;
    public static String versionName = "unknown";

    @Override
    @CallSuper
    protected void onApplicationAfter(@NonNull Context context) {
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
        } catch (PackageManager.NameNotFoundException ignore) {
        }

        logD(TAG, "Success to get binder: " + iSuperLyricDistributor + ", caller package name: " + packageName);
    }

    /**
     * Hook 热更新服务，用于更改当前 classloader
     */
    public static void hookTencentTinker() {
        hookMethod("com.tencent.tinker.loader.TinkerLoader",
            "tryLoad",
            "com.tencent.tinker.loader.app.TinkerApplication",
            new IHook() {
                @Override
                public void after() {
                    Intent intent = (Intent) getResult();
                    Application application = (Application) getArg(0);
                    int code = intent.getIntExtra("intent_return_code", -2);
                    if (code == 0) {
                        HCData.setClassLoader(application.getClassLoader());
                    }
                }
            }
        );
    }

    /**
     * 模拟蓝牙为开启状态
     */
    public static void openBluetoothA2dp() {
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
    public static void getMediaMetadataCompatLyric() {
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

    private static String lastLyric;

    /**
     * 发送歌词
     *
     * @param lyric 歌词
     */
    public static void sendLyric(String lyric) {
        sendLyric(lyric, 0);
    }

    /**
     * 发送歌词和当前歌词的持续时间 (ms)
     *
     * @param lyric 歌词
     * @param delay 歌词持续时间 (ms)
     */
    public static void sendLyric(String lyric, int delay) {
        if (lyric == null) return;
        if (iSuperLyricDistributor == null) return;

        try {
            lyric = lyric.trim();
            if (Objects.equals(lyric, lastLyric)) return;
            if (lyric.isEmpty()) return;
            lastLyric = lyric;

            iSuperLyricDistributor.onSuperLyric(
                new SuperLyricData()
                    .setPackageName(packageName)
                    .setLyric(lyric)
                    .setDelay(delay)
            );
        } catch (RemoteException e) {
            logE("BaseLyric", "sendLyric: ", e);
        }

        logD("BaseLyric", delay != 0 ? "Send lyric: " + lyric + ", delay: " + delay : "Send lyric: " + lyric);
    }

    /**
     * 发送播放状态暂停
     */
    public static void sendStop() {
        sendStop(packageName);
    }

    /**
     * 发送播放状态暂停
     *
     * @param packageName 暂停播放的音乐软件包名
     */
    public static void sendStop(String packageName) {
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
    public static void sendStop(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onStop(data);
        } catch (RemoteException e) {
            logE("BaseLyric", "sendStop: " + e);
        }

        logD("BaseLyric", "Stop lyric: " + data);
    }

    /**
     * 发送数据包
     *
     * @param data 数据
     */
    public static void sendSuperLyricData(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE("BaseLyric", "sendSuperLyricData: " + e);
        }

        logD("BaseLyric", "Send data: " + data);
    }
}
