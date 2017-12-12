package com.topband.autoupgrade.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RecoverySystem;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.topband.autoupgrade.util.AppUtils;

public class UpdateService extends Service {
    private static final String TAG = "UpdateService";

    public static final String EXTRA_PACKAGE_PATH = "com.topband.autoupgrade.extra.PACKAGE_PATH";

    public static final int COMMAND_NULL = 0;
    public static final int COMMAND_CHECK_LOCAL_UPDATING = 1;
    public static final int COMMAND_CHECK_REMOTE_UPDATING = 2;
    public static final int COMMAND_VERIFY_UPDATE_PACKAGE = 3;
    public static final int COMMAND_DELETE_UPDATE_PACKAGE = 4;

    private static final String COMMAND_FLAG_SUCCESS = "success";
    private static final String COMMAND_FLAG_UPDATING = "updating";

    public static final int UPDATE_SUCCESS = 1;
    public static final int UPDATE_FAILED = 2;

    public static final String DATA_ROOT = "/data/media/0";
    public static final String FLASH_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    public static final String SDCARD_ROOT = "/mnt/external_sd";
    public static final String USB_ROOT = "/mnt/usb_storage";
    public static final String CACHE_ROOT = Environment.getDownloadCacheDirectory().getAbsolutePath();
    private static final String[] PACKAGE_FILE_DIRS = {
            DATA_ROOT + "/",
            FLASH_ROOT + "/",
            SDCARD_ROOT + "/",
            USB_ROOT + "/",
    };

    private static File RECOVERY_DIR = new File("/cache/recovery");
    private static File UPDATE_FLAG_FILE = new File(RECOVERY_DIR, "last_flag");

    private Context mContext;
    private volatile boolean mIsFirstStartUp = true;

    private String mLastUpdatePath;
    private WorkHandler mWorkHandler;
    private Handler mMainHandler;

    public static String sOtaPackageName = "update.zip";
    private static volatile boolean sWorkHandleLocked = false;
    private static volatile boolean sIsNeedDeletePackage = false;

    public static URI mRemoteURI = null;
    private String mTargetURI = null;
    private boolean mUseBackupHost = false;
    private String mOtaPackageVersion = null;
    private String mSystemVersion = null;
    private String mOtaPackageName = null;
    private String mOtaPackageLength = null;
    private String mDescription = null;

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private final LocalBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public void installPackage(String packagePath) {
            Log.d(TAG, "installPackage, path: " + packagePath);
            try {
                sWorkHandleLocked = true;
                RecoverySystem.installPackage(mContext, new File(packagePath));
            } catch (IOException e) {
                Log.e(TAG, "installPackage, Reboot for installPackage failed: " + e);
            }
        }

        public boolean verifyPackage(String packagePath) {
            Log.d(TAG, "verifyPackage, start verify package, imagePath: " + packagePath);

            try {
                RecoverySystem.verifyPackage(new File(packagePath), null, null);
            } catch (GeneralSecurityException e) {
                Log.d(TAG, "verifyPackage, verifyPackage failed: " + e);
                return false;
            } catch (IOException exc) {
                Log.d(TAG, "verifyPackage, verifyPackage failed: " + exc);
                return false;
            }
            return true;
        }

        public void deletePackage(String packagePath) {
            Log.d(TAG, "deletePackage, try to delete package");
            File f = new File(packagePath);
            if (f.exists()) {
                f.delete();
                Log.d(TAG, "deletePackage, delete complete, path=" + packagePath);
            } else {
                Log.d(TAG, "deletePackage, path: " + packagePath + ", file not exists!");
            }
        }

        public void unLockWorkHandler() {
            Log.d(TAG, "unLockWorkHandler...");
            sWorkHandleLocked = false;
        }

        public void LockWorkHandler() {
            sWorkHandleLocked = true;
            Log.d(TAG, "LockWorkHandler...");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        mContext = this;

        String otaPackageFileName = getOtaPackageFileName();
        if (!TextUtils.isEmpty(otaPackageFileName)) {
            sOtaPackageName = otaPackageFileName;
            Log.d(TAG, "onCreate, get ota package name is: " + otaPackageFileName);
        }

        try {
            mRemoteURI = new URI(getRemoteUri());
            Log.d(TAG, "onCreate, remote uri is " + mRemoteURI.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread workThread = new HandlerThread("UpdateService: workThread");
        workThread.start();
        mWorkHandler = new WorkHandler(workThread.getLooper());

        if (mIsFirstStartUp) {
            Log.d(TAG, "onCreate, first startup!!!");
            mIsFirstStartUp = false;
            String command = readFlagCommand();
            if (!TextUtils.isEmpty(command)) {
                Log.d(TAG, "command = " + command);
                if (command.contains("$path")) {
                    String path = command.substring(command.indexOf('=') + 1);
                    Log.d(TAG, "onCreate, last_flag: path=" + path);

                    if (command.startsWith(COMMAND_FLAG_SUCCESS)) {
                        mLastUpdatePath = path;
                        sIsNeedDeletePackage = true;

                        showUpdateSuccess();

                        sWorkHandleLocked = true;
                    } else if (command.startsWith(COMMAND_FLAG_UPDATING)) {
                        showUpdateFailed();
                        sWorkHandleLocked = true;
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy...");
        super.onDestroy();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "onStart...");

        super.onStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand...");

        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        int command = intent.getIntExtra("command", COMMAND_NULL);
        int delayTime = intent.getIntExtra("delay", 1000);

        Log.d(TAG, "onStartCommand, command=" + command + " delayTime=" + delayTime);
        if (command == COMMAND_NULL) {
            return Service.START_NOT_STICKY;
        }

        if (sIsNeedDeletePackage) {
            command = COMMAND_DELETE_UPDATE_PACKAGE;
            delayTime = 20000;
            sWorkHandleLocked = true;
        }

        Message msg = new Message();
        msg.what = command;
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
                case COMMAND_CHECK_LOCAL_UPDATING:
                    Log.d(TAG, "WorkHandler, COMMAND_CHECK_LOCAL_UPDATING");

                    if (sWorkHandleLocked) {
                        Log.w(TAG, "WorkHandler, locked!!!");
                        return;
                    }

                    path = getValidPackageFile(PACKAGE_FILE_DIRS);
                    if (!TextUtils.isEmpty(path)) {
                        showNewVersion(path);
                    }
                    break;

                case COMMAND_CHECK_REMOTE_UPDATING:
//                    if (sWorkHandleLocked) {
//                        Log.d(TAG, "WorkHandler::handleMessage() : locked !!!");
//                        return;
//                    }
//
//                    for (int i = 0; i < 2; i++) {
//                        try {
//                            boolean result;
//
//                            if (i == 0) {
//                                mUseBackupHost = false;
//                                result = requestUpdate(mRemoteURI);
//                            } else {
//                                mUseBackupHost = true;
//                                result = requestUpdate(mRemoteURIBackup);
//                            }
//
//                            if (result) {
//                                Log.d(TAG, "find a remote update package, now start PackageDownloadActivity...");
//                                startNotifyActivity();
//                            } else {
//                                Log.d(TAG, "no find remote update package...");
//                                myMakeToast(mContext.getString(R.string.current_new));
//                            }
//                            break;
//                        } catch (Exception e) {
//                            //e.printStackTrace();
//                            Log.d(TAG, "request remote server error...");
//                            myMakeToast(mContext.getString(R.string.current_new));
//                        }
//
//                        try {
//                            Thread.sleep(5000);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
                    break;

                case COMMAND_VERIFY_UPDATE_PACKAGE:
                    Bundle b = msg.getData();
                    path = b.getString("path");
                    if (mBinder.verifyPackage(path)) {
                        mBinder.installPackage(path);
                    } else {
                        Log.e(TAG, path + " verify failed!");
                        showInvalidPackage(path);
                    }
                    break;

                case COMMAND_DELETE_UPDATE_PACKAGE:
                    if (sIsNeedDeletePackage) {
                        Log.d(TAG, "WorkHandler, COMMAND_DELETE_UPDATE_PACKAGE");
                        File f = new File(mLastUpdatePath);
                        if (f.exists()) {
                            f.delete();
                            Log.d(TAG, "WorkHandler, path=" + mLastUpdatePath + ", delete complete!");
                        } else {
                            Log.d(TAG, "WorkHandler, path=" + mLastUpdatePath + " , file not exists!");
                        }

                        sIsNeedDeletePackage = false;
                        sWorkHandleLocked = false;
                    }
                    break;

                default:
                    break;
            }
        }

    }

    private String getValidPackageFile(String searchPaths[]) {
        for (String dirPath : searchPaths) {
            String filePath = dirPath + sOtaPackageName;
            if ((new File(filePath)).exists()) {
                Log.d(TAG, "getValidPackageFile, find package file: " + filePath);
                return filePath;
            }
        }

        //find usb device update package
        File usbRoot = new File(USB_ROOT);
        if (usbRoot.listFiles() == null) {
            return null;
        }

        for (File file : usbRoot.listFiles()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles(new FileFilter() {

                    @Override
                    public boolean accept(File tmpFile) {
                        Log.d(TAG, "getValidPackageFile, scan usb files: " + tmpFile.getAbsolutePath());
                        return !tmpFile.isDirectory() && tmpFile.getName().equals(sOtaPackageName);
                    }
                });

                if (files != null && files.length > 0) {
                    String filePath = files[0].getAbsolutePath();
                    Log.d(TAG, "getValidPackageFile, find package file: " + filePath);
                    return filePath;
                }
            }
        }

        return "";
    }

    private void showNewVersion(final String path) {
        if (!TextUtils.isEmpty(path)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("系统升级");
            builder.setMessage("发现新的系统版本，是否升级？\n" + path);
            builder.setPositiveButton("立即升级", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Message msg = new Message();
                    msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                    Bundle b = new Bundle();
                    b.putString("path", path);
                    msg.setData(b);
                    mWorkHandler.sendMessage(msg);
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("暂不升级", null);
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        }
    }

    private void showNewVersion() {
        //TODO
//        Intent intent = new Intent(mContext, OtaUpdateNotifyActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.putExtra("uri", mTargetURI);
//        intent.putExtra("OtaPackageLength", mOtaPackageLength);
//        intent.putExtra("OtaPackageName", mOtaPackageName);
//        intent.putExtra("OtaPackageVersion", mOtaPackageVersion);
//        intent.putExtra("SystemVersion", mSystemVersion);
//        intent.putExtra("description", mDescription);
//        mContext.startActivity(intent);
//        sWorkHandleLocked = true;
    }

    private void showInvalidPackage(final String path) {
        if (!TextUtils.isEmpty(path)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("系统升级");
            builder.setMessage("无效的升级文件！\n" + path);
            builder.setPositiveButton("重试", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Message msg = new Message();
                    msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                    Bundle b = new Bundle();
                    b.putString("path", path);
                    msg.setData(b);
                    mWorkHandler.sendMessage(msg);
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("取消", null);
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        }
    }

    private void showUpdateSuccess() {

    }

    private void showUpdateFailed() {

    }

    private boolean requestUpdate(URI remote) {
        //TODO
//        if (remote == null) {
//            return false;
//        }
//
//        HttpClient httpClient = CustomerHttpClient.getHttpClient();
//        HttpHead httpHead = new HttpHead(remote);
//
//        HttpResponse response = httpClient.execute(httpHead);
//        int statusCode = response.getStatusLine().getStatusCode();
//
//        if (statusCode != 200) {
//            return false;
//        }
//        if (DEBUG) {
//            for (Header header : response.getAllHeaders()) {
//                Log.d(TAG, header.getName() + ":" + header.getValue());
//            }
//        }
//
//        Header[] headLength = response.getHeaders("OtaPackageLength");
//        if (headLength != null && headLength.length > 0) {
//            mOtaPackageLength = headLength[0].getValue();
//        }
//
//        Header[] headName = response.getHeaders("OtaPackageName");
//        if (headName == null) {
//            return false;
//        }
//        if (headName.length > 0) {
//            mOtaPackageName = headName[0].getValue();
//        }
//
//        Header[] headVersion = response.getHeaders("OtaPackageVersion");
//        if (headVersion != null && headVersion.length > 0) {
//            mOtaPackageVersion = headVersion[0].getValue();
//        }
//
//        Header[] headTargetURI = response.getHeaders("OtaPackageUri");
//        if (headTargetURI == null) {
//            return false;
//        }
//        if (headTargetURI.length > 0) {
//            mTargetURI = headTargetURI[0].getValue();
//        }
//
//        if (mOtaPackageName == null || mTargetURI == null) {
//            Log.d(TAG, "server response format error!");
//            return false;
//        }
//
//        //get description from server response.
//        Header[] headDescription = response.getHeaders("description");
//        if (headDescription != null && headDescription.length > 0) {
//            mDescription = new String(headDescription[0].getValue().getBytes("ISO8859_1"), "UTF-8");
//        }
//
//        if (!mTargetURI.startsWith("http://") && !mTargetURI.startsWith("https://") && !mTargetURI.startsWith("ftp://")) {
//            mTargetURI = "http://" + (mUseBackupHost ? getRemoteHostBackup() : getRemoteHost()) + (mTargetURI.startsWith("/") ? mTargetURI : ("/" + mTargetURI));
//        }
//
//        mSystemVersion = getSystemVersionName();
//
//        Log.d(TAG, "OtaPackageName = " + mOtaPackageName + " OtaPackageVersion = " + mOtaPackageVersion
//                + " OtaPackageLength = " + mOtaPackageLength + " SystemVersion = " + mSystemVersion
//                + "OtaPackageUri = " + mTargetURI);
        return true;
    }

    public static String readFlagCommand() {
        if (UPDATE_FLAG_FILE.exists()) {
            char[] buf = new char[128];
            int readCount = 0;
            try {
                FileReader reader = new FileReader(UPDATE_FLAG_FILE);
                readCount = reader.read(buf, 0, buf.length);
                Log.d(TAG, "readFlagCommand, readCount=" + readCount + " buf.length=" + buf.length);
            } catch (IOException e) {
                Log.e(TAG, "readFlagCommand, can not read: /cache/recovery/last_flag! \n" + e.getMessage());
            } finally {
                UPDATE_FLAG_FILE.delete();
            }

            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < readCount; i++) {
                if (buf[i] == 0) {
                    break;
                }
                sBuilder.append(buf[i]);
            }
            return sBuilder.toString();
        } else {
            Log.d(TAG, "readFlagCommand, " + UPDATE_FLAG_FILE.getPath() + " not exist");
            return "";
        }
    }

    public static void writeFlagCommand(String path) throws IOException {
        RECOVERY_DIR.mkdirs();
        UPDATE_FLAG_FILE.delete();
        FileWriter writer = new FileWriter(UPDATE_FLAG_FILE);
        try {
            writer.write("updating$path=" + path);
        } finally {
            writer.close();
        }
    }

    public static String getRemoteUri() {
        //TODO
        return "http://" + getRemoteHost() + "/OtaUpdater/android?product=" + getProductName() + "&version=" + getSystemVersionName()
                + "&sn=" + getProductSN() + "&country=" + getCountry() + "&language=" + getLanguage();
    }

    public static String getRemoteHost() {
        String remoteHost = AppUtils.getProperty("ro.product.ota.host", "");
        if (TextUtils.isEmpty(remoteHost)) {
            //TODO
            remoteHost = "192.168.1.143:2300";
        }
        return remoteHost;
    }

    private String getOtaPackageFileName() {
        String str = AppUtils.getProperty("ro.ota.packagename", "");
        if (!TextUtils.isEmpty(str) && !str.endsWith(".zip")) {
            return str + ".zip";
        }

        return str;
    }

    private String getFirmwareVersion() {
        return AppUtils.getProperty("ro.firmware.version", "");
    }

    private static String getProductName() {
        return AppUtils.getProperty("ro.product.model", "");
    }

    public static String getSystemVersionName() {
        String versionName = AppUtils.getProperty("ro.product.version", "");
        if (TextUtils.isEmpty(versionName)) {
            versionName = "1.0.0";
        }

        return versionName;
    }

    public static String getProductSN() {
        String sn = AppUtils.getProperty("ro.serialno", "");
        if (TextUtils.isEmpty(sn)) {
            sn = "unknown";
        }

        return sn;
    }

    public static String getCountry() {
        return Locale.getDefault().getCountry();
    }

    public static String getLanguage() {
        return Locale.getDefault().getLanguage();
    }

    private void makeToast(final CharSequence msg) {
        mMainHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
