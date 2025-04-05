package com.hchen.superlyric;

import static com.hchen.hooktool.HCInit.LOG_D;

import com.hchen.hooktool.HCEntrance;
import com.hchen.hooktool.HCInit;
import com.hchen.superlyric.hook.SuperLyricProxy;
import com.hchen.superlyric.hook.music.MiPlayer;

import java.util.Objects;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class InitHook extends HCEntrance {
    @Override
    public HCInit.BasicData initHC(HCInit.BasicData basicData) {
        return basicData.setTag("SuperLyric")
            .setLogLevel(LOG_D)
            .setModulePackageName(BuildConfig.APPLICATION_ID)
            .initLogExpand(new String[]{
                "com.hchen.superlyric.hook"
            });
    }

    @Override
    public String[] ignorePackageNameList() {
        return new String[]{"com.miui.contentcatcher", "com.android.providers.settings", "com.android.server.telecom"};
    }

    @Override
    public void onLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (Objects.equals(lpparam.packageName, "android"))
            new SuperLyricProxy().onLoadPackage();
        else if (Objects.equals(lpparam.packageName, "com.miui.player")) {
            new MiPlayer().onApplicationCreate().onLoadPackage();
        }
    }
}
