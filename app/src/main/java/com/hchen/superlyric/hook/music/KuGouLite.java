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

import android.content.Context;
import android.content.Intent;

import com.hchen.collect.Collect;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.base.BaseLyric;

import java.util.Objects;

@Collect(targetPackage = "com.kugou.android.lite", onApplication = true)
public class KuGouLite extends BaseLyric {

    @Override
    protected void init() {
        onTinker();
        openBluetoothA2dp();
    }

    @Override
    protected void onApplicationAfter(Context context) {
        super.onApplicationAfter(context);

        try {
            long code = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
            if (!Objects.equals(lpparam.processName, "com.kugou.android.lite.support")) {
                if (code <= 10935) {
                    hookLocalBroadcast("android.support.v4.content.LocalBroadcastManager");
                } else {
                    hookLocalBroadcast("androidx.localbroadcastmanager.content.LocalBroadcastManager");
                }
                MockFlyme.mock();
                fixProbabilityCollapse();
            }
        } catch (Throwable e) {
            logE(TAG, e);
        }
    }

    private void hookLocalBroadcast(String clazz) {
        hookAllMethod(clazz,
            "sendBroadcast",
            new IHook() {
                @Override
                public void before() {
                    Intent intent = (Intent) getArgs(0);
                    if (intent == null) return;

                    String action = intent.getAction();
                    String message = intent.getStringExtra("lyric");
                    if (message == null) return;

                    if (Objects.equals(action, "com.kugou.android.update_meizu_lyric")) {
                        sendLyric(message);
                    }
                }
            }
        );
    }

    private void fixProbabilityCollapse() {
        hookAllMethod("com.kugou.framework.hack.ServiceFetcherHacker$FetcherImpl",
            "createServiceObject",
            new IHook() {
                @Override
                public void after() {
                    String mServiceName = (String) getThisField("serviceName");
                    if (mServiceName == null) return;

                    if (mServiceName.equals(Context.WIFI_SERVICE)) {
                        if (getThrowable() != null) {
                            setThrowable(null);
                            setResult(null);
                        }
                    }
                }
            }
        );
    }
}
