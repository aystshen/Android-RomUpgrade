/*
 * Copyright(c) 2020 Bob Shen <ayst.shen@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ayst.romupgrade.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.baidu.otasdk.ota.Constants;
import com.ayst.romupgrade.App;
import com.ayst.romupgrade.service.UpdateService;

/**
 * Listen to the broadcast, start the UpdateService
 * to perform the upgrade action.
 * <p>
 * Intent.ACTION_BOOT_COMPLETED
 * ConnectivityManager.CONNECTIVITY_ACTION
 * Intent.ACTION_MEDIA_MOUNTED
 * UsbManager.ACTION_USB_STATE
 * VolumeInfo.ACTION_VOLUME_STATE_CHANGED
 * Constants.BROADCAST_NEWVERSION
 * <p>
 * Created by ayst.shen@foxmail.com on 2018/11/6.
 */
public class UpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "UpdateReceiver";

    private static final String ACTION_START = "com.ayst.romupgrade.action.START";

    private static int sVolumeState = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "onReceive, action = " + action);

        if (TextUtils.equals(Intent.ACTION_BOOT_COMPLETED, action)
            || TextUtils.equals(ACTION_START, action)) {
            /*
             Check local and remote upgrade after power-on.
              */
            Log.i(TAG, "onReceive, Boot completed. To check local and remote update.");
            context.startService(buildIntent(context,
                    UpdateService.COMMAND_INITIAL,
                    5000));

            context.startService(buildIntent(context,
                    UpdateService.COMMAND_CHECK_LOCAL_UPDATE,
                    10000));

//            context.startService(buildIntent(context,
//                    UpdateService.COMMAND_CHECK_REMOTE_UPDATE,
//                    25000));

        } else if (TextUtils.equals(Intent.ACTION_MEDIA_MOUNTED, action)) {
            /*
             U-disk insert check local upgrade.
              */
            Log.i(TAG, "onReceive, Media is mounted. To check local update.");
            context.startService(buildIntent(context,
                    UpdateService.COMMAND_CHECK_LOCAL_UPDATE,
                    5000));

        } else if (TextUtils.equals(UsbManager.ACTION_USB_STATE, action)) {
            /*
             U-disk insert check local upgrade.
              */
            Bundle extras = intent.getExtras();
            if (null != extras) {
                boolean connected = extras.getBoolean(UsbManager.USB_CONNECTED);
                boolean configured = extras.getBoolean(UsbManager.USB_CONFIGURED);
                boolean mtpEnabled = extras.getBoolean(UsbManager.USB_FUNCTION_MTP);
                boolean ptpEnabled = extras.getBoolean(UsbManager.USB_FUNCTION_PTP);

                if (!connected && mtpEnabled && !configured) {
                    Log.i(TAG, "onReceive, mtp is enabled. To check local update.");
                    context.startService(buildIntent(context,
                            UpdateService.COMMAND_CHECK_LOCAL_UPDATE,
                            5000));
                }
            }

        } else if (TextUtils.equals(VolumeInfo.ACTION_VOLUME_STATE_CHANGED, action)) {
            /*
             U-disk insert check local upgrade.
              */
            int state = intent.getIntExtra(VolumeInfo.EXTRA_VOLUME_STATE, VolumeInfo.STATE_UNMOUNTED);
            if (sVolumeState == VolumeInfo.STATE_UNMOUNTED && state == VolumeInfo.STATE_MOUNTED) {
                Log.i(TAG, "onReceive, Volume is mounted. To check local update.");
                context.startService(buildIntent(context,
                        UpdateService.COMMAND_CHECK_LOCAL_UPDATE,
                        5000));
            }
            sVolumeState = state;

        } else if (TextUtils.equals(Constants.BROADCAST_NEWVERSION, action)) {
            /*
             Baidu otasdk automatically checks for upgrade notifications.
              */
            String pid = intent.getStringExtra(Constants.BROADCAST_KEY_PID);
            if (TextUtils.equals(App.getProductId(), pid)) {
                String infos = intent.getStringExtra(Constants.BROADCAST_KEY_INFOS);

                Log.i(TAG, "onReceive, new version infos=" + infos);
                if (!TextUtils.isEmpty(infos)) {
                    Intent serviceIntent = buildIntent(context, UpdateService.COMMAND_NEW_VERSION, 0);
                    Bundle bundle = new Bundle();
                    bundle.putString("infos", infos);
                    serviceIntent.putExtra("bundle", bundle);
                    context.startService(serviceIntent);
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

    public class VolumeInfo {
        /**
         * Unmounted
         */
        public static final int STATE_UNMOUNTED = 0;

        /**
         * Checking
         */
        public static final int STATE_CHECKING = 1;

        /**
         * Mounted
         */
        public static final int STATE_MOUNTED = 2;

        /**
         * Read only
         */
        public static final int STATE_MOUNTED_READ_ONLY = 3;

        /**
         * Formatting
         */
        public static final int STATE_FORMATTING = 4;

        /**
         * Ejecting
         */
        public static final int STATE_EJECTING = 5;
        /**
         * Not mountable
         */
        public static final int STATE_UNMOUNTABLE = 6;

        /**
         * Removed
         */
        public static final int STATE_REMOVED = 7;

        /**
         * Remove fail
         */
        public static final int STATE_BAD_REMOVAL = 8;

        /**
         * Volume state changed broadcast action.
         */
        public static final String ACTION_VOLUME_STATE_CHANGED =
                "android.os.storage.action.VOLUME_STATE_CHANGED";

        public static final String EXTRA_VOLUME_ID =
                "android.os.storage.extra.VOLUME_ID";

        public static final String EXTRA_VOLUME_STATE =
                "android.os.storage.extra.VOLUME_STATE";
    }

    public class UsbManager {
        /**
         * Broadcast Action:  A sticky broadcast for USB state change events when in device mode.
         */
        public static final String ACTION_USB_STATE =
                "android.hardware.usb.action.USB_STATE";

        /**
         * Boolean extra indicating whether USB is connected or disconnected.
         * Used in extras for the {@link #ACTION_USB_STATE} broadcast.
         * <p>
         * {@hide}
         */
        public static final String USB_CONNECTED = "connected";

        /**
         * Boolean extra indicating whether USB is configured.
         * Used in extras for the {@link #ACTION_USB_STATE} broadcast.
         */
        public static final String USB_CONFIGURED = "configured";

        /**
         * Name of the MTP USB function.
         * Used in extras for the {@link #ACTION_USB_STATE} broadcast
         */
        public static final String USB_FUNCTION_MTP = "mtp";

        /**
         * Name of the PTP USB function.
         * Used in extras for the {@link #ACTION_USB_STATE} broadcast
         */
        public static final String USB_FUNCTION_PTP = "ptp";
    }
}


