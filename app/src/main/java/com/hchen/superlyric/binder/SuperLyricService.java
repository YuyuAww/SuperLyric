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

import android.os.RemoteException;

import com.hchen.hooktool.log.AndroidLog;
import com.hchen.superlyricapi.ISuperLyric;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SuperLyricService extends ISuperLyricDistributor.Stub {
    private static final String TAG = "SuperLyric";
    private static final List<ISuperLyric> mISuperLyricList = new ArrayList<>();

    public void addSuperLyricBinder(ISuperLyric iSuperLyric) {
        mISuperLyricList.add(iSuperLyric);
    }

    @Override
    public void onStop() throws RemoteException {
        Iterator<ISuperLyric> iterator = mISuperLyricList.iterator();
        while (iterator.hasNext()) {
            ISuperLyric superLyric = iterator.next();
            try {
                superLyric.onStop();
            } catch (RemoteException e) {
                iterator.remove();
                AndroidLog.logW(TAG, "[onStop]: Will remove: " + superLyric, e);
            }
        }
    }

    @Override
    public void onSuperLyric(SuperLyricData data) throws RemoteException {
        Iterator<ISuperLyric> iterator = mISuperLyricList.iterator();
        while (iterator.hasNext()) {
            ISuperLyric superLyric = iterator.next();
            try {
                superLyric.onSuperLyric(data);
            } catch (RemoteException e) {
                iterator.remove();
                AndroidLog.logW(TAG, "[onSuperLyric]: Will remove: " + superLyric, e);
            }
        }
    }
}
