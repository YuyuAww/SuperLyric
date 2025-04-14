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

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.hchen.hooktool.log.AndroidLog;
import com.hchen.superlyricapi.ISuperLyric;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 控制器注册服务
 *
 * @author 焕晨HChen
 */
public class SuperLyricControllerService {
    private static final String TAG = "SuperLyric";
    private static SuperLyricService mSuperLyricService;
    public static final CopyOnWriteArraySet<String> mFinalExemptSet = new CopyOnWriteArraySet<>();
    private static final Messenger mMessengerService = new Messenger(new ControllerHandler(Looper.getMainLooper()));
    private static final ConcurrentHashMap<String, ISuperLyric.Stub> mPackageName2ISuperLyricStubMap = new ConcurrentHashMap<>();

    public SuperLyricControllerService(SuperLyricService superLyricService) {
        mSuperLyricService = superLyricService;
        mFinalExemptSet.add("com.android.systemui");
    }

    private static class ControllerHandler extends Handler {
        public ControllerHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Bundle obj = msg.getData();
            if (obj == null) return;

            String packageName = obj.getString("super_lyric_controller_package");
            if (packageName == null || packageName.isEmpty()) return;
            if (!mFinalExemptSet.contains(packageName)) return;

            ISuperLyric.Stub superLyric = null;
            if (mPackageName2ISuperLyricStubMap.containsKey(packageName)) {
                superLyric = mPackageName2ISuperLyricStubMap.get(packageName);
                if (superLyric == null)
                    mPackageName2ISuperLyricStubMap.remove(packageName);
                else if (!superLyric.isBinderAlive()) {
                    mPackageName2ISuperLyricStubMap.remove(packageName);
                    superLyric = null;
                }
            }

            Messenger client = msg.replyTo;
            if (client == null) return;

            try {
                if (superLyric == null) {
                    superLyric = createSuperLyricStub();
                    mPackageName2ISuperLyricStubMap.put(packageName, superLyric);
                }
                Message message = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putBinder("reply", superLyric);
                message.setData(bundle);
                client.send(message);

                AndroidLog.logD(TAG, "Callback client: " + client + ", controller: " + superLyric + ", packageName: " + packageName);
            } catch (RemoteException e) {
                AndroidLog.logE(TAG, "[SuperLyricControllerService]: Failed to send binder message!!", e);
            }
        }
    }

    private static ISuperLyric.Stub createSuperLyricStub() {
        return new ISuperLyric.Stub() {
            @Override
            public void onStop(SuperLyricData superLyricData) throws RemoteException {
                if (!mPackageName2ISuperLyricStubMap.containsValue(this)) return;

                try {
                    mSuperLyricService.onStop(superLyricData);
                } catch (Throwable e) {
                    AndroidLog.logE(TAG, "[onStop]: Error!!", e);
                }
            }

            @Override
            public void onSuperLyric(SuperLyricData superLyricData) throws RemoteException {
                if (!mPackageName2ISuperLyricStubMap.containsValue(this)) return;

                try {
                    mSuperLyricService.onSuperLyric(superLyricData);
                } catch (Throwable e) {
                    AndroidLog.logE(TAG, "[onSuperLyric]: Error!!", e);
                }
            }
        };
    }

    public void removeSuperLyricStubIfNeed(String packageName) {
        mPackageName2ISuperLyricStubMap.remove(packageName);
    }

    @NonNull
    public IBinder getBinder() {
        return mMessengerService.getBinder();
    }
}
