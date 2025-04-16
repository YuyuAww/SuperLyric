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

import androidx.annotation.NonNull;

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * QQ 音乐
 *
 * @author 焕晨HChen
 */
@Collect(targetPackage = "com.tencent.qqmusic")
public class QQMusic extends BaseLyric {
    private final CopyOnWriteArrayList<LyricData> lyricDataList = new CopyOnWriteArrayList<>();
    private Field lyricField;
    private Field durationField;
    private Field playedDurationField;

    @Override
    protected void init() {
        // MockFlyme.mock();
        // MockFlyme.notificationLyric(this);

        Class<?> marqueeLyricViewClass = findClass("com.lyricengine.ui.MarqueeLyricView");
        if (marqueeLyricViewClass == null) return;

        ClassData classData = DexKitUtils.getDexKitBridge().findClass(FindClass.create()
            .matcher(ClassMatcher.create()
                .usingStrings("[generateTextBySplit] get singer text failed!")
            )
        ).singleOrNull();

        if (classData != null) {
            try {
                FieldData fieldData = DexKitUtils.getDexKitBridge().findField(FindField.create()
                    .matcher(FieldMatcher.create()
                        .declaredClass(classData.getInstance(classLoader))
                        .type(long.class)
                        .addReadMethod(MethodMatcher.create()
                            .declaredClass(marqueeLyricViewClass)
                            .name("forbidLineHighlight")
                        )
                    )
                ).singleOrNull();

                if (fieldData == null) return;
                durationField = fieldData.getFieldInstance(classLoader);
            } catch (Throwable e) {
                logE(TAG, e);
            }
        }

        MethodData methodData = DexKitUtils.getDexKitBridge().findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass(ClassMatcher.create()
                    .usingStrings("showLyricView operateLyric:true")
                )
                .usingStrings("showLyricView operateLyric:true")
            )
        ).singleOrThrow(() -> new Throwable("Failed to find visibility method!!"));

        try {
            Class<?> clazz = methodData.getClassInstance(classLoader);
            Field viewField = null;
            for (Field field : clazz.getDeclaredFields()) {
                if (Objects.equals(field.getType(), View.class)) {
                    viewField = field;
                    break;
                }
            }
            if (viewField == null)
                return;

            Field finalViewField = viewField;
            hook(methodData.getMethodInstance(classLoader),
                new IHook() {
                    @Override
                    public void after() {
                        View view = (View) getThisField(finalViewField);
                        view.setVisibility(GONE);
                    }
                }
            );
        } catch (Throwable e) {
            logE(TAG, e);
            return;
        }

        MethodData getIntMethodData = DexKitUtils.getDexKitBridge().findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass(ClassMatcher.create()
                    .usingStrings("[ifNeedTransfer] import ")
                )
                .name("getInt")
            )
        ).singleOrThrow(() -> new Throwable("Failed to find getInt method!!"));

        try {
            hook(getIntMethodData.getMethodInstance(classLoader),
                new IHook() {
                    @Override
                    public void before() {
                        String key = (String) getArgs(0);
                        if (Objects.equals(key, "KEY_STATUS_BAR_LYRIC_SWITCH")) {
                            setResult(1);
                        }
                    }
                }
            );
        } catch (Throwable e) {
            logE(TAG, e);
            return;
        }

        hookAllMethod(marqueeLyricViewClass,
            "setLyric", new IHook() {
                @Override
                public void after() {
                    Object mLyric = getThisField("mLyric");
                    if (mLyric == null) return;

                    updateLyricData(mLyric);
                }
            }
        );

        hookMethod("com.lyricengine.ui.base.BaseLyricView",
            "findCurrentLine",
            int.class, CopyOnWriteArrayList.class, long.class,
            new IHook() {
                @Override
                public void after() {
                    sendLyric((Integer) getResult());
                }
            }
        );

        ClassData classData1 = DexKitUtils.getDexKitBridge().findClass(FindClass.create()
            .matcher(ClassMatcher.create()
                .usingStrings("[addWindowIfNotExist] addView")
            )
        ).singleOrNull();

        try {
            if (classData1 == null) return;

            Method method = null;
            for (Method m : classData1.getInstance(classLoader).getDeclaredMethods()) {
                if (m.getParameterCount() == 2 && Objects.equals(m.getParameterTypes()[1], Object.class)) {
                    method = m;
                    break;
                }
            }
            if (method == null) return;

            hook(method,
                new IHook() {
                    @Override
                    public void before() {
                        boolean b = (boolean) getArgs(1);
                        if (!b)
                            setResult(null);
                    }
                }
            );
        } catch (Throwable e) {
            logE(TAG, e);
        }
    }

    private void updateLyricData(Object mLyric) {
        CopyOnWriteArrayList<?> copyOnWriteArrayList = new CopyOnWriteArrayList<>();
        for (Field field : mLyric.getClass().getDeclaredFields()) {
            Object value = getField(mLyric, field);
            if (value instanceof CopyOnWriteArrayList<?> list) {
                copyOnWriteArrayList = list;
                break;
            }
        }

        lyricDataList.clear();
        for (Object data : copyOnWriteArrayList) {
            if (lyricField == null) {
                findField:
                for (Field f : data.getClass().getDeclaredFields()) {
                    if (Objects.equals(String.class, f.getType())) {
                        lyricField = f;
                        if (durationField == null || playedDurationField != null)
                            break findField;
                    } else if (!Objects.equals(f, durationField) && Objects.equals(long.class, f.getType())) {
                        playedDurationField = f;
                        if (lyricField != null)
                            break findField;
                    }
                }
            }

            if (lyricField == null) return;
            if (durationField == null && playedDurationField == null) {
                lyricDataList.add(new LyricData((String) getField(data, lyricField), -1, -1));
            } else if (durationField != null && playedDurationField == null) {
                lyricDataList.add(new LyricData((String) getField(data, lyricField), -1, (Long) getField(data, durationField)));
            } else if (durationField != null && playedDurationField != null) {
                lyricDataList.add(new LyricData((String) getField(data, lyricField), (Long) getField(data, playedDurationField), (Long) getField(data, durationField)));
            }
        }
    }

    private void sendLyric(Integer line) {
        if (line == null) return;
        if (lyricDataList.isEmpty()) return;

        LyricData data = lyricDataList.get(line);
        String lyric = data.lyric;
        // logI(TAG, "lyric data: " + data);
        if (lyric == null || lyric.isEmpty()) return;
        if (data.duration == -1)
            sendLyric(lyric);
        else
            sendLyric(lyric, (int) data.duration);
    }

    private static class LyricData {
        String lyric;
        long playedDuration;
        long duration;

        public LyricData(String lyric, long playedDuration, long duration) {
            this.lyric = lyric;
            this.playedDuration = playedDuration;
            this.duration = duration;
        }

        @NonNull
        @Override
        public String toString() {
            return "LyricData{" +
                "lyric='" + lyric + '\'' +
                ", playedDuration=" + playedDuration +
                ", duration=" + duration +
                '}';
        }
    }
}
