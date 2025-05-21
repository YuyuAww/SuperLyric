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
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.hchen.collect.Collect;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.hook.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 网易云音乐
 */
@Collect(targetPackage = "com.netease.cloudmusic")
public class Netease extends BaseLyric {
    @Override
    protected void init() {
        hookTencentTinker();

        if (existsClass("android.app.Instrumentation")) {
            hookMethod("android.app.Instrumentation",
                "newApplication",
                ClassLoader.class, String.class, Context.class,
                new IHook() {
                    @Override
                    public void before() {
                        if (Objects.equals("com.netease.nis.wrapper.MyApplication", getArg(1))) {
                            setArg(1, "com.netease.cloudmusic.CloudMusicApplication");
                            logD(TAG, "Hooked netease wrapper class");
                        }
                    }
                }
            );
        }
    }

    @Override
    protected void onApplication(@NonNull Context context) {
        super.onApplication(context);
        HCInit.setClassLoader(context.getClassLoader());

        if (versionCode >= 8000041) {
            MeizuHelper.mockDevice();
            MeizuHelper.getFlymeNotificationLyric();

            MethodData methodData = DexKitUtils.getDexKitBridge(classLoader).findMethod(FindMethod.create()
                .matcher(MethodMatcher.create()
                    .declaredClass(ClassMatcher.create()
                        .usingStrings("KEY_SHOW_LOCK_SCREEN_PERMISSION")
                    )
                    .usingStrings("KEY_SHOW_LOCK_SCREEN_PERMISSION")
                )
            ).singleOrNull();

            try {
                if (methodData != null) {
                    hook(methodData.getMethodInstance(classLoader),
                        returnResult(null)
                    );
                }
            } catch (Throwable e) {
                logE(TAG, e);
            }

            ClassData classData = DexKitUtils.getDexKitBridge(classLoader).findClass(FindClass.create()
                .matcher(ClassMatcher.create()
                    .usingStrings("com/netease/cloudmusic/module/lyric/flyme/StatusBarLyricSettingManager.class:setSwitchStatus:(Z)V")
                )
            ).singleOrNull();

            try {
                if (classData != null) {
                    Class<?> clazz = classData.getInstance(classLoader);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.getReturnType().equals(boolean.class)) {
                            hook(method, returnResult(true));
                        } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(boolean.class)) {
                            hook(method, new IHook() {
                                @Override
                                public void before() {
                                    setArg(0, true);
                                }
                            });
                        } else if (method.getReturnType().equals(SharedPreferences.class)) {
                            hook(method, new IHook() {
                                @Override
                                public void after() {
                                    SharedPreferences sp = (SharedPreferences) getResult();
                                    sp.edit().putBoolean("status_bar_lyric_setting_key", true).apply();
                                }
                            });
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                logE(TAG, "Failed to hook status bar lyric!!");
            }
        } else {
            getMediaMetadataCompatLyric();
        }
    }
}
