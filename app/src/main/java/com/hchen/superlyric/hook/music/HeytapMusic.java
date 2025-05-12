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

import androidx.annotation.NonNull;

import com.hchen.collect.Collect;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.hook.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import kotlin.jvm.functions.Function0;

/**
 * OPPO 音乐
 */
@Collect(targetPackage = "com.heytap.music")
public class HeytapMusic extends BaseLyric {
    @Override
    protected void init() {
    }

    @Override
    protected void onApplication(@NonNull Context context) {
        super.onApplication(context);
        HCInit.setClassLoader(context.getClassLoader());

        OPPOHelper.mockDevice();
        getMediaMetadataCompatLyric();

        MethodData methodData = DexKitUtils.getDexKitBridge().findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass("com.allsaints.music.player.thirdpart.MediaSessionHelper")
                .usingStrings("isCarBluetoothConnected 没有蓝牙连接权限")
            )
        ).singleOrThrow(new Function0<Throwable>() {
            @Override
            public Throwable invoke() {
                return new Throwable("Failed to find bluetooth method!!");
            }
        });

        try {
            hook(methodData.getMethodInstance(classLoader),
                new IHook() {
                    @Override
                    public void after() {
                        setResult(true);
                    }
                }
            );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
