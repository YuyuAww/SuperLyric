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

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.hook.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 汽水音乐
 *
 * @author 焕晨HChen
 */
@Collect(targetPackage = "com.luna.music")
public class Qishui extends BaseLyric {
    private static Object BLUETOOTH;

    @Override
    protected void init() {
        openBluetoothA2dp();
        getMediaMetadataCompatLyric();

        Class<?> blueToothLyricStatus = findClass("com.luna.biz.playing.lyric.bluetoothlyrics.BlueToothLyricStatus");
        BLUETOOTH = getStaticField("com.luna.common.arch.device.OutputDeviceType", "BLUETOOTH");
        MethodData methodData = DexKitUtils.getDexKitBridge().findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .paramTypes(null, blueToothLyricStatus, boolean.class, int.class, Object.class)
            )
        ).single();

        try {
            Method method = methodData.getMethodInstance(classLoader);
            Class<?> clazz = method.getParameterTypes()[0];

            findMethodPro(clazz)
                .withParamCount(1)
                .withReturnType(boolean.class)
                .single()
                .hook(
                    new IHook() {
                        @Override
                        public void before() {
                            Object N = getArg(0);
                            if (N == null) {
                                setResult(true);
                            } else {
                                Object type = callMethod(N, "getType");
                                if (Objects.equals(type, BLUETOOTH)) {
                                    setResult(true);
                                }
                            }
                        }
                    }
                );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
