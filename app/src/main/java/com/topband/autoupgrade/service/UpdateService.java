package com.topband.autoupgrade.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.GeneralSecurityException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Build;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.topband.autoupgrade.R;
import com.topband.autoupgrade.config.UsbConfigManager;
import com.topband.autoupgrade.helper.AndroidX;
import com.topband.autoupgrade.http.HttpHelper;
import com.topband.autoupgrade.http.SessionIDManager;
import com.topband.autoupgrade.http.TopbandApi;
import com.topband.autoupgrade.model.ApplyKeyRspData;
import com.topband.autoupgrade.model.ReqBody;
import com.topband.autoupgrade.model.RspBody;
import com.topband.autoupgrade.model.UpgradeReqData;
import com.topband.autoupgrade.model.UpgradeRspData;
import com.topband.autoupgrade.receiver.UpdateReceiver;
import com.topband.autoupgrade.util.AppUtils;
import com.topband.autoupgrade.util.DataEncryptUtil;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdateService extends Service {
    private static final String TAG = "UpdateService";

    public static final int COMMAND_NULL = 0;
    public static final int COMMAND_CHECK_LOCAL_UPDATING = 1;
    public static final int COMMAND_CHECK_REMOTE_UPDATING = 2;
    public static final int COMMAND_VERIFY_UPDATE_PACKAGE = 3;
    public static final int COMMAND_DELETE_UPDATE_PACKAGE = 4;

    private static final String COMMAND_FLAG_SUCCESS = "success";
    private static final String COMMAND_FLAG_UPDATING = "updating";

    public static final int UPDATE_SUCCESS = 1;
    public static final int UPDATE_FAILED = 2;

    public static final int UPDATE_TYPE_RECOMMEND = 1;
    public static final int UPDATE_TYPE_FORCE = 2;

    public static final String USB_CONFIG_FILENAME = "config.ini";

    public static final String DATA_ROOT = "/data/media/0";
    public static final String FLASH_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    public static final String SDCARD_ROOT = "/mnt/external_sd";
    public static final String USB_ROOT = "/mnt/usb_storage";
    public static final String USB_ROOT_M = "/mnt/media_rw";
    public static final String CACHE_ROOT = Environment.getDownloadCacheDirectory().getAbsolutePath();
    private static final String[] PACKAGE_FILE_DIRS = {
            DATA_ROOT + "/",
            FLASH_ROOT + "/",
            SDCARD_ROOT + "/",
            USB_ROOT + "/",
    };

    private static final String RECOVERY_DIR = "/cache/recovery";
    private static final String UPDATE_FLAG_FILE = RECOVERY_DIR + "/last_flag";
    private static final String OTHER_FLAG_FILE = RECOVERY_DIR + "/other_flag";

    private Context mContext;
    private volatile boolean mIsFirstStartUp = true;
    private int mUpdateType = UPDATE_TYPE_RECOMMEND;
    private AndroidX mAndroidX;

    private String mLastUpdatePath;
    private WorkHandler mWorkHandler;
    private Handler mMainHandler;
    private UpdateReceiver mUpdateReceiver;

    private Dialog mDialog = null;
    private ProgressBar mDownloadPgr = null;
    private int mDownloadTaskId = 0;

    public static String sOtaPackageName = "update.zip";
    private static volatile boolean sWorkHandleLocked = false;
    private static volatile boolean sIsNeedDeletePackage = false;

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private final LocalBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public void installPackage(String packagePath) {
            Log.d(TAG, "installPackage, path: " + packagePath);

            try {
                writeFlag(OTHER_FLAG_FILE, "watchdog=" + (mAndroidX.watchdogIsOpen() ? "true" : "false"));
                writeFlag(UPDATE_FLAG_FILE, "updating$path=" + packagePath);

                /*
                 * 安装升级包前一定要关闭watchdog，否则升级过程中
                 * watchdog超时复位将导致严重后果。
                 */
                mAndroidX.toggleWatchdog(false);

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
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        mContext = this;
        mAndroidX = new AndroidX(this);

        String otaPackageFileName = getOtaPackageFileName();
        if (!TextUtils.isEmpty(otaPackageFileName)) {
            sOtaPackageName = otaPackageFileName;
            Log.d(TAG, "onCreate, get ota package name is: " + otaPackageFileName);
        }

        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread workThread = new HandlerThread("UpdateService: workThread");
        workThread.start();
        mWorkHandler = new WorkHandler(workThread.getLooper());

        mUpdateReceiver = new UpdateReceiver();
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction("android.hardware.usb.action.USB_STATE");
        intentFilter.addAction("android.os.storage.action.VOLUME_STATE_CHANGED");
        this.registerReceiver(mUpdateReceiver, intentFilter);

        checkUpdateFlag();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy...");

        this.unregisterReceiver(mUpdateReceiver);

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
                        Log.d(TAG, "WorkHandler, find update file: " + path);
                        if (UPDATE_TYPE_FORCE == mUpdateType) {
                            showNewVersionOfForce(path);
                        } else {
                            showNewVersion(path);
                        }
                    } else {
                        Log.d(TAG, "WorkHandler, not found update file");
                    }
                    break;

                case COMMAND_CHECK_REMOTE_UPDATING:
                    Log.d(TAG, "WorkHandler, COMMAND_CHECK_REMOTE_UPDATING");
                    if (sWorkHandleLocked) {
                        Log.d(TAG, "WorkHandler, locked !!!");
                        return;
                    }

                    if (AppUtils.isConnNetWork(getApplicationContext())) {
                        requestUpdate();
                    } else {
                        Log.e(TAG, "WorkHandler, network is disconnect!");
                    }
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
                    break;

                default:
                    break;
            }
        }

    }

    private String getValidPackageFile(String searchPaths[]) {
        String packageFile = "";
        for (String dirPath : searchPaths) {
            String path = dirPath + USB_CONFIG_FILENAME;
            if ((new File(path)).exists()) {
                mUpdateType = new UsbConfigManager(this, new File(path)).getUpdateType();
                Log.d(TAG, "getValidPackageFile, find config file: " + path);
            }

            path = dirPath + sOtaPackageName;
            if ((new File(path)).exists()) {
                packageFile = path;
                Log.d(TAG, "getValidPackageFile, find package file: " + packageFile);
            }
        }
        if (!packageFile.isEmpty()) {
            return packageFile;
        }

        //find usb device update package
        String usbRootDir = USB_ROOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            usbRootDir = USB_ROOT_M;
        }
        Log.i(TAG, "getValidPackageFile, find usb: " + usbRootDir);
        File usbRoot = new File(usbRootDir);
        if (usbRoot.listFiles() == null) {
            return "";
        }

        for (File file : usbRoot.listFiles()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles(new FileFilter() {

                    @Override
                    public boolean accept(File tmpFile) {
                        Log.d(TAG, "getValidPackageFile, scan usb files: " + tmpFile.getAbsolutePath());
                        return (!tmpFile.isDirectory() && (tmpFile.getName().equals(sOtaPackageName)
                                || tmpFile.getName().equals(USB_CONFIG_FILENAME)));
                    }
                });

                if (files != null && files.length > 0) {
                    for (File tmpFile : files) {
                        if (tmpFile.getName().equals(USB_CONFIG_FILENAME)) {
                            mUpdateType = new UsbConfigManager(this, new File(tmpFile.getAbsolutePath())).getUpdateType();
                            Log.d(TAG, "getValidPackageFile, find config file: " + tmpFile.getAbsolutePath());
                        }

                        if (tmpFile.getName().equals(sOtaPackageName)) {
                            packageFile = tmpFile.getAbsolutePath();
                            Log.d(TAG, "getValidPackageFile, find package file: " + packageFile);
                        }
                    }
                    if (!packageFile.isEmpty()) {
                        return packageFile;
                    }
                }
            }
        }

        return "";
    }

    private void showNewVersion(final String path) {
        if (!TextUtils.isEmpty(path)) {
            sWorkHandleLocked = true;
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
            builder.setNegativeButton("暂不升级", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    sWorkHandleLocked = false;
                    dialog.dismiss();
                }
            });
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();

            mDialog = dialog;
        }
    }

    private void showNewVersionOfForce(final String path) {
        if (!TextUtils.isEmpty(path)) {
            sWorkHandleLocked = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("系统升级");
            builder.setMessage("发现新的系统版本，5秒后将自动升级！\n" + path);
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
            mDialog = dialog;

            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Message msg = new Message();
                    msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                    Bundle b = new Bundle();
                    b.putString("path", path);
                    msg.setData(b);
                    mWorkHandler.sendMessage(msg);
                    dialog.dismiss();
                }
            }, 5000);
        }
    }

    private void showNewVersion(final UpgradeRspData data) {
        if (null != data) {
            sWorkHandleLocked = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("系统升级");
            builder.setMessage("发现新的系统版本，是否升级？\n" + data.getDesc());
            builder.setPositiveButton("立即升级", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String path = AppUtils.getDir(getApplicationContext(), "upgrade") + "/update.zip";
                    download(data.getPakgUrl(), path);
                    showDownloading();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("暂不升级", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    sWorkHandleLocked = false;
                    dialog.dismiss();
                }
            });
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();

            mDialog = dialog;
        }
    }

    private void showNewVersionOfForce(final UpgradeRspData data) {
        if (null != data) {
            sWorkHandleLocked = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("系统升级");
            builder.setMessage("发现新的系统版本，5秒后将自动下载升级！\n" + data.getDesc());
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
            mDialog = dialog;

            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    String path = AppUtils.getDir(getApplicationContext(), "upgrade") + "/update.zip";
                    download(data.getPakgUrl(), path);
                    showDownloading();
                    dialog.dismiss();
                }
            }, 5000);
        }
    }

    private void showDownloading() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("系统升级");
        builder.setMessage("正在下载新版本，请稍候...");
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.layout_download, null);
        mDownloadPgr = (ProgressBar) view.findViewById(R.id.pgr_download);
        builder.setView(view);
        builder.setPositiveButton("隐藏", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                sWorkHandleLocked = false;
                FileDownloader.getImpl().pause(mDownloadTaskId);
                dialog.dismiss();
            }
        });
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    private void showDownloaded(final String path) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("系统升级");
        builder.setMessage("新版本已经下载完成，是否升级？");
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
        builder.setNegativeButton("暂不升级", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                sWorkHandleLocked = false;
                dialog.dismiss();
            }
        });
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    private void showDownloadedOfForce(final String path) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("系统升级");
        builder.setMessage("新版本已经下载完成，5秒后将自动升级");
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
        mDialog = dialog;

        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Message msg = new Message();
                msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                Bundle b = new Bundle();
                b.putString("path", path);
                msg.setData(b);
                mWorkHandler.sendMessage(msg);
                dialog.dismiss();
            }
        }, 5000);
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
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    sWorkHandleLocked = false;
                    dialog.dismiss();
                }
            });
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();

            mDialog = dialog;
        }
    }

    private void showUpdateSuccess() {
        sWorkHandleLocked = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("系统升级");
        builder.setMessage("新版本升级成功！");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    private void showUpdateFailed() {
        sWorkHandleLocked = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("系统升级");
        builder.setMessage("新版本升级失败！");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sWorkHandleLocked = false;
                dialog.dismiss();
            }
        });
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    private void requestUpdate() {
        if (TextUtils.isEmpty(SessionIDManager.instance(this).getSessionID())) {
            SessionIDManager.instance(this).requestSessionID(new SessionIDManager.SessionIDListener() {
                @Override
                public void onSuccess(ApplyKeyRspData data) {
                    requestUpdate(data.getSessionId(), data.getKey());
                }

                @Override
                public void onFailed(int errCode) {
                    Log.e(TAG, "requestUpdate->requestSessionID, onFailed errCode=" + errCode);
                }
            });
        } else {
            requestUpdate(SessionIDManager.instance(this).getSessionID(), SessionIDManager.instance(this).getKey());
        }
    }

    private void requestUpdate(final String sessionId, final String key) {
        UpgradeReqData reqData = new UpgradeReqData().withMac(AppUtils.getMacNoColon(this))
                .withProductId(AppUtils.getProductId())
                .withVersion(AppUtils.getSwVersionCode())
                .withType(3);
        String data = DataEncryptUtil.encryptData(reqData, SessionIDManager.instance(this).getKey());
        ReqBody reqBody = new ReqBody().withSn(AppUtils.getUUID())
                .withTime(String.valueOf(System.currentTimeMillis()))
                .withSessionId(sessionId)
                .withData(data);
        Log.d(TAG, "requestUpdate, " + reqBody.toString() + ", " + reqData.toString());
        HttpHelper.instance(this).getService(TopbandApi.class)
                .getUpgradeInfo(reqBody)
                .enqueue(new Callback<RspBody>() {
                    @Override
                    public void onResponse(Call<RspBody> call, Response<RspBody> response) {
                        RspBody rspBody = response.body();
                        if (null != rspBody) {
                            Log.i(TAG, "requestUpdate->onResponse, " + rspBody.toString());
                            if (rspBody.getStatus() == 0) {
                                // success
                                UpgradeRspData data = DataEncryptUtil.decryptData(rspBody.getData(),
                                        UpgradeRspData.class,
                                        key);
                                if (null != data) {
                                    Log.d(TAG, "requestUpdate->onResponse, data=" + data.toString());
                                    mUpdateType = data.getUpgradeType();
                                    if (UPDATE_TYPE_FORCE == mUpdateType) {
                                        showNewVersionOfForce(data);
                                    } else {
                                        showNewVersion(data);
                                    }
                                } else {
                                    Log.e(TAG, "requestUpdate->onResponse, data is null!");
                                }
                            }
                        } else {
                            Log.e(TAG, "requestUpdate->onResponse, body is null!");
                        }
                    }

                    @Override
                    public void onFailure(Call<RspBody> call, Throwable t) {
                        Log.i(TAG, "requestUpdate->onFailure");
                    }
                });
    }

    private void download(final String url, final String path) {
        Log.d(TAG, "download...");
        mDownloadTaskId = FileDownloader.getImpl().create(url)
                .setPath(path)
                .setListener(new FileDownloadListener() {
                    @Override
                    protected void pending(BaseDownloadTask baseDownloadTask, int i, int i1) {

                    }

                    @Override
                    protected void progress(BaseDownloadTask baseDownloadTask, int soFarBytes, int totalBytes) {
                        long progress = soFarBytes / (totalBytes / 100);
                        Log.d(TAG, "download->progress, " + soFarBytes + "/" + totalBytes + " " + progress + "%");
                        mDownloadPgr.setProgress((int) progress);
                    }

                    @Override
                    protected void completed(BaseDownloadTask baseDownloadTask) {
                        Log.d(TAG, "download->completed");
                        if (null != mDialog && mDialog.isShowing()) {
                            mDialog.dismiss();
                        }
                        if (UPDATE_TYPE_FORCE == mUpdateType) {
                            showDownloadedOfForce(path);
                        } else {
                            showDownloaded(path);
                        }
                    }

                    @Override
                    protected void paused(BaseDownloadTask baseDownloadTask, int i, int i1) {

                    }

                    @Override
                    protected void error(BaseDownloadTask baseDownloadTask, Throwable throwable) {

                    }

                    @Override
                    protected void warn(BaseDownloadTask baseDownloadTask) {

                    }
                }).start();
    }

    public static String readFlag(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            char[] buf = new char[256];
            int readCount = 0;
            FileReader reader = null;

            try {
                reader = new FileReader(file);
                readCount = reader.read(buf, 0, buf.length);
                Log.d(TAG, "readFlag, readCount=" + readCount + " buf.length=" + buf.length);
            } catch (IOException e) {
                Log.e(TAG, "readFlag, can not read: " + filename + "\n" + e.getMessage());
            } finally {
                if (null != reader) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                file.delete();
            }

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < readCount; i++) {
                if (buf[i] == 0) {
                    break;
                }
                builder.append(buf[i]);
            }
            return builder.toString();
        } else {
            Log.d(TAG, "readFlag, " + filename + " not exist");
        }

        return "";
    }

    public static void writeFlag(String filename, String flag) throws IOException {
        if (TextUtils.isEmpty(filename) || TextUtils.isEmpty(flag)) {
            Log.w(TAG, "writeFlag, filename or flag is null.");
            return;
        }

        File recoveryDir = new File(RECOVERY_DIR);
        if (!recoveryDir.exists()) {
            recoveryDir.mkdirs();
        }

        File file = new File(filename);
        if (file.exists()) {
            file.delete();
        }
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(flag);
        } finally {
            writer.close();
        }
    }

    private void checkUpdateFlag() {
        if (mIsFirstStartUp) {
            Log.d(TAG, "checkUpdateFlag, first startup!!!");

            mIsFirstStartUp = false;

            String flag = readFlag(UPDATE_FLAG_FILE);
            if (!TextUtils.isEmpty(flag)) {
                Log.d(TAG, "checkUpdateFlag, flag = " + flag);
                String[] array = flag.split("$");
                if (array.length == 2) {
                    if (array[1].startsWith("path")) {
                        mLastUpdatePath = array[1].substring(array[1].indexOf('=') + 1);
                    }

                    sIsNeedDeletePackage = true;
                    if (TextUtils.equals(COMMAND_FLAG_SUCCESS, array[0])) {
                        showUpdateSuccess();
                    } else if (TextUtils.equals(COMMAND_FLAG_UPDATING, array[0])) {
                        showUpdateFailed();
                    }
                }
            }

            flag = readFlag(OTHER_FLAG_FILE);
            if (!TextUtils.isEmpty(flag)) {
                Log.d(TAG, "checkUpdateFlag, flag = " + flag);
                String[] array = flag.split("$");
                for (String param : array) {
                    if (param.startsWith("watchdog")) {
                        String value = param.substring(param.indexOf('=') + 1);
                        Log.d(TAG, "checkUpdateFlag, watchdog=" + value);

                        if (TextUtils.equals("true", value)) {
                            mAndroidX.toggleWatchdog(true);
                        }
                    }
                }
            }
        }
    }

    private String getOtaPackageFileName() {
        String str = AppUtils.getProperty("ro.ota.packagename", "");
        if (!TextUtils.isEmpty(str) && !str.endsWith(".zip")) {
            return str + ".zip";
        }

        return str;
    }
}
