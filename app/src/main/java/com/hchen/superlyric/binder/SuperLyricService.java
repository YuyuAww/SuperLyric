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
package com.hchen.superlyric.binder;

import android.os.IBinder;
import android.os.RemoteException;

import com.hchen.hooktool.log.AndroidLog;
import com.hchen.superlyricapi.ISuperLyric;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

/**
 * Super Lyric 服务
 *
 * @author 焕晨HChen
 */
public class SuperLyricService extends ISuperLyricDistributor.Stub {
    private static final String TAG = "SuperLyric";
    private static final CopyOnWriteArrayList<ISuperLyric> mISuperLyricList = new CopyOnWriteArrayList<>();
    private final static ConcurrentHashMap<IBinder, ISuperLyric> mIBinder2ISuperLyricMap = new ConcurrentHashMap<>();
    public static final CopyOnWriteArraySet<String> mExemptSet = new CopyOnWriteArraySet<>();
    public static final CopyOnWriteArraySet<String> mSelfControlSet = new CopyOnWriteArraySet<>();

    public void addSuperLyricBinder(IBinder iBinder, ISuperLyric iSuperLyric) {
        try {
            if (mIBinder2ISuperLyricMap.get(iBinder) == null) {
                mISuperLyricList.add(iSuperLyric);
                mIBinder2ISuperLyricMap.put(iBinder, iSuperLyric);
            }
        } catch (Throwable e) {
            AndroidLog.logE(TAG, "[addSuperLyricBinder]: Failed to add binder: " + iSuperLyric, e);
        }
    }

    public void addSelfControlPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        mSelfControlSet.add(packageName);
    }

    public void removeSuperLyricBinder(IBinder iBinder) {
        try {
            if (mIBinder2ISuperLyricMap.get(iBinder) != null) {
                ISuperLyric iSuperLyric = mIBinder2ISuperLyricMap.get(iBinder);
                if (iSuperLyric == null) return;

                mISuperLyricList.removeIf(new Predicate<ISuperLyric>() {
                    @Override
                    public boolean test(ISuperLyric sl) {
                        return Objects.equals(sl, iSuperLyric);
                    }
                });
                mIBinder2ISuperLyricMap.remove(iBinder);
            }
        } catch (Throwable e) {
            AndroidLog.logE(TAG, "[removeSuperLyricBinder]: Failed to remove binder: " + iBinder, e);
        }
    }

    public void removeSelfControlPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        mSelfControlSet.remove(packageName);
    }

    @Override
    public void onStop(SuperLyricData data) throws RemoteException {
        for (ISuperLyric superLyric : mISuperLyricList) {
            try {
                superLyric.onStop(data);
            } catch (Throwable e) {
                try {
                    mISuperLyricList.remove(superLyric);
                } catch (Throwable ignore) {
                }
                AndroidLog.logE(TAG, "[onStop]: Will remove: " + superLyric, e);
            }
        }
    }

    @Override
    public void onSuperLyric(SuperLyricData data) throws RemoteException {
        for (ISuperLyric superLyric : mISuperLyricList) {
            try {
                superLyric.onSuperLyric(data);
            } catch (Throwable e) {
                try {
                    mISuperLyricList.remove(superLyric);
                } catch (Throwable ignore) {
                }
                AndroidLog.logE(TAG, "[onSuperLyric]: Will remove: " + superLyric, e);
            }
        }
    }

    public void addExemptPackage(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) return;

            mExemptSet.add(packageName);
        } catch (Throwable e) {
            AndroidLog.logE(TAG, "[onExempt]: Error!", e);
        }
    }

    public void onDied(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;

        try {
            mExemptSet.remove(packageName); // 死后自动移除豁免
            mSelfControlSet.remove(packageName); // 移除自我控制
            onStop(new SuperLyricData().setPackageName(packageName));
        } catch (Throwable e) {
            AndroidLog.logE(TAG, "App :" + packageName + " is died!", e);
        }
    }
}
