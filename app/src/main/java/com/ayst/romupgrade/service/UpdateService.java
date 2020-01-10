package com.ayst.romupgrade.service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.ayst.romupgrade.entity.LocalPackage;
import com.ayst.romupgrade.util.SilentInstall;
import com.ayst.romupgrade.util.filecopy.FileCopyTask;
import com.ayst.romupgrade.util.filecopy.FileCopyTaskParam;
import com.ayst.romupgrade.util.filecopy.IFileCopyListener;
import com.baidu.commonlib.interfaces.IDownloadListener;
import com.baidu.commonlib.interfaces.IUpgradeInterface;
import com.baidu.commonlib.interfaces.IUpgradeListener;
import com.baidu.otasdk.ota.Constants;
import com.ayst.romupgrade.App;
import com.ayst.romupgrade.R;
import com.ayst.romupgrade.baidu.NewVersionBean;
import com.ayst.romupgrade.config.UsbConfigManager;
import com.ayst.romupgrade.receiver.UpdateReceiver;
import com.ayst.romupgrade.util.AppUtils;
import com.ayst.romupgrade.util.FileUtils;

public class UpdateService extends Service {
    private static final String TAG = "UpdateService";

    /**
     * Command
     */
    public static final int COMMAND_NULL = 0;
    public static final int COMMAND_CHECK_LOCAL_UPDATING = 1;
    public static final int COMMAND_CHECK_REMOTE_UPDATING = 2;
    public static final int COMMAND_NEW_VERSION = 3;
    public static final int COMMAND_VERIFY_UPDATE_PACKAGE = 4;

    /**
     * Local upgrade type
     */
    public static final int UPDATE_TYPE_RECOMMEND = 1;
    public static final int UPDATE_TYPE_SILENT = 2;

    /**
     * USB config filename
     */
    public static final String USB_CONFIG_FILENAME = "config.ini";

    /**
     * APP local update path
     */
    public static String LOCAL_UPDATE_PATH = "exupdate";

    /**
     * Local upgrade package search path
     */
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

    /**
     * Recovery upgrade status storage file
     */
    private static final String RECOVERY_DIR = "/cache/recovery";
    private static final File UPDATE_FLAG_FILE = new File(RECOVERY_DIR + "/last_flag");
    private static final File OTHER_FLAG_FILE = new File(RECOVERY_DIR + "/last_other_flag");

    /**
     * Recovery upgrade result
     */
    private static final String COMMAND_FLAG_SUCCESS = "success";
    private static final String COMMAND_FLAG_UPDATING = "updating";

    /**
     * Upgrade package file name
     */
    public static String sOtaPackageName = "update.zip";

    private static volatile boolean sWorkHandleLocked = false;
    private static volatile boolean isFirstStartUp = true;

    private Context mContext;
    private WorkHandler mWorkHandler;
    private Handler mMainHandler;
    private UpdateReceiver mUpdateReceiver;
    private UpdateReceiver mMediaMountReceiver;
    private Dialog mDialog;
    private ProgressBar mDownloadPgr;

    private int mLocalPackageIndex = 0;
    private List<LocalPackage> mLocalPackages = new ArrayList<>();

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private final LocalBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        /**
         * Install package
         *
         * @param packagePath
         */
        void installPackage(String packagePath) {
            try {
                saveUpdateFlag(packagePath);

                /*
                 * For Android 5.1 and above, replace /storage/emulated/0 with /data/media/0,
                 * otherwise the recovery will not be accessible.
                 */
                String newPackagePath = packagePath;
                if (packagePath.startsWith(FLASH_ROOT)
                        && android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                    newPackagePath = packagePath.replaceAll(FLASH_ROOT, DATA_ROOT);
                }

                Log.i(TAG, "installPackage, path: " + newPackagePath);

                RecoverySystem.installPackage(mContext, new File(newPackagePath));
            } catch (IOException e) {
                Log.e(TAG, "installPackage, failed: " + e);
                sWorkHandleLocked = false;
            }
        }

        /**
         * Verify package
         *
         * @param packagePath
         * @return
         */
        boolean verifyPackage(String packagePath) {
            Log.i(TAG, "verifyPackage, path: " + packagePath);

            try {
                RecoverySystem.verifyPackage(new File(packagePath), null, null);
            } catch (GeneralSecurityException e) {
                Log.i(TAG, "verifyPackage, failed: " + e);
                return false;
            } catch (IOException e) {
                Log.i(TAG, "verifyPackage, failed: " + e);
                return false;
            }
            return true;
        }

        /**
         * Delete package
         *
         * @param packagePath
         */
        void deletePackage(String packagePath) {
            Log.i(TAG, "deletePackage, try to delete package");

            File f = new File(packagePath);
            if (f.exists()) {
                f.delete();
            } else {
                Log.i(TAG, "deletePackage, path: " + packagePath + ", file not exists!");
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate...");

        mContext = this;

        // Configure Baidu otasdk custom upgrade interface
        App.getOtaAgent().setCustomUpgrade(new CustomUpgradeInterface());

        String otaPackageFileName = getOtaPackageFileName();
        if (!TextUtils.isEmpty(otaPackageFileName)) {
            sOtaPackageName = otaPackageFileName;
            Log.i(TAG, "onCreate, get ota package name is: " + otaPackageFileName);
        }

        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread workThread = new HandlerThread("UpdateService: workThread");
        workThread.start();
        mWorkHandler = new WorkHandler(workThread.getLooper());

        mUpdateReceiver = new UpdateReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(UpdateReceiver.UsbManager.ACTION_USB_STATE);
        intentFilter.addAction(UpdateReceiver.VolumeInfo.ACTION_VOLUME_STATE_CHANGED);
        intentFilter.addAction(Constants.BROADCAST_NEWVERSION); // For Baidu otasdk
        this.registerReceiver(mUpdateReceiver, intentFilter);

        mMediaMountReceiver = new UpdateReceiver();
        IntentFilter mediaFilter = new IntentFilter();
        mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilter.addDataScheme("file");
        this.registerReceiver(mMediaMountReceiver, mediaFilter);

        checkUpdateFlag();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy...");

        this.unregisterReceiver(mUpdateReceiver);
        this.unregisterReceiver(mMediaMountReceiver);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand...");

        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        int command = intent.getIntExtra("command", COMMAND_NULL);
        int delayTime = intent.getIntExtra("delay", 1000);
        Bundle bundle = intent.getBundleExtra("bundle");

        Log.i(TAG, "onStartCommand, command=" + command + " delayTime=" + delayTime);
        if (command == COMMAND_NULL) {
            return Service.START_NOT_STICKY;
        }

        Message msg = new Message();
        msg.what = command;
        msg.obj = bundle;
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
                    Log.i(TAG, "WorkHandler, COMMAND_CHECK_LOCAL_UPDATING");
                    if (sWorkHandleLocked) {
                        Log.i(TAG, "WorkHandler, locked !!!");
                        return;
                    }

                    checkLocalUpdate();
                    break;

                case COMMAND_CHECK_REMOTE_UPDATING:
                    Log.i(TAG, "WorkHandler, COMMAND_CHECK_REMOTE_UPDATING");
                    if (sWorkHandleLocked) {
                        Log.i(TAG, "WorkHandler, locked !!!");
                        return;
                    }

                    if (AppUtils.isConnNetWork(getApplicationContext())) {
                        //TODO
                    } else {
                        Log.e(TAG, "WorkHandler, network is disconnect!");
                    }
                    break;

                case COMMAND_NEW_VERSION:
                    Log.i(TAG, "WorkHandler, COMMAND_NEW_VERSION");
                    if (sWorkHandleLocked) {
                        Log.i(TAG, "WorkHandler, locked !!!");
                        return;
                    }

                    Bundle bundle = (Bundle) msg.obj;
                    if (null != bundle) {
                        showNewVersion((NewVersionBean) bundle.getSerializable("new_version"));
                    }
                    break;

                case COMMAND_VERIFY_UPDATE_PACKAGE:
                    Log.i(TAG, "WorkHandler, COMMAND_VERIFY_UPDATE_PACKAGE");
                    Bundle b = msg.getData();
                    path = b.getString("path");
                    if (mBinder.verifyPackage(path)) {
                        mBinder.installPackage(path);
                    } else {
                        Log.e(TAG, path + " verify failed!");
                        showInvalidPackage(path);
                    }
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * Check local update (apk and rom)
     */
    private void checkLocalUpdate() {
        mLocalPackageIndex = 0;
        mLocalPackages.clear();

        List<String> searchPaths = AppUtils.getStorageList(this);
        for (String dirPath : searchPaths) {
            Log.i(TAG, "checkLocalUpdate, search: " + dirPath);

            int updateType = UPDATE_TYPE_RECOMMEND;
            File dir = new File(dirPath);
            for (File file : dir.listFiles()) {

                if (file.isDirectory()) {
                    if (TextUtils.equals(file.getName(), LOCAL_UPDATE_PATH)) {
                        File[] files = file.listFiles(new FileFilter() {
                            @Override
                            public boolean accept(File tmpFile) {
                                return (!tmpFile.isDirectory() && TextUtils.equals(
                                        FileUtils.getFileSuffix(tmpFile.getName()), "apk"));
                            }
                        });

                        for (File apkFile : files) {
                            mLocalPackages.add(new LocalPackage(LocalPackage.TYPE_APP, apkFile));
                        }

                        File configFile = new File(file, USB_CONFIG_FILENAME);
                        if (configFile.exists()) {
                            updateType = new UsbConfigManager(this, configFile).getUpdateType();
                        }

                        File otaFile = new File(file, sOtaPackageName);
                        if (otaFile.exists()) {
                            mLocalPackages.add(new LocalPackage(LocalPackage.TYPE_ROM, otaFile));
                        }
                    }
                }
            }

            if (!mLocalPackages.isEmpty()) {
                for (LocalPackage localPackage : mLocalPackages) {
                    Log.i(TAG, "checkLocalUpdate, found: " + localPackage.toString());
                }

                showNewVersion(updateType);
                break;
            }
        }
    }

    /**
     * Local new version dialog
     */
    private void showNewVersion(int updateType) {
        sWorkHandleLocked = true;

        StringBuilder sb = new StringBuilder();
        for (LocalPackage localPackage : mLocalPackages) {
            sb.append(localPackage.getFile().getName()).append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle(R.string.upgrade_title);

        if (UPDATE_TYPE_SILENT == updateType) {
            builder.setMessage(getString(R.string.upgrade_message_force) + sb.toString());
        } else {
            builder.setMessage(getString(R.string.upgrade_message) + sb.toString());
            builder.setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    installNext();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    sWorkHandleLocked = false;
                    dialog.dismiss();
                }
            });
        }

        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        if (UPDATE_TYPE_SILENT == updateType) {
            mWorkHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    installNext();
                    dialog.dismiss();
                }
            }, 5000);
        }
    }

    /**
     * Local new version dialog
     *
     * @param path rom ota package
     */
    private void showNewVersion(final String path) {
        if (!TextUtils.isEmpty(path)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle(R.string.upgrade_title);
            builder.setMessage(getString(R.string.upgrade_message) + path);
            builder.setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Notification verification upgrade package
                    Message msg = new Message();
                    msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                    Bundle b = new Bundle();
                    b.putString("path", path);
                    msg.setData(b);
                    mWorkHandler.sendMessage(msg);

                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
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

    /**
     * Local new version dialog for forced upgrade
     *
     * @param path
     */
    private void showNewVersionOfForce(final String path) {
        if (!TextUtils.isEmpty(path)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle(R.string.upgrade_title);
            builder.setMessage(getString(R.string.upgrade_message_force) + path);
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
            mDialog = dialog;

            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Notification verification upgrade package
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

    /**
     * OTA Upgrade new version dialog for baidu
     *
     * @param newVersion NewVersionBean
     */
    private void showNewVersion(final NewVersionBean newVersion) {
        if (null != newVersion) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle(R.string.upgrade_title);
            builder.setMessage(getString(R.string.upgrade_message)
                    + getString(R.string.upgrade_version) + newVersion.getVersion()
                    + "\n" + newVersion.getInfo());
            builder.setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showDownloading();
                    dialog.dismiss();

                    // Download by Baidu
                    App.getOtaAgent().downLoad(newVersion.getPackageX(), new DownloadListener());
                }
            });
            builder.setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
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

    /**
     * Download progress dialog
     */
    private void showDownloading() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.layout_download, null);
        mDownloadPgr = (ProgressBar) view.findViewById(R.id.pgr_download);

        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle(R.string.upgrade_title);
        builder.setMessage(R.string.upgrade_downloading_message);
        builder.setView(view);
        builder.setPositiveButton(R.string.hide, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                // Abort download
                App.getOtaAgent().downLoadAbortAll();
                dialog.dismiss();
            }
        });
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    /**
     * Download completion dialog
     *
     * @param pkgName package name
     */
    private void showDownloaded(final String pkgName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle(R.string.upgrade_title);
        builder.setMessage(R.string.upgrade_downloaded_message);
        builder.setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // install package
                App.getOtaAgent().upgrade(pkgName, null);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    /**
     * Upgrade package verification failure dialog
     *
     * @param path
     */
    private void showInvalidPackage(final String path) {
        if (!TextUtils.isEmpty(path)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle(R.string.upgrade_title);
            builder.setMessage(getString(R.string.upgrade_invalid_package_message) + path);
            builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Notification verification upgrade package
                    Message msg = new Message();
                    msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                    Bundle b = new Bundle();
                    b.putString("path", path);
                    msg.setData(b);
                    mWorkHandler.sendMessage(msg);

                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    sWorkHandleLocked = false;
                    deletePackage(path);
                    dialog.dismiss();
                }
            });
            final Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
            mDialog = dialog;

            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sWorkHandleLocked = false;
                    deletePackage(path);
                    dialog.dismiss();
                }
            }, 15000);
        }
    }

    /**
     * Upgrade success dialog
     */
    private void showUpdateSuccess() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle(R.string.upgrade_title);
        builder.setMessage(R.string.upgrade_success_message);
        builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
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

        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
            }
        }, 5000);
    }

    /**
     * Upgrade failed dialog
     */
    private void showUpdateFailed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setTitle(R.string.upgrade_title);
        builder.setMessage(R.string.upgrade_failed_message);
        builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
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

        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
            }
        }, 5000);
    }

    /**
     * Save the flag to check the upgrade result after the upgrade is complete.
     *
     * @param packagePath package file
     * @throws IOException
     */
    private void saveUpdateFlag(String packagePath)
            throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("path=").append(packagePath);

        FileUtils.writeFile(OTHER_FLAG_FILE, sb.toString());

        FileUtils.writeFile(UPDATE_FLAG_FILE, "updating$path=" + packagePath);
    }

    /**
     * After the upgrade is complete, check the upgrade results.
     */
    private void checkUpdateFlag() {
        if (isFirstStartUp) {
            Log.i(TAG, "checkUpdateFlag, first startup!!!");

            isFirstStartUp = false;

            // Check the other flag
            String flag = null;
            try {
                flag = FileUtils.readFile(OTHER_FLAG_FILE);
            } catch (IOException e) {
                Log.w(TAG, "checkUpdateFlag, " + e.getMessage());
            }
            Log.i(TAG, "checkUpdateFlag, other flag = " + flag);

            if (!TextUtils.isEmpty(flag)) {
                String[] array = flag.split("\\$");
                for (String param : array) {
                    if (param.startsWith("path")) {
                        String lastPath = param.substring(param.indexOf('=') + 1);
                        Log.i(TAG, "checkUpdateFlag, lastPath=" + lastPath);

                        if (lastPath.startsWith(AppUtils.getExternalRootDir(this))) {
                            deletePackage(lastPath);
                        }
                    }
                    OTHER_FLAG_FILE.delete();
                }
            }

            // Check the upgrade flag
            flag = null;
            try {
                flag = FileUtils.readFile(UPDATE_FLAG_FILE);
            } catch (IOException e) {
                Log.w(TAG, "checkUpdateFlag, " + e.getMessage());
            }
            Log.i(TAG, "checkUpdateFlag, upgrade flag = " + flag);

            if (!TextUtils.isEmpty(flag)) {
                String[] array = flag.split("\\$");
                if (array.length == 2) {
                    if (TextUtils.equals(COMMAND_FLAG_SUCCESS, array[0])) {
                        showUpdateSuccess();
                    } else if (TextUtils.equals(COMMAND_FLAG_UPDATING, array[0])) {
                        showUpdateFailed();
                    }
                }
                UPDATE_FLAG_FILE.delete();
            }
        }
    }

    /**
     * Delete upgrade package
     *
     * @param packagePath package file
     */
    private void deletePackage(String packagePath) {
        File file = new File(packagePath);
        if (file.exists()) {
            file.delete();
            Log.i(TAG, "deletePackage, path=" + packagePath);
        }
    }

    /**
     * Configure a new upgrade package name
     *
     * @return new package name
     */
    private String getOtaPackageFileName() {
        String str = AppUtils.getProperty("ro.ota.packagename", "");
        if (!TextUtils.isEmpty(str) && !str.endsWith(".zip")) {
            return str + ".zip";
        }

        return str;
    }

    private void installNext() {
        if (mLocalPackageIndex < mLocalPackages.size()) {
            LocalPackage localPackage = mLocalPackages.get(mLocalPackageIndex);
            mLocalPackageIndex++;

            if (localPackage.getFile() != null
                    && localPackage.getFile().exists()) {

                if (localPackage.getType() == LocalPackage.TYPE_APP) {
                    FileCopyTaskParam param = new FileCopyTaskParam();
                    param.from = localPackage.getFile();
                    param.to = new File(AppUtils.getExternalCacheDir(UpdateService.this, "apks")
                            + File.separator + localPackage.getFile().getName());
                    param.listener = new FileCopyListener();
                    new FileCopyTask(this).execute(param);

                } else if (localPackage.getType() == LocalPackage.TYPE_ROM) {
                    Message msg = new Message();
                    msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                    Bundle b = new Bundle();
                    b.putString("path", localPackage.getFile().getAbsolutePath());
                    msg.setData(b);
                    mWorkHandler.sendMessage(msg);
                }
            } else {
                installNext();
            }

        } else {
            sWorkHandleLocked = false;
        }
    }

    private void install(File file) {
        Log.i(TAG, "install, file=" + file.getPath());

        if (SilentInstall.install(this, file.getAbsolutePath())) {
            Toast.makeText(this, String.format(
                    getString(R.string.upgrade_install_success),
                    file.getName()), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, String.format(
                    getString(R.string.upgrade_install_failed),
                    file.getName()), Toast.LENGTH_SHORT).show();
        }
    }

    private class FileCopyListener implements IFileCopyListener<File> {
        @Override
        public void progress(int progress) {
            //Log.i(TAG, "FileCopyListener, Copy progress: " + progress);
        }

        @Override
        public void completed(File file) {
            Log.i(TAG, "FileCopyListener, Copy " + file.getName() + " completed");
            install(file);
            installNext();
        }

        @Override
        public void error(Exception e) {
            Log.e(TAG, "FileCopyListener, Copy error: " + e.getMessage());
        }
    }

    /**
     * Download listener for Baidu
     */
    private class DownloadListener implements IDownloadListener {

        @Override
        public void onPending(String pkgName) {

        }

        @Override
        public void onPrepare(String pkgName) {

        }

        @Override
        public void onProgress(String pkgName, int sofarBytes, int totalBytes) {
            long progress = sofarBytes / (totalBytes / 100);
            Log.i(TAG, "download->progress, " + sofarBytes
                    + "/" + totalBytes + " " + progress + "%");
            mDownloadPgr.setProgress((int) progress);
        }

        @Override
        public void onPaused(String pkgName) {

        }

        @Override
        public void onFailed(String pkgName, int errCode, String reason) {
            Log.e(TAG, "DownloadListener, onFailed errCode=" + errCode
                    + " reason=" + reason);
            if (null != mDialog && mDialog.isShowing()) {
                mDialog.dismiss();
            }
            Toast.makeText(getApplicationContext(), R.string.upgrade_download_failed_message,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFinished(String pkgName) {
            Log.i(TAG, "download->completed");
            if (null != mDialog && mDialog.isShowing()) {
                mDialog.dismiss();
            }
            showDownloaded(pkgName);
        }
    }

    /**
     * Custom upgrade interface for Baidu
     */
    private class CustomUpgradeInterface implements IUpgradeInterface {

        @Override
        public String installPackage(String pkgName, String file, boolean silence) {
            if (silence) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showNewVersionOfForce(file);
                    }
                });
            } else {
                Message msg = new Message();
                msg.what = COMMAND_VERIFY_UPDATE_PACKAGE;
                Bundle b = new Bundle();
                b.putString("path", file);
                msg.setData(b);
                mWorkHandler.sendMessage(msg);
            }

            return "";
        }

        @Override
        public String unInstallPackage(String pkgName, boolean silence) {
            return null;
        }

        @Override
        public void setListener(IUpgradeListener listener) {

        }
    }
}
