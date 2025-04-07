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
import com.hchen.superlyric.base.BaseLyric;

import java.util.Objects;

@Collect(targetPackage = "com.miui.player")
public class MiPlayer extends BaseLyric {
    @Override
    protected void init() {
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
                    if (getArgs(1) == null) return;
                    String lyric = (String) getArgs(1);
                    if (Objects.equals("NEED_NOT_UPDATE_TITLE", lyric)) return;
                    sendLyric(lyric);
                }
            }
        );
    }
}
