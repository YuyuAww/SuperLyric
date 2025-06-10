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
import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.binder.SuperLyricControllerService;
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
public class SuperLyricProxy extends HCBase {
    private static SuperLyricService mSuperLyricService;
    private static SuperLyricControllerService mSuperLyricControllerService;

    @Override
    protected void init() {
        Method systemReadyMethod;
        if (existsMethod("com.android.server.am.ActivityManagerService",
            "systemReady",
            Runnable.class, "com.android.server.utils.TimingsTraceAndSlog")) {
            systemReadyMethod = findMethod("com.android.server.am.ActivityManagerService",
                "systemReady",
                Runnable.class /* goingCallback */, "com.android.server.utils.TimingsTraceAndSlog" /* t */);
        } else {
            systemReadyMethod = findMethodPro("com.android.server.am.ActivityManagerService")
                .withParamCount(2)
                .withMethodName("systemReady")
                .single()
                .get();
        }

        if (systemReadyMethod == null) {
            logW(TAG, "Failed to get method:[systemReady], maybe can't use super lyric!!");
            return;
        }

        hook(systemReadyMethod,
            new IHook() {
                @Override
                public void after() {
                    if (mSuperLyricService == null) {
                        mSuperLyricService = new SuperLyricService();
                        mSuperLyricControllerService = new SuperLyricControllerService(mSuperLyricService);

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
            logW(TAG, "Failed to get method:[registerReceiverWithFeature], maybe can't use super lyric!!");
            return;
        }

        hook(registerReceiverWithFeatureMethod,
            new IHook() {
                @Override
                public void after() {
                    if (mSuperLyricService == null) return;

                    Intent intent = (Intent) getResult();
                    if (intent == null) return;

                    String callerPackage = (String) getArg(1);
                    // if (!CollectMap.getAllPackageSet().contains(callerPackage)
                    //     && !SuperLyricService.mExemptSet.contains(callerPackage)
                    // ) return;
                    if (!SuperLyricService.mExemptSet.contains(callerPackage) &&
                        !SuperLyricControllerService.mFinalExemptSet.contains(callerPackage))
                        return;

                    Bundle bundle = new Bundle();
                    bundle.putBinder("super_lyric_binder", mSuperLyricService);
                    bundle.putBinder("super_lyric_controller", mSuperLyricControllerService.getBinder());
                    intent.putExtra("super_lyric_info", bundle);
                    setResult(intent);

                    logD(TAG, "Return binder: " + mSuperLyricService + ", caller package name: " + callerPackage);
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
            logW(TAG, "Failed to get method:[broadcastIntentWithFeature], maybe can't use super lyric!!");
            return;
        }

        hook(broadcastIntentWithFeatureMethod,
            new IHook() {
                @Override
                public void before() {
                    if (mSuperLyricService == null) return;
                    if (!(getArg(2) instanceof Intent intent)) return;
                    if (!Objects.equals(intent.getAction(), "Super_Lyric")) return;

                    try {
                        String callerPackage = "unknown";
                        try {
                            Object caller = getArg(0);
                            Object callerApp = callThisMethod("getRecordForAppLOSP", caller);
                            callerPackage = (String) getField(getField(callerApp, "info"), "packageName");
                        } catch (Throwable ignore) {
                        }

                        String addPackage = intent.getStringExtra("super_lyric_add_package");
                        if (addPackage != null) {
                            mSuperLyricService.addExemptPackage(addPackage);
                            logD(TAG, "Will add package name: " + addPackage + ", caller package name: " + callerPackage);
                            return;
                        }

                        String unController = intent.getStringExtra("super_lyric_un_controller");
                        if (unController != null && !unController.isEmpty()) {
                            if (mSuperLyricControllerService != null) {
                                mSuperLyricControllerService.removeSuperLyricStubIfNeed(unController);
                                logD(TAG, "Will un_controller super lyric: " + unController + ", caller package name: " + callerPackage);
                            }
                            return;
                        }

                        String selfControl = intent.getStringExtra("super_lyric_self_control_package");
                        if (selfControl != null && !selfControl.isEmpty()) {
                            mSuperLyricService.addSelfControlPackage(selfControl);
                            logD(TAG, "Will add self control package name: " + selfControl + ", caller package name: " + callerPackage);
                            return;
                        }

                        String unSelfControl = intent.getStringExtra("super_lyric_un_self_control_package");
                        if (unSelfControl != null && !unSelfControl.isEmpty()) {
                            mSuperLyricService.removeSelfControlPackage(unSelfControl);
                            logD(TAG, "Will remove self control package name: " + unSelfControl + ", caller package name: " + callerPackage);
                            return;
                        }

                        Bundle bundle = intent.getExtras();
                        if (bundle == null) return;

                        IBinder superLyricBinder = bundle.getBinder("super_lyric_binder");
                        if (superLyricBinder != null) {
                            ISuperLyric iSuperLyric = ISuperLyric.Stub.asInterface(superLyricBinder);
                            mSuperLyricService.addSuperLyricBinder(superLyricBinder, iSuperLyric);
                            logD(TAG, "Will add binder: " + superLyricBinder + ", super lyric binder: " + iSuperLyric + ", caller package name: " + callerPackage);
                            return;
                        }

                        superLyricBinder = bundle.getBinder("super_lyric_un_binder");
                        if (superLyricBinder != null) {
                            ISuperLyric iSuperLyric = ISuperLyric.Stub.asInterface(superLyricBinder);
                            mSuperLyricService.removeSuperLyricBinder(superLyricBinder);
                            logD(TAG, "Will remove binder: " + superLyricBinder + ", super lyric binder: " + iSuperLyric + ", caller package name: " + callerPackage);
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
                /** @noinspection SimplifiableConditionalExpression*/
                @Override
                public void after() {
                    if (mSuperLyricService == null) return;

                    Object app = getArg(0);
                    boolean isKilled = existsMethod(app.getClass(), "isKilled") ?
                        (boolean) Optional.ofNullable(
                            callMethod(app, "isKilled")
                        ).orElse(true) :
                        existsField(app.getClass(), "mKilled") ?
                            (boolean) Optional.ofNullable(
                                getField(app, "mKilled")
                            ).orElse(true) :
                            true;
                    if (isKilled) {
                        String packageName = (String) getField(getField(app, "info"), "packageName");
                        String processName = (String) getField(app, "processName");
                        if (Objects.equals(packageName, processName)) { // 主进程
                            if (
                                // CollectMap.getAllPackageSet().contains(packageName) ||
                                SuperLyricService.mExemptSet.contains(packageName) ||
                                    SuperLyricControllerService.mFinalExemptSet.contains(packageName)
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
}
