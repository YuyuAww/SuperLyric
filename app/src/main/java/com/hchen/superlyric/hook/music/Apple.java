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

import android.app.Application;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Apple Music
 */
@Collect(targetPackage = "com.apple.android.music")
public class Apple extends BaseLyric {

    private Class<?> lyricConvertClass;
    private String lyricConvertMethodName;
    private PlaybackState playbackState;

    private final LinkedList<LyricsLine> lyricList = new LinkedList<>();
    private String currentTitle = "";
    private int delay = 0;

    private Timer timer;
    private boolean isRunning = false;

    static class LyricsLine {
        int start;
        int end;
        String lyric;

        LyricsLine(int start, int end, String lyric) {
            this.start = start;
            this.end = end;
            this.lyric = lyric;
        }
    }

    @Override
    protected void init() {
        hookMethod("com.apple.android.music.player.viewmodel.PlayerLyricsViewModel", "buildTimeRangeToLyricsMap",
                "com.apple.android.music.ttml.javanative.model.SongInfo$SongInfoPtr",
                new IHook() {
                    @Override
                    public void after() {
                        Object curSongInfo = callMethod(getArgs(0), "get");
                        if (curSongInfo == null) return;

                        Object lyricsSectionVector = callMethod(curSongInfo, "getSections");
                        if (lyricsSectionVector == null || lyricConvertClass == null) return;

                        try {
                            Constructor<?> constructor = lyricConvertClass.getConstructor(lyricsSectionVector.getClass());
                            Object curLyricObj = constructor.newInstance(lyricsSectionVector);

                            lyricList.clear();
                            int i = 1;
                            while (true) {
                                Object lyricsLinePtr;
                                try {
                                    lyricsLinePtr = callMethod(curLyricObj, lyricConvertMethodName, i);
                                    if (lyricsLinePtr == null) break;
                                } catch (Exception e) {
                                    break;
                                }

                                Object lyricsLine = callMethod(lyricsLinePtr, "get");
                                if (lyricsLine == null) break;

                                String lyric = (String) callMethod(lyricsLine, "getHtmlLineText");
                                Integer start = (Integer) callMethod(lyricsLine, "getBegin");
                                Integer end = (Integer) callMethod(lyricsLine, "getEnd");

                                if (lyric != null && start != null && end != null) {
                                    if (!lyricList.isEmpty() && lyricList.getLast().start > start) {
                                        lyricList.clear();
                                    }
                                    lyricList.add(new LyricsLine(start, end, lyric));
                                }
                                i++;
                            }
                        } catch (Exception e) {
                            logE(TAG, "Error processing lyrics", e);
                        }
                    }
                }
        );

        hookAllConstructor("android.media.session.PlaybackState",
                new IHook() {
                    @Override
                    public void after() {
                        playbackState = (PlaybackState) thisObject();
                    }
                }
        );

        if (existsClass("android.support.v4.media.MediaMetadataCompat")) {
            hookAllMethod("android.support.v4.media.MediaMetadataCompat", "a",
                    new IHook() {
                        @Override
                        public void after() {
                            if (getArgs(0) instanceof MediaMetadata mediaMetadata) {
                                String title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                                if (title != null && !title.equals(currentTitle)) {
                                    currentTitle = title;
                                    lyricList.clear();
                                    sendStop(null);
                                }
                            }
                        }
                    }
            );
        }

        if (existsClass("android.support.v4.media.session.MediaControllerCompat$a$b")) {
            hookAllMethod("android.support.v4.media.session.MediaControllerCompat$a$b",
                    "handleMessage",
                    new IHook() {
                        @Override
                        public void after() {
                            if (getArgs(0) instanceof Message message && message.what == 2 && playbackState != null) {
                                switch (playbackState.getState()) {
                                    case PlaybackState.STATE_PLAYING:
                                        startTimer();
                                        break;
                                    case PlaybackState.STATE_PAUSED:
                                        stopTimer();
                                        break;
                                }
                            }
                        }
                    }
            );
        }

        findLyricClasses();
        hookPlaybackItemSetId();
    }

    private void findLyricClasses() {
        try {
            MethodData methodData = DexKitUtils.getDexKitBridge().findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .returnType("com.apple.android.music.ttml.javanative.model.LyricsLine$LyricsLinePtr")
                            .paramCount(1)
                            .paramTypes("int")
                            .usingNumbers(0)
                    )
            ).singleOrNull();

            if (methodData != null) {
                lyricConvertClass = classLoader.loadClass(methodData.getDeclaredClassName());
                lyricConvertMethodName = methodData.getName();
            }

            ClassData stringVectorClass = DexKitUtils.getDexKitBridge().findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .usingStrings("No Internet, Unable to get the SongInfo instance.")
                    )
            ).singleOrNull();

            if (stringVectorClass != null) {
                Class<?> clazz = classLoader.loadClass(stringVectorClass.getName());
                clazz.getConstructor(Context.class, long.class, long.class, long.class,
                        classLoader.loadClass("com.apple.android.mediaservices.javanative.common.StringVector$StringVectorNative"),
                        boolean.class);
            }
        } catch (Exception e) {
            logE(TAG, "Failed to find lyric classes", e);
        }
    }

    private void hookPlaybackItemSetId() {
        try {
            Class<?> playbackItemClass = classLoader.loadClass("com.apple.android.music.model.PlaybackItem");
            Class<?> playerLyricsViewModelClass = classLoader.loadClass("com.apple.android.music.player.viewmodel.PlayerLyricsViewModel");
            final Constructor<?> viewModelConstructor = playerLyricsViewModelClass.getConstructor(Application.class);

            if (existsClass("com.apple.android.music.model.BaseContentItem")) {
                hookMethod("com.apple.android.music.model.BaseContentItem",
                        "setId",
                        String.class,
                        new IHook() {
                            @Override
                            public void after() {
                                if (playbackItemClass.isInstance(thisObject())) {
                                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                                    String traceString = getStackTraceString(stackTrace);

                                    if (traceString.contains("getItemAtIndex") &&
                                            (traceString.contains("i7.u.accept") || traceString.contains("e3.h.w") || traceString.contains("k7.t.accept"))) {
                                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                            try {
                                                Object viewModel = viewModelConstructor.newInstance(context);
                                                callMethod(viewModel, "loadLyrics", thisObject());
                                            } catch (Exception e) {
                                                logE(TAG, "Error loading lyrics", e);
                                            }
                                        }, 400);
                                    }
                                }
                            }
                        }
                );
            }
        } catch (Exception e) {
            logE(TAG, "Failed to hook PlaybackItem.setId", e);
        }
    }

    private String getStackTraceString(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    private void startTimer() {
        if (isRunning) return;

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (lyricList.isEmpty() || playbackState == null) return;
                long currentPosition = (long) ((SystemClock.elapsedRealtime() - playbackState.getLastPositionUpdateTime())
                        * playbackState.getPlaybackSpeed() + playbackState.getPosition());

                for (LyricsLine line : lyricList) {
                    if (line.start <= currentPosition && line.end >= currentPosition) {
                        delay = (line.end - line.start);
                        sendLyric(line.lyric, delay);
                        break;
                    }
                }
            }
        }, 0, 400);

        isRunning = true;
    }

    private void stopTimer() {
        if (!isRunning) return;

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        sendStop(null);
        isRunning = false;
    }
}