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

import static android.view.View.GONE;

import android.view.View;
import android.widget.TextView;

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;

import java.util.HashSet;

/**
 * 汽水音乐
 */
@Collect(targetPackage = "com.luna.music")
public class Qishui extends BaseLyric {
    private static final HashSet<String> mTargetView = new HashSet<>();

    static {
        mTargetView.add("LyricTextView");
        mTargetView.add("MarqueeLastLineLyricTextView");
    }

    @Override
    protected void init() {
        hookMethod(TextView.class,
            "setText",
            CharSequence.class,
            new IHook() {
                @Override
                public void after() {
                    if (mTargetView.contains(thisObject().getClass().getSimpleName())) {
                        sendLyric((String) getArgs(0));
                    }
                }
            }
        );

        hookConstructor("com.luna.biz.playing.lyric.floatinglyrics.view.FloatingLyricFrameLayout",
            android.content.Context.class, android.util.AttributeSet.class, int.class, new IHook() {
                @Override
                public void after() {
                    View view = (View) thisObject();
                    view.setVisibility(GONE);
                }
            }
        );
    }
}
