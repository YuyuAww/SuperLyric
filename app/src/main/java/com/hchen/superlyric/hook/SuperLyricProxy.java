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
package com.hchen.superlyric.hook;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;

import com.hchen.collect.Collect;
import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.binder.SuperLyricService;
import com.hchen.superlyric.state.PlayStateListener;
import com.hchen.superlyricapi.ISuperLyric;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/**
 * 代理 Super Lyric 服务
 *
 * @author 焕晨HChen
 */
@Collect(targetPackage = "android", onApplication = false)
public class SuperLyricProxy extends BaseHC {
    private static SuperLyricService mSuperLyricService;

    @Override
    protected void init() {
        hookMethod("com.android.server.am.ActivityManagerService",
            "systemReady",
            Runnable.class /* goingCallback */, "com.android.server.utils.TimingsTraceAndSlog" /* t */,
            new IHook() {
                @Override
                public void after() {
                    if (mSuperLyricService == null) {
                        mSuperLyricService = new SuperLyricService();

                        Context context = (Context) getThisField("mContext");
                        new PlayStateListener(context, mSuperLyricService).start();
                    }

                    logI(TAG, "Super lyric service ready!!");
                }
            }
        );

        Method registerReceiverWithFeatureMethod = null;
        if (existsMethod("com.android.server.am.ActivityManagerService",
            "registerReceiverWithFeature",
            "android.app.IApplicationThread", String.class, String.class, String.class,
            "android.content.IIntentReceiver", IntentFilter.class, String.class, int.class, int.class))
            registerReceiverWithFeatureMethod = findMethod("com.android.server.am.ActivityManagerService",
                "registerReceiverWithFeature",
                "android.app.IApplicationThread" /* caller */, String.class /* callerPackage */,
                String.class /* callerFeatureId */, String.class /* receiverId */,
                "android.content.IIntentReceiver" /* receiver */, IntentFilter.class /* filter */,
                String.class /* permission */, int.class /* userId */, int.class /* flags */);

        else if (existsMethod("com.android.server.am.ActivityManagerService",
            "registerReceiverWithFeature",
            "android.app.IApplicationThread", String.class, String.class, "android.content.IIntentReceiver",
            IntentFilter.class, String.class, int.class, int.class))
            registerReceiverWithFeatureMethod = findMethod("com.android.server.am.ActivityManagerService",
                "registerReceiverWithFeature",
                "android.app.IApplicationThread" /* caller */, String.class /* callerPackage */,
                String.class /* callerFeatureId */, "android.content.IIntentReceiver" /* receiver */,
                IntentFilter.class /* filter */, String.class /* permission */, int.class /* userId */, int.class /* flags */);

        if (registerReceiverWithFeatureMethod == null) {
            logW(TAG,"Failed to get method:[registerReceiverWithFeature], maybe can't use super lyric!!");
            return;
        }

        hook(registerReceiverWithFeatureMethod,
            new IHook() {
                @Override
                public void after() {
                    if (mSuperLyricService == null) return;

                    Intent intent = (Intent) getResult();
                    if (intent == null) return;

                    String callerPackage = (String) getArgs(1);
                    // if (!CollectMap.getAllPackageSet().contains(callerPackage)
                    //     && !SuperLyricService.mExemptSet.contains(callerPackage)
                    // ) return;
                    if (!SuperLyricService.mExemptSet.contains(callerPackage)) return;

                    Bundle bundle = new Bundle();
                    bundle.putBinder("super_lyric_binder", mSuperLyricService);
                    intent.putExtra("super_lyric_info", bundle);
                    setResult(intent);

                    logD(TAG, "Return binder: " + mSuperLyricService);
                }
            }
        );

        Method broadcastIntentWithFeatureMethod = null;
        if (existsMethod("com.android.server.am.ActivityManagerService", "broadcastIntentWithFeature",
            "android.app.IApplicationThread", String.class, Intent.class, String.class, "android.content.IIntentReceiver", int.class,
            String.class, Bundle.class, String[].class, String[].class, String[].class, int.class, Bundle.class, boolean.class, boolean.class, int.class))
            broadcastIntentWithFeatureMethod = findMethod("com.android.server.am.ActivityManagerService",
                "broadcastIntentWithFeature",
                "android.app.IApplicationThread" /* caller */, String.class /* callingFeatureId */, Intent.class /* intent */,
                String.class /* resolvedType */, "android.content.IIntentReceiver" /* resultTo */, int.class /* resultCode */,
                String.class /* resultData */, Bundle.class /* resultExtras */, String[].class /* requiredPermissions */,
                String[].class /* excludedPermissions */, String[].class /* excludedPackages */, int.class /* appOp */,
                Bundle.class /* bOptions */, boolean.class /* serialized */, boolean.class /* sticky */, int.class /* userId */);

        else if (existsMethod("com.android.server.am.ActivityManagerService", "broadcastIntentWithFeature",
            "android.app.IApplicationThread", String.class, Intent.class, String.class, "android.content.IIntentReceiver", int.class,
            String.class, Bundle.class, String[].class, String[].class, int.class, Bundle.class, boolean.class, boolean.class, int.class))
            broadcastIntentWithFeatureMethod = findMethod("com.android.server.am.ActivityManagerService",
                "broadcastIntentWithFeature",
                "android.app.IApplicationThread" /* caller */, String.class /* callingFeatureId */, Intent.class /* intent */,
                String.class /* resolvedType */, "android.content.IIntentReceiver" /* resultTo */, int.class /* resultCode */,
                String.class /* resultData */, Bundle.class /* resultExtras */, String[].class /* requiredPermissions */,
                String[].class /* excludedPermissions */, int.class /* appOp */, Bundle.class /* bOptions */,
                boolean.class /* serialized */, boolean.class /* sticky */, int.class /* userId */);

        else if (existsMethod("com.android.server.am.ActivityManagerService", "broadcastIntentWithFeature",
            "android.app.IApplicationThread", String.class, Intent.class, String.class, "android.content.IIntentReceiver", int.class,
            String.class, Bundle.class, String[].class, int.class, Bundle.class, boolean.class, boolean.class, int.class))
            broadcastIntentWithFeatureMethod = findMethod("com.android.server.am.ActivityManagerService",
                "broadcastIntentWithFeature",
                "android.app.IApplicationThread" /* caller */, String.class /* callingFeatureId */, Intent.class /* intent */,
                String.class /* resolvedType */, "android.content.IIntentReceiver" /* resultTo */, int.class /* resultCode */,
                String.class /* resultData */, Bundle.class /* resultExtras */, String[].class /* requiredPermissions */, int.class /* appOp */,
                Bundle.class /* bOptions */, boolean.class /* serialized */, boolean.class /* sticky */, int.class /* userId */);

        if (broadcastIntentWithFeatureMethod == null) {
            logW(TAG,"Failed to get method:[broadcastIntentWithFeature], maybe can't use super lyric!!");
            return;
        }

        hook(broadcastIntentWithFeatureMethod,
            new IHook() {
                @Override
                public void before() {
                    if (mSuperLyricService == null) return;
                    if (!(getArgs(2) instanceof Intent intent)) return;
                    if (!Objects.equals(intent.getAction(), "Super_Lyric")) return;

                    String addPackage = intent.getStringExtra("super_lyric_add_package");
                    if (addPackage != null) {
                        mSuperLyricService.addExemptPackage(addPackage);
                        return;
                    }

                    Bundle bundle = intent.getExtras();
                    if (bundle == null) return;

                    try {
                        IBinder superLyricBinder = bundle.getBinder("super_lyric_binder");
                        if (superLyricBinder != null) {
                            ISuperLyric iSuperLyric = ISuperLyric.Stub.asInterface(superLyricBinder);
                            mSuperLyricService.addSuperLyricBinder(superLyricBinder, iSuperLyric);
                            if (bundle.getBoolean("super_lyric_self_control", false)) {
                                synchronized (thisObject()) {
                                    String pkg = getPackageName(thisObject(), getArgs(0));
                                    mSuperLyricService.addSelfControlPackage(pkg);

                                    logD(TAG, "Will add self control package name: " + pkg);
                                }
                            }

                            logD(TAG, "Will add binder: " + superLyricBinder + ", super lyric binder: " + iSuperLyric);
                        } else {
                            superLyricBinder = bundle.getBinder("super_lyric_un_binder");
                            if (superLyricBinder != null) {
                                ISuperLyric iSuperLyric = ISuperLyric.Stub.asInterface(superLyricBinder);
                                mSuperLyricService.removeSuperLyricBinder(superLyricBinder);

                                logD(TAG, "Will remove binder: " + superLyricBinder + ", super lyric binder: " + iSuperLyric);
                            }
                            if (bundle.getBoolean("super_lyric_un_self_control", false)) {
                                synchronized (thisObject()) {
                                    String pkg = getPackageName(thisObject(), getArgs(0));
                                    mSuperLyricService.removeSelfControlPackage(pkg);

                                    logD(TAG, "Will remove self control package name: " + pkg);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        logE(TAG, e);
                    }
                }
            }
        );

        hookMethod("com.android.server.am.ActivityManagerService",
            "appDiedLocked",
            "com.android.server.am.ProcessRecord" /* app */, int.class /* pid */,
            "android.app.IApplicationThread" /* thread */, boolean.class /* fromBinderDied */, String.class /* reason */,
            new IHook() {
                @Override
                public void after() {
                    if (mSuperLyricService == null) return;

                    Object app = getArgs(0);
                    boolean isKilled = (boolean) Optional.ofNullable(
                        callMethod(app, "isKilled")
                    ).orElse(true);
                    if (isKilled) {
                        String packageName = (String) getField(
                            getField(
                                app,
                                "info"
                            ),
                            "packageName"
                        );
                        String processName = (String) getField(app, "processName");
                        if (Objects.equals(packageName, processName)) { // 主进程
                            if (
                                // CollectMap.getAllPackageSet().contains(packageName) ||
                                SuperLyricService.mExemptSet.contains(packageName)
                            ) {
                                mSuperLyricService.onDied(packageName);
                                logD(TAG, "App: " + packageName + " is died!!");
                            }
                        }
                    }
                }
            }
        );
    }

    private String getPackageName(Object instance, Object caller) {
        Object callerApp = callMethod(instance, "getRecordForAppLOSP", caller);

        return (String) getField(
            getField(
                callerApp,
                "info"
            ),
            "packageName"
        );
    }
}
