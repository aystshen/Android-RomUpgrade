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
import android.util.Log;

import com.topband.autoupgrade.service.UpdateService;
import com.topband.autoupgrade.util.AppUtils;

import java.lang.reflect.Method;

public class UpdateReceiver extends BroadcastReceiver {
    private final static String TAG = "UpdateReceiver";
    private static boolean isBootCompleted = false;
    private static int mVolumeState = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive, action = " + action);
        Intent serviceIntent;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
//            serviceIntent = new Intent(context, UpdateService.class);
//            serviceIntent.putExtra("command", UpdateService.COMMAND_CHECK_LOCAL_UPDATING);
//            serviceIntent.putExtra("delay", 20000);
//            context.startService(serviceIntent);

            serviceIntent = new Intent(context, UpdateService.class);
            serviceIntent.putExtra("command", UpdateService.COMMAND_CHECK_REMOTE_UPDATING);
            serviceIntent.putExtra("delay", 25000);
            context.startService(serviceIntent);
            Log.d(TAG, "onReceive, Boot completed. To check remote update.");
            isBootCompleted = true;
        } else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            String[] path = {intent.getData().getPath()};
            serviceIntent = new Intent(context, UpdateService.class);
            serviceIntent.putExtra("command", UpdateService.COMMAND_CHECK_LOCAL_UPDATING);
            serviceIntent.putExtra("delay", 5000);
            context.startService(serviceIntent);
            Log.d(TAG, "onReceive, Media is mounted to '" + path[0] + "'. To check local update.");
        } else if ("android.hardware.usb.action.USB_STATE".equals(action)) {
            Bundle extras = intent.getExtras();
            boolean connected = extras.getBoolean("connected");
            boolean configured = extras.getBoolean("configured");
            boolean mtpEnabled = extras.getBoolean("mtp");
            boolean ptpEnabled = extras.getBoolean("ptp");
            // Start MTP service if USB is connected and either the MTP or PTP function is enabled
            if (!connected && mtpEnabled && !configured) {
                serviceIntent = new Intent(context, UpdateService.class);
                serviceIntent.putExtra("command", UpdateService.COMMAND_CHECK_LOCAL_UPDATING);
                serviceIntent.putExtra("delay", 5000);
                context.startService(serviceIntent);
                Log.d(TAG, "onReceive, Udisk is connected. To check local update.");
            }
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (AppUtils.isConnNetWork(context)) {
                serviceIntent = new Intent(context, UpdateService.class);
                serviceIntent.putExtra("command", UpdateService.COMMAND_CHECK_REMOTE_UPDATING);
                serviceIntent.putExtra("delay", 5000);
                context.startService(serviceIntent);
                Log.d(TAG, "onReceive, Network is connected. To check remote update.");
            }
        } else if(action.equals("android.os.storage.action.VOLUME_STATE_CHANGED")){
            int state = intent.getIntExtra("android.os.storage.extra.VOLUME_STATE", 0);
            if (mVolumeState == 0 && state == 2) {
                serviceIntent = new Intent(context, UpdateService.class);
                serviceIntent.putExtra("command", UpdateService.COMMAND_CHECK_LOCAL_UPDATING);
                serviceIntent.putExtra("delay", 5000);
                context.startService(serviceIntent);
                Log.d(TAG, "onReceive, Volume is mounted. To check local update.");
            }
            mVolumeState = state;
        }
    }
}


