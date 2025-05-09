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

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * DexKit 工具
 *
 * @author 焕晨HChen
 */
public class DexKitUtils {
    private static final String TAG = "DexKitUtils";
    private static boolean isInit = false;
    private static XC_LoadPackage.LoadPackageParam mLoadPackageParam;
    private static DexKitBridge mDexKitBridge;

    public static void init(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        mLoadPackageParam = loadPackageParam;
        isInit = false;
    }

    private static void initDexKit(ClassLoader classLoader) {
        if (mDexKitBridge != null) return;

        System.loadLibrary("dexkit");
        mDexKitBridge = DexKitBridge.create(classLoader, false);
        isInit = true;
    }

    private static void initDexKit() {
        if (mDexKitBridge != null) return;

        System.loadLibrary("dexkit");
        String hostDir = mLoadPackageParam.appInfo.sourceDir;
        mDexKitBridge = DexKitBridge.create(hostDir);
        isInit = true;
    }

    public static DexKitBridge getDexKitBridge(ClassLoader classLoader) {
        if (!isInit)
            initDexKit(classLoader);

        if (mDexKitBridge == null)
            throw new RuntimeException("[SuperLyric]: mDexKitBridge is null!!");
        return mDexKitBridge;
    }

    public static DexKitBridge getDexKitBridge() {
        if (!isInit)
            initDexKit();

        if (mDexKitBridge == null)
            throw new RuntimeException("[SuperLyric]: mDexKitBridge is null!!");
        return mDexKitBridge;
    }

    /**
     * 请勿手动调用
     */
    public static void close() {
        if (!isInit) return;

        if (mDexKitBridge != null) {
            mDexKitBridge.close();
            mDexKitBridge = null;
        }
        mLoadPackageParam = null;
        isInit = false;
    }
}
