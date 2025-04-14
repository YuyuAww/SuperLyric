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

import android.app.Notification;
import android.app.Service;

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;

/**
 * Gramophone [留声机]
 */
@Collect(targetPackage = "org.akanework.gramophone")
public class Gramophone extends BaseLyric {
    @Override
    protected void init() {
        if (existsClass("androidx.media3.common.util.Util")) {
            hookMethod("androidx.media3.common.util.Util",
                    "setForegroundServiceNotification",
                    Service.class, int.class, Notification.class, int.class, String.class,
                    new IHook() {
                        @Override
                        public void before() {
                            Notification notification = (Notification) getArgs(2);
                            if (notification == null || notification.tickerText == null) return;

                            String lyric = notification.tickerText.toString();
                            if (lyric.isEmpty()) return;

                            sendLyric(lyric);
                        }
                    }
            );
        }
    }
}