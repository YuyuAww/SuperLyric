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
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;
import com.hchen.superlyricapi.SuperLyricData;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Constructor;
import java.util.LinkedList;

import de.robv.android.xposed.XposedHelpers;

/**
 * Apple Music
 */
@Collect(targetPackage = "com.apple.android.music")
public class Apple extends BaseLyric {
    private Class<?> lyricConvertClass;
    private String lyricConvertMethodName;
    private Object currentSongInfo;
    private Object lyricObject;
    private PlaybackState playbackState;
    private Object playbackItem;
    private Object lyricViewModel;

    private final LinkedList<LyricsLine> lyricList = new LinkedList<>();
    private String currentTitle = "";
    private String currentTrackId;
    private boolean isRunning = false;

    private Handler mainHandler;
    private Handler lyricHandler;
    private LyricsLine lastShownLyric;

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
        // 初始化 Handler
        mainHandler = new Handler(Looper.getMainLooper());
        HandlerThread lyricThread = new HandlerThread("AppleMusicLyricThread");
        lyricThread.start();
        lyricHandler = new Handler(lyricThread.getLooper());

        // Hook 歌词构建方法
        hookMethod("com.apple.android.music.player.viewmodel.PlayerLyricsViewModel", "buildTimeRangeToLyricsMap",
                "com.apple.android.music.ttml.javanative.model.SongInfo$SongInfoPtr",
                new IHook() {
                    @Override
                    public void after() {
                        Object songInfoPtr = getArgs(0);
                        if (songInfoPtr == null) return;

                        currentSongInfo = callMethod(songInfoPtr, "get");
                        if (currentSongInfo == null || lyricConvertClass == null) return;

                        Object lyricsSectionVector = callMethod(currentSongInfo, "getSections");
                        if (lyricsSectionVector == null) return;

                        try {
                            Constructor<?> constructor = lyricConvertClass.getConstructor(lyricsSectionVector.getClass());
                            lyricObject = constructor.newInstance(lyricsSectionVector);
                            updateLyricList();
                        } catch (Exception e) {
                            logE(TAG, "Error creating lyric converter", e);
                        }
                    }
                }
        );

        // Hook PlaybackState 构造
        hookAllConstructor("android.media.session.PlaybackState",
                new IHook() {
                    @Override
                    public void after() {
                        playbackState = (PlaybackState) thisObject();
                    }
                }
        );

        // Hook MediaMetadata 变化
        if (existsClass("android.support.v4.media.session.MediaControllerCompat$a$a")) {
            hookMethod("android.support.v4.media.session.MediaControllerCompat$a$a",
                    "onMetadataChanged",
                    android.media.MediaMetadata.class,
                    new IHook() {
                        @Override
                        public void before() {
                            try {
                                // 获取MediaMetadata实例
                                Object metadataCompat = callStaticMethod(
                                        findClass("android.support.v4.media.MediaMetadataCompat"),
                                        "a",
                                        getArgs(0)
                                );
                                Object metadataOjb = null;
                                String[] possibleFieldNames = {"t", "u", "v", "w", "x", "y", "z"};

                                for (String fieldName : possibleFieldNames) {
                                    try {
                                        metadataOjb = XposedHelpers.getObjectField(metadataCompat, fieldName);
                                        if (metadataOjb instanceof MediaMetadata) {
                                            break;
                                        }
                                        metadataOjb = null;
                                    } catch (NoSuchFieldError ignored) {
                                        // 字段不存在，静默忽略
                                    } catch (Exception ignored) {
                                        logD(TAG, "MediaMetadata is null");
                                        return;
                                    }
                                }

                                MediaMetadata metadata = (MediaMetadata) metadataOjb;
                                String newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);

                                // 检测歌曲变化
                                if (newTitle != null && !currentTitle.equals(newTitle)) {
                                    // 停止现有歌词
                                    sendLyric("");
                                    sendStop(new SuperLyricData().setPackageName(context.getPackageName()));

                                    // 重置现有状态
                                    lyricList.clear();
                                    isRunning = false;
                                    lastShownLyric = null;

                                    // 更新当前歌曲名
                                    currentTitle = newTitle;
                                    logD(TAG, "Current song title: " + currentTitle);

                                    // 请求当前歌词
                                    mainHandler.postDelayed(() -> requestLyrics(), 400);
                                }
                            } catch (Exception e) {
                                logE(TAG, "Error getting MediaMetadata", e);
                            }
                        }
                    }
            );
        }

        // Hook 初始化 LyricViewModel
        hookMethod("com.apple.android.music.AppleMusicApplication",
                "onCreate",
                new IHook() {
                    @Override
                    public void after() {
                        try {
                            Application application = (Application) thisObject();
                            Class<?> playerLyricsViewModelClass = findClass("com.apple.android.music.player.viewmodel.PlayerLyricsViewModel");
                            if (playerLyricsViewModelClass != null) {
                                Constructor<?> constructor = playerLyricsViewModelClass.getConstructor(Application.class);
                                lyricViewModel = constructor.newInstance(application);
                            }
                        } catch (Exception e) {
                            logE(TAG, "Failed to initialize LyricViewModel", e);
                        }
                    }
                }
        );

        // Hook 播放状态变化
        if (existsClass("android.support.v4.media.session.MediaControllerCompat$a$b")) {
            hookMethod("android.support.v4.media.session.MediaControllerCompat$a$b",
                    "handleMessage",
                    android.os.Message.class,
                    new IHook() {
                        @Override
                        public void before() {
                            Message m = (Message) getArgs(0);
                            if (m.what == 2) {
                                // 获取 PlaybackStateCompat 对象
                                Object playbackStateCompat = m.obj;
                                if (playbackStateCompat == null) return;

                                // 获取 PlaybackState 对象
                                Object playbackStateObj = null;
                                String[] possibleFieldNames = {"D", "E", "F", "G", "H", "I", "J", "K"};

                                for (String fieldName : possibleFieldNames) {
                                    try {
                                        playbackStateObj = XposedHelpers.getObjectField(playbackStateCompat, fieldName);
                                        if (playbackStateObj instanceof PlaybackState) {
                                            break;
                                        }
                                        playbackStateObj = null;
                                    } catch (NoSuchFieldError ignored) {
                                        // 字段不存在，静默忽略
                                    } catch (Exception ignored) {
                                        logD(TAG, "PlayBackState is null");
                                        return;
                                    }
                                }

                                playbackState = (PlaybackState) playbackStateObj;
                                updateLyricPosition();
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
                logD(TAG, "Found lyric converter class: " + lyricConvertClass.getName());
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
                logD(TAG, "Found StringVector class: " + clazz.getName());
            }
        } catch (Exception e) {
            logE(TAG, "Failed to find lyric classes", e);
        }
    }

    private void hookPlaybackItemSetId() {
        try {
            Class<?> playbackItemClass = classLoader.loadClass("com.apple.android.music.model.PlaybackItem");

            if (existsClass("com.apple.android.music.model.BaseContentItem")) {
                hookMethod("com.apple.android.music.model.BaseContentItem",
                        "setId",
                        String.class,
                        new IHook() {
                            @Override
                            public void after() {
                                if (playbackItemClass.isInstance(thisObject())) {
                                    String trackId = (String) getArgs(0);
                                    if (trackId == null) return;

                                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                                    String traceString = getStackTraceString(stackTrace);

                                    if (traceString.contains("getItemAtIndex")
                                            && traceString.contains(".accept") // 4.0.0+
                                    ) {
                                        currentTrackId = trackId;
                                        playbackItem = thisObject();
                                        logD(TAG, "Current music ID: " + currentTrackId);
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

    private void requestLyrics() {
        try {
            if (lyricViewModel != null && playbackItem != null) {
                logD(TAG, "Requesting lyrics via ViewModel");
                callMethod(lyricViewModel, "loadLyrics", playbackItem);
            } else {
                logD(TAG, "Unable to request lyrics - missing ViewModel or PlaybackItem");
            }
        } catch (Exception e) {
            logE(TAG, "Error requesting lyrics", e);
        }
    }

    private void updateLyricList() {
        if (lyricObject == null || lyricConvertMethodName == null) return;
        LinkedList<LyricsLine> newLyricList = new LinkedList<>();

        try {
            int i = 0;
            while (true) {
                Object lyricsLinePtr;
                try {
                    lyricsLinePtr = callMethod(lyricObject, lyricConvertMethodName, i);
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
                    newLyricList.add(new LyricsLine(start, end, lyric));
                }
                i++;
            }
            if (!newLyricList.isEmpty() && (lyricList.isEmpty() || newLyricList.getFirst().start != lyricList.getFirst().start)) {
                lyricList.clear();
                lyricList.addAll(newLyricList);
            }

            logD(TAG, "Loaded " + lyricList.size() + " lyrics lines");
        } catch (Exception e) {
            logE(TAG, "Error processing lyrics", e);
        }
    }

    private void updateLyricPosition() {
        if (isRunning) return;

        isRunning = true;
        lyricHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRunning || playbackState == null) {
                    isRunning = false;
                    return;
                }

                // 暂停状态处理
                if (playbackState.getState() == PlaybackState.STATE_PAUSED) {
                    isRunning = false;
                    return;
                }

                // 计算当前播放位置
                long currentPosition = (long) (((SystemClock.elapsedRealtime() -
                        playbackState.getLastPositionUpdateTime()) *
                        playbackState.getPlaybackSpeed()) +
                        playbackState.getPosition());

                // 查找并显示当前歌词
                LyricsLine currentLine = null;
                for (LyricsLine line : lyricList) {
                    if (currentPosition >= line.start && currentPosition < line.end) {
                        currentLine = line;
                        break;
                    }
                }

                if (currentLine != null && (lastShownLyric == null || lastShownLyric != currentLine)) {
                    sendLyric(currentLine.lyric);
                    lastShownLyric = currentLine;
                }

                // 循环获取
                lyricHandler.postDelayed(this, 400);
            }
        });
    }

    private String getStackTraceString(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

}