package com.hchen.superlyric.hook.music;

import android.content.Context;
import android.content.SharedPreferences;

import com.hchen.collect.Collect;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;
import com.hchen.superlyric.utils.DexKitUtils;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 网易云音乐
 */
@Collect(targetPackage = "com.netease.cloudmusic")
public class Netease extends BaseLyric {

    @Override
    protected void init() {
        if (existsClass("android.app.Instrumentation")) {
            hookMethod("android.app.Instrumentation",
                "newApplication",
                ClassLoader.class, String.class, Context.class,
                new IHook() {
                    @Override
                    public void before() {
                        if (Objects.equals("com.netease.nis.wrapper.MyApplication", getArgs(1))) {
                            setArgs(1, "com.netease.cloudmusic.CloudMusicApplication");
                            logD(TAG, "Hooked netease wrapper class");
                        }
                    }
                }
            );
        }
    }

    @Override
    protected void onApplicationAfter(Context context) {
        super.onApplicationAfter(context);
        HCInit.setClassLoader(context.getClassLoader());
        onTinker();

        if (versionCode >= 8000041 || Objects.equals("com.hihonor.cloudmusic", lpparam.packageName)) {
            MockFlyme.mock();
            MockFlyme.notificationLyric(this);

            ClassData classData = DexKitUtils.getDexKitBridge(classLoader).findClass(FindClass.create()
                .matcher(ClassMatcher.create()
                    .usingStrings("com/netease/cloudmusic/module/lyric/flyme/StatusBarLyricSettingManager.class:setSwitchStatus:(Z)V")
                )
            ).singleOrNull();

            try {
                if (classData != null) {
                    Class<?> clazz = classData.getInstance(classLoader);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.getReturnType().equals(boolean.class)) {
                            hook(method, returnResult(true));
                        } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(boolean.class)) {
                            hook(method, new IHook() {
                                @Override
                                public void before() {
                                    setArgs(0, true);
                                }
                            });
                        } else if (method.getReturnType().equals(SharedPreferences.class)) {
                            hook(method, new IHook() {
                                @Override
                                public void after() {
                                    SharedPreferences sp = (SharedPreferences) getResult();
                                    sp.edit().putBoolean("status_bar_lyric_setting_key", true).apply();
                                }
                            });
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                logE(TAG, "Failed to hook status bar lyric!!");
            }
        } else {
            mediaMetadataCompatLyric();
        }
    }
}
