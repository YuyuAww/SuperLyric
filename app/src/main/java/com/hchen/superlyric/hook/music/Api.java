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

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;
import com.hchen.superlyricapi.SuperLyricData;

public class Api extends BaseLyric {
    @Override
    protected boolean enabled() {
        return existsClass("com.hchen.superlyricapi.SuperLyricTool");
    }

    @Override
    protected void init() {
        setStaticField("com.hchen.superlyricapi.SuperLyricTool", "isEnabled", true);
        hookMethod("com.hchen.superlyricapi.SuperLyricPush",
            "onStop",
            "com.hchen.superlyricapi.SuperLyricData",
            new IHook() {
                @Override
                public void after() {
                    Parcel parcel = (Parcel) callMethod(getArgs(0), "marshall");
                    if (parcel != null) {
                        sendStop(SuperLyricData.unmarshall(parcel));
                    }
                }
            }
        );
        hookMethod("com.hchen.superlyricapi.SuperLyricPush",
            "onSuperLyric",
            "com.hchen.superlyricapi.SuperLyricData",
            new IHook() {
                @Override
                public void after() {
                    Parcel parcel = (Parcel) callMethod(getArgs(0), "marshall");
                    if (parcel != null) {
                        sendSuperLyricData(SuperLyricData.unmarshall(parcel));
                    }
                }
            }
        );
    }

    @Nullable
    @Override
    public String addExempt() {
        return mContext.getPackageName();
    }
}
