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
import com.hchen.collect.CollectMap;
import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.binder.SuperLyricService;
import com.hchen.superlyric.state.PlayStateListener;
import com.hchen.superlyricapi.ISuperLyric;

import java.util.Objects;

@Collect(targetPackage = "android", onApplication = false)
public class SuperLyricProxy extends BaseHC {
    private static SuperLyricService mSuperLyricService;

    @Override
    protected void init() {
        hookMethod("com.android.server.am.ActivityManagerService",
            "systemReady",
            Runnable.class, "com.android.server.utils.TimingsTraceAndSlog",
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

        hookMethod("com.android.server.am.ActivityManagerService",
            "registerReceiverWithFeature",
            "android.app.IApplicationThread" /* caller */, String.class /* callerPackage */,
            String.class /* callerFeatureId */, String.class /* receiverId */,
            "android.content.IIntentReceiver" /* receiver */, IntentFilter.class /* filter */,
            String.class /* permission */, int.class /* userId */, int.class /* flags */,
            new IHook() {
                @Override
                public void after() {
                    if (mSuperLyricService == null) return;

                    Intent intent = (Intent) getResult();
                    if (intent == null) return;

                    String callerPackage = (String) getArgs(1);
                    if (!CollectMap.getAllPackageSet().contains(callerPackage)
                        && !SuperLyricService.mExemptSet.contains(callerPackage)
                    ) return;

                    Bundle bundle = new Bundle();
                    bundle.putBinder("super_lyric_binder", mSuperLyricService);
                    intent.putExtra("super_lyric_info", bundle);
                    setResult(intent);

                    logD(TAG, "Return binder: " + mSuperLyricService);
                }
            }
        );

        hookMethod("com.android.server.am.ActivityManagerService",
            "broadcastIntentWithFeature",
            "android.app.IApplicationThread" /* caller */, String.class /* callingFeatureId */, Intent.class /* intent */,
            String.class /* resolvedType */, "android.content.IIntentReceiver" /* resultTo */, int.class /* resultCode */,
            String.class /* resultData */, Bundle.class /* resultExtras */, String[].class /* requiredPermissions */,
            String[].class /* excludedPermissions */, String[].class /* excludedPackages */, int.class /* appOp */,
            Bundle.class /* bOptions */, boolean.class /* serialized */, boolean.class /* sticky */, int.class /* userId */,
            new IHook() {
                @Override
                public void before() {
                    if (!(getArgs(2) instanceof Intent intent)) return;
                    if (!Objects.equals(intent.getAction(), "Super_Lyric")) return;

                    Bundle bundle = intent.getExtras();
                    if (bundle == null) return;

                    IBinder superLyricBinder = bundle.getBinder("super_lyric_binder");
                    if (superLyricBinder == null) return;

                    try {
                        ISuperLyric iSuperLyric = ISuperLyric.Stub.asInterface(superLyricBinder);
                        addSuperLyricBinder(iSuperLyric);

                        logD(TAG, "Will add binder: " + iSuperLyric);
                    } catch (Throwable e) {
                        logE(TAG, e);
                    }
                }
            }
        );
    }

    private void addSuperLyricBinder(ISuperLyric iSuperLyric) {
        mSuperLyricService.addSuperLyricBinder(iSuperLyric);
    }
}
