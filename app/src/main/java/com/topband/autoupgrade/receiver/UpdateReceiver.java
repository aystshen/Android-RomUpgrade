/*
 **
 ** Copyright 2007, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package com.topband.autoupgrade.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.baidu.otasdk.ota.Constants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.topband.autoupgrade.App;
import com.topband.autoupgrade.baidu.NewVersionBean;
import com.topband.autoupgrade.service.UpdateService;
import com.topband.autoupgrade.util.AppUtils;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Listen to the broadcast, start the UpdateService
 * to perform the upgrade action.
 * <p>
 * Intent.ACTION_BOOT_COMPLETED
 * ConnectivityManager.CONNECTIVITY_ACTION
 * Intent.ACTION_MEDIA_MOUNTED
 * android.hardware.usb.action.USB_STATE
 * android.os.storage.action.VOLUME_STATE_CHANGED
 * Constants.BROADCAST_NEWVERSION
 * <p>
 * Created by ayst.shen@foxmail.com on 2018/11/6.
 */
public class UpdateReceiver extends BroadcastReceiver {
    private final static String TAG = "UpdateReceiver";
    private static int mVolumeState = -1;
    private static Gson sGson;

    public UpdateReceiver() {
        sGson = new Gson();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "onReceive, action = " + action);

        if (TextUtils.equals(Intent.ACTION_BOOT_COMPLETED, action)) {
            // Check local and remote upgrades after booting.
            Log.i(TAG, "onReceive, Boot completed. To check local and remote update.");
            context.startService(buildIntent(context,
                    UpdateService.COMMAND_CHECK_LOCAL_UPDATING,
                    5000));

            context.startService(buildIntent(context,
                    UpdateService.COMMAND_CHECK_REMOTE_UPDATING,
                    25000));

        } else if (TextUtils.equals(ConnectivityManager.CONNECTIVITY_ACTION, action)) {
            // Check remote upgrade after connecting to the network.
            if (AppUtils.isConnNetWork(context)) {
                Log.i(TAG, "onReceive, Network is connected. To check remote update.");
                context.startService(buildIntent(context,
                        UpdateService.COMMAND_CHECK_REMOTE_UPDATING,
                        5000));
            }

        } else if (TextUtils.equals(Intent.ACTION_MEDIA_MOUNTED, action)) {
            // U disk insert check local upgrade.
            String[] path = {intent.getData().getPath()};
            Log.i(TAG, "onReceive, Media is mounted to '"
                    + path[0] + "'. To check local update.");
            context.startService(buildIntent(context,
                    UpdateService.COMMAND_CHECK_LOCAL_UPDATING,
                    5000));

        } else if (TextUtils.equals("android.hardware.usb.action.USB_STATE", action)) {
            // U disk insert check local upgrade.
            Bundle extras = intent.getExtras();
            boolean connected = extras.getBoolean("connected");
            boolean configured = extras.getBoolean("configured");
            boolean mtpEnabled = extras.getBoolean("mtp");
            boolean ptpEnabled = extras.getBoolean("ptp");

            // Start MTP service if USB is connected and either the MTP or PTP function is enabled
            if (!connected && mtpEnabled && !configured) {
                Log.i(TAG, "onReceive, u disk is connected. To check local update.");
                context.startService(buildIntent(context,
                        UpdateService.COMMAND_CHECK_LOCAL_UPDATING,
                        5000));
            }

        } else if (TextUtils.equals("android.os.storage.action.VOLUME_STATE_CHANGED", action)) {
            // U disk insert check local upgrade.
            int state = intent.getIntExtra("android.os.storage.extra.VOLUME_STATE", 0);
            if (mVolumeState == 0 && state == 2) {
                Log.i(TAG, "onReceive, Volume is mounted. To check local update.");
                context.startService(buildIntent(context,
                        UpdateService.COMMAND_CHECK_LOCAL_UPDATING,
                        5000));
            }
            mVolumeState = state;

        } else if (TextUtils.equals(Constants.BROADCAST_NEWVERSION, action)) {
            // Baidu otasdk automatically checks for upgrade notifications.
            String pid = intent.getStringExtra(Constants.BROADCAST_KEY_PID);
            if (TextUtils.equals(App.getProductId(), pid)) {
                String infos = intent.getStringExtra(Constants.BROADCAST_KEY_INFOS);

                Log.i(TAG, "onReceive, new version infos=" + infos);
                if (!TextUtils.isEmpty(infos)) {
                    Type type = new TypeToken<List<NewVersionBean>>() {
                    }.getType();
                    List<NewVersionBean> newVersions = sGson.fromJson(infos, type);

                    if (null != newVersions && !newVersions.isEmpty()) {
                        Intent serviceIntent = buildIntent(context, UpdateService.COMMAND_NEW_VERSION, 0);

                        Bundle bundle = new Bundle();
                        bundle.putSerializable("new_version", newVersions.get(0));
                        serviceIntent.putExtra("bundle", bundle);

                        context.startService(serviceIntent);
                    }
                }
            }
        }
    }

    private Intent buildIntent(Context context, int command, int delay) {
        Intent intent = new Intent(context, UpdateService.class);
        intent.putExtra("command", command);
        intent.putExtra("delay", delay);
        return intent;
    }
}


