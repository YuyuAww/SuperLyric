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
package com.hchen.superlyric.utils;

import androidx.annotation.NonNull;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * DexKit 工具
 *
 * @author 焕晨HChen
 * @deprecated
 */
@Deprecated
public class DexKitUtils {
    private static final String TAG = "DexKitUtils";
    private static XC_LoadPackage.LoadPackageParam loadPackageParam;
    private static DexKitBridge dexKitBridge;
    private static boolean isInit = false;

    public static void init(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        DexKitUtils.loadPackageParam = loadPackageParam;
        isInit = false;
    }

    private static void initDexKit(@NonNull ClassLoader classLoader) {
        if (dexKitBridge != null) return;

        System.loadLibrary("dexkit");
        dexKitBridge = DexKitBridge.create(classLoader, false);
        isInit = true;
    }

    private static void initDexKit() {
        if (dexKitBridge != null) return;

        System.loadLibrary("dexkit");
        String hostDir = loadPackageParam.appInfo.sourceDir;
        dexKitBridge = DexKitBridge.create(hostDir);
        isInit = true;
    }

    @NonNull
    public static DexKitBridge getDexKitBridge1(@NonNull ClassLoader classLoader) {
        if (!isInit)
            initDexKit(classLoader);

        if (dexKitBridge == null)
            throw new NullPointerException("[SuperLyric]: DexKitBridge is null!!");
        return dexKitBridge;
    }

    @NonNull
    public static DexKitBridge getDexKitBridge1() {
        if (!isInit)
            initDexKit();

        if (dexKitBridge == null)
            throw new NullPointerException("[SuperLyric]: DexKitBridge is null!!");
        return dexKitBridge;
    }

    /**
     * 请勿手动调用
     */
    public static void close() {
        if (!isInit) return;

        if (dexKitBridge != null)
            dexKitBridge.close();
        dexKitBridge = null;
        loadPackageParam = null;
        isInit = false;
    }
}
