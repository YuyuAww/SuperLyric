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

import com.hchen.collect.Collect;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.tool.itool.IMemberFilter;
import com.hchen.superlyric.base.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * 酷我音乐
 */
@Collect(targetPackage = "cn.kuwo.player")
public class KuWo extends BaseLyric {
    @Override
    protected void init() {
    }

    @Override
    protected void onApplicationAfter(Context context) {
        super.onApplicationAfter(context);

        HCInit.setClassLoader(context.getClassLoader());

        if (existsClass("cn.kuwo.mod.playcontrol.RemoteControlLyricMgr")) {
            hookAllMethod("cn.kuwo.mod.playcontrol.RemoteControlLyricMgr",
                "updateLyricText",
                new IHook() {
                    @Override
                    public void after() {
                        String lyric = (String) getArgs(0);
                        if (lyric == null || lyric.isEmpty()) return;

                        Timeout.start(KuWo.this);
                        sendLyric(lyric);
                    }
                }
            );
        } else {
            Class<?> confMMKVMgrImplClass = findClass("cn.kuwo.base.config.ConfMMKVMgrImpl");
            if (confMMKVMgrImplClass == null) return;

            hook(filterMethod(confMMKVMgrImplClass, new IMemberFilter<Method>() {
                    @Override
                    public boolean test(Method member) {
                        return Objects.equals(member.getReturnType(), boolean.class) &&
                            member.getParameterCount() == 3 &&
                            Arrays.equals(member.getParameterTypes(), new Class<?>[]{String.class, String.class, boolean.class});
                    }
                })[0],
                new IHook() {
                    @Override
                    public void before() {
                        String key = (String) getArgs(1);
                        if (Objects.equals(key, "bluetooth_car_lyric"))
                            setResult(true);
                    }
                }
            );

            openBluetoothA2dp();

            ClassData classData = DexKitUtils.getDexKitBridge(context.getClassLoader())
                .findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("正在搜索歌词...", "bluetooth_car_lyric")
                    )
                ).singleOrNull();

            try {
                if (classData == null) return;

                Class<?> clazz = classData.getInstance(classLoader);
                hook(filterMethod(clazz, new IMemberFilter<Method>() {
                        @Override
                        public boolean test(Method member) {
                            return member.getParameterCount() == 1 && Objects.equals(member.getParameterTypes()[0], String.class);
                        }
                    })[0],
                    new IHook() {
                        @Override
                        public void before() {
                            String lyric = (String) getArgs(0);
                            if (lyric == null || lyric.isEmpty()) return;

                            Timeout.start(KuWo.this);
                            sendLyric(lyric);
                        }
                    }
                );
            } catch (ClassNotFoundException e) {
                logE(TAG, e);
            }
        }
    }
}
