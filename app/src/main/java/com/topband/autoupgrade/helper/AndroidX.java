package com.topband.autoupgrade.helper;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * The AndroidX service is docked mainly to
 * disable the Watchdog function during the
 * upgrade process.
 */
public class AndroidX {
    private static final String TAG = "AndroidX";

    private static final String ANDROIDX_PACKAGE_NAME = "com.ayst.androidx";
    private static final String RECEIVER_PERMISSION = "com.ayst.androidx.permission.SELF_BROADCAST";

    private static final String ACTION_RUN_ALL = "com.topband.androidx.ACTION_RUN_ALL";
    private static final String ACTION_4G_KEEP_LIVE = "com.topband.androidx.ACTION_4G_KEEP_LIVE";
    private static final String ACTION_WATCHDOG = "com.topband.androidx.ACTION_WATCHDOG";

    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_ACTION_OPEN = "open";
    private static final String EXTRA_ACTION_CLOSE = "close";
    private static final String EXTRA_ACTION_CONFIG = "config";

    private Context mContext;
    private Mcu mMcu;

    public AndroidX(Context context) {
        mContext = context;
        mMcu = new Mcu(context);
    }

    /**
     * Toggle 4G keep-alive service
     * @param on on/off
     */
    public void toggle4GKeepLive(boolean on) {
        Intent intent = new Intent();
        intent.setAction(ACTION_4G_KEEP_LIVE);
        intent.putExtra(EXTRA_ACTION, on ? EXTRA_ACTION_OPEN : EXTRA_ACTION_CLOSE);
        intent.setPackage(ANDROIDX_PACKAGE_NAME);
        mContext.sendBroadcast(intent, RECEIVER_PERMISSION);
    }

    /**
     * Toggle watchdog service
     * @param on on/off
     */
    public void toggleWatchdog(boolean on) {
        Intent intent = new Intent();
        intent.setAction(ACTION_WATCHDOG);
        intent.putExtra(EXTRA_ACTION, on ? EXTRA_ACTION_OPEN : EXTRA_ACTION_CLOSE);
        intent.setPackage(ANDROIDX_PACKAGE_NAME);
        mContext.sendBroadcast(intent, RECEIVER_PERMISSION);
    }

    /**
     * Set watchdog timeout
     * @param timeout watchdog timeout
     */
    public void setWatchdogTimeout(int timeout) {
        Intent intent = new Intent();
        intent.setAction(ACTION_WATCHDOG);
        intent.putExtra(EXTRA_ACTION, EXTRA_ACTION_CONFIG);
        Bundle bundle = new Bundle();
        bundle.putInt("timeout", timeout);
        intent.putExtras(bundle);
        intent.setPackage(ANDROIDX_PACKAGE_NAME);
        mContext.sendBroadcast(intent, RECEIVER_PERMISSION);
    }

    /**
     * The watchdog is open
     * @return on/off
     */
    public boolean watchdogIsOpen() {
        return mMcu.watchdogIsOpen();
    }
}
