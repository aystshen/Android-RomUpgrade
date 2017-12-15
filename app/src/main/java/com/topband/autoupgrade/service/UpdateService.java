package com.topband.autoupgrade.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.topband.autoupgrade.R;
import com.topband.autoupgrade.http.HttpHelper;
import com.topband.autoupgrade.http.SessionIDManager;
import com.topband.autoupgrade.http.TopbandApi;
import com.topband.autoupgrade.model.ApplyKeyRspData;
import com.topband.autoupgrade.model.ReqBody;
import com.topband.autoupgrade.model.RspBody;
import com.topband.autoupgrade.model.UpgradeReqData;
import com.topband.autoupgrade.model.UpgradeRspData;
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
                writeFlagCommand(packagePath);
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

        String otaPackageFileName = getOtaPackageFileName();
        if (!TextUtils.isEmpty(otaPackageFileName)) {
            sOtaPackageName = otaPackageFileName;
            Log.d(TAG, "onCreate, get ota package name is: " + otaPackageFileName);
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
                    } else if (command.startsWith(COMMAND_FLAG_UPDATING)) {
                        showUpdateFailed();
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

    private void showDownloading() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle("系统升级");
        builder.setMessage("正在下载新版本，请稍候...");
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        UpgradeReqData reqData = new UpgradeReqData().withMac(AppUtils.getWifiMacAddr(this))
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
                                    showNewVersion(data);
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
                        long progress = soFarBytes/(totalBytes/100);
                        Log.d(TAG, "download->progress, " + soFarBytes + "/" + totalBytes + " " + progress + "%");
                        mDownloadPgr.setProgress((int)progress);
                    }

                    @Override
                    protected void completed(BaseDownloadTask baseDownloadTask) {
                        Log.d(TAG, "download->completed");
                        if (null != mDialog && mDialog.isShowing()) {
                            mDialog.dismiss();
                        }
                        showDownloaded(path);
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

    private String getOtaPackageFileName() {
        String str = AppUtils.getProperty("ro.ota.packagename", "");
        if (!TextUtils.isEmpty(str) && !str.endsWith(".zip")) {
            return str + ".zip";
        }

        return str;
    }

    private void makeToast(final CharSequence msg) {
        mMainHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
