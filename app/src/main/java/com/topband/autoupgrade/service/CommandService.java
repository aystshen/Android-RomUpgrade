package com.topband.autoupgrade.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

public class CommandService extends Service {
    public static final String TAG = "CommandService";

    public static final String ACTION_COMMAND = "com.android.action.COMMAND";

    public static final int COMMAND_NULL = 0;
    public static final int COMMAND_REBOOT = 1001;
    public static final int COMMAND_START_APP = 1002;

    public static final String EXTRA_PACKAGE_NAME = "package_name";

    public static final int DEFAULT_DELAY_TIME = 3000;

    private WorkHandler mWorkHandler;
    private Handler mMainHandler;

    public CommandService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread workThread = new HandlerThread("CommandService: workThread");
        workThread.start();
        mWorkHandler = new WorkHandler(workThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand...");

        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        int command = intent.getIntExtra("command", COMMAND_NULL);
        int delayTime = intent.getIntExtra("delay", DEFAULT_DELAY_TIME);
        Bundle bundle = intent.getExtras();

        Log.d(TAG, "onStartCommand, command=" + command + " delayTime=" + delayTime);
        if (command == COMMAND_NULL) {
            return Service.START_NOT_STICKY;
        }

        Message msg = new Message();
        msg.what = command;
        msg.obj = bundle;
        mWorkHandler.sendMessageDelayed(msg, delayTime);

        return Service.START_REDELIVER_INTENT;
    }

    /**
     * WorkHandler
     */
    private class WorkHandler extends Handler {
        WorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            String path = "";

            switch (msg.what) {
                case COMMAND_REBOOT:
                    reboot();
                    break;

                case COMMAND_START_APP:
                    if (null != msg.obj) {
                        Bundle bundle = (Bundle) msg.obj;
                        startAppWithPackageName(bundle.getString(EXTRA_PACKAGE_NAME));
                    } else {
                        Log.e(TAG, "WorkHandler, COMMAND_START_APP param is null");
                    }
                    break;
            }
        }
    }

    private void reboot() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (null != powerManager) {
            powerManager.reboot(null);
        }
    }

    private void startAppWithPackageName(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            Log.e(TAG, "startAppWithPackageName, packageName is null");
            return;
        }

        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageInfo == null) {
            return;
        }

        Intent resolveIntent = new Intent(Intent.ACTION_MAIN, null);
        resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resolveIntent.setPackage(packageInfo.packageName);

        List<ResolveInfo> resolveInfoList = getPackageManager()
                .queryIntentActivities(resolveIntent, 0);

        ResolveInfo resolveinfo = resolveInfoList.iterator().next();
        if (resolveinfo != null) {
            String pkgName = resolveinfo.activityInfo.packageName;
            String className = resolveinfo.activityInfo.name;
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            ComponentName cn = new ComponentName(pkgName, className);

            intent.setComponent(cn);
            startActivity(intent);
        }
    }
}
