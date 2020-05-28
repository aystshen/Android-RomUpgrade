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

package com.ayst.romupgrade.service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ListView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.ayst.romupgrade.IRomUpgradeService;
import com.ayst.romupgrade.adapter.DownloadAdapter;
import com.ayst.romupgrade.baidu.ErrorString;
import com.ayst.romupgrade.entity.InstallProgress;
import com.ayst.romupgrade.entity.LocalPackage;
import com.ayst.romupgrade.util.InstallUtil;
import com.baidu.commonlib.interfaces.ErrorCode;
import com.baidu.commonlib.interfaces.ICheckUpdateListener;
import com.baidu.commonlib.interfaces.IDownloadListener;
import com.baidu.commonlib.interfaces.IUpgradeListener;
import com.baidu.otasdk.ota.Constants;
import com.ayst.romupgrade.App;
import com.ayst.romupgrade.R;
import com.ayst.romupgrade.baidu.NewVersionBean;
import com.ayst.romupgrade.config.UsbConfigManager;
import com.ayst.romupgrade.receiver.UpdateReceiver;
import com.ayst.romupgrade.util.AppUtils;
import com.ayst.romupgrade.util.FileUtils;
import com.baidu.otasdk.ota.DefaultUpgradeImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import cn.trinea.android.common.util.ShellUtils;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class UpdateService extends Service {
    private static final String TAG = "UpdateService";

    /**
     * 检查升级结果通知广播Action
     */
    public static final String ACTION_CHECK_UPDATE_RESULT =
            "com.ayst.romupgrade.action.CHECK_UPDATE_RESULT";

    /**
     * 命令
     * <p>
     * COMMAND_NULL                    无效命令
     * COMMAND_INITIAL                 初始化Service
     * COMMAND_CHECK_LOCAL_UPDATE      查询本地升级
     * COMMAND_CHECK_REMOTE_UPDATE     查询在线升级
     * COMMAND_NEW_VERSION             百度升级新版本
     */
    public static final int COMMAND_NULL = 0;
    public static final int COMMAND_INITIAL = 1;
    public static final int COMMAND_CHECK_LOCAL_UPDATE = 2;
    public static final int COMMAND_CHECK_REMOTE_UPDATE = 3;
    public static final int COMMAND_NEW_VERSION = 4;

    /**
     * 本地升级类型
     * <p>
     * 推荐升级：弹窗提示
     * 静默升级：不弹窗，直接安装升级包
     */
    public static final int UPDATE_TYPE_RECOMMEND = 1;
    public static final int UPDATE_TYPE_SILENT = 2;

    /**
     * 本地应用升级目录
     * <p>
     * u盘中新建此目录，将待安装app复制到此目录下，插入u盘将弹出升级提示
     */
    public static String LOCAL_UPDATE_PATH = "exupdate";

    /**
     * 本地升级配置文件名
     * <p>
     * 在u盘{@link #LOCAL_UPDATE_PATH}路径下创建此配置文件，内容如下：
     * <p>
     * #升级类型，1：推荐升级，2：静默升级
     * UPDATE_TYPE=2
     */
    public static final String USB_CONFIG_FILENAME = "config.ini";

    /**
     * 本地系统升级ota包名
     * <p>
     * 将ota包复制到u盘根路径下，并重命名为此文件名，插入u盘将弹出升级提示
     */
    public static final String ROM_OTA_PACKAGE_FILENAME = "update.zip";

    /**
     * 安装系统升级包时，替换路径，否则recovery中无法识别
     */
    public static final String DATA_ROOT = "/data/media/0";
    public static final String FLASH_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();

    /**
     * 本地升级包检查内部路径(sdcard)，只检查根目录
     * 例：/data/media/0/{@link #LOCAL_UPDATE_PATH}
     * /data/media/0/{@link #ROM_OTA_PACKAGE_FILENAME}
     * <p>
     * 不能使用AppUtils.getStorageList()获取的路径，否则在Recovery中无法找到文件
     */
    private static final String[] INTERNAL_DIRS = {
            DATA_ROOT,
            FLASH_ROOT,
            "/mnt/external_sd"
    };

    /**
     * 本地升级包检查外部路径（usb），检查二级子目录
     * 例：/mnt/media_rw/6ABF-0AD3/{@link #LOCAL_UPDATE_PATH}
     * /mnt/media_rw/6ABF-0AD3/{@link #ROM_OTA_PACKAGE_FILENAME}
     * <p>
     * 不能使用AppUtils.getStorageList()获取的路径，否则在Recovery中无法找到文件
     */
    private static final String[] EXTERNAL_DIRS = {
            "/mnt/usb_storage",
            "/mnt/media_rw"
    };

    /**
     * 保存系统升级状态文件
     */
    private static final String RECOVERY_DIR = "/cache/recovery";
    private static final File UPDATE_FLAG_FILE = new File(RECOVERY_DIR + "/last_flag");
    private static final File OTHER_FLAG_FILE = new File(RECOVERY_DIR + "/last_other_flag");

    /**
     * 系统升级结果
     */
    private static final String COMMAND_FLAG_SUCCESS = "success";
    private static final String COMMAND_FLAG_UPDATING = "updating";

    private static volatile boolean sWorkHandleLocked = false;
    private static volatile boolean isFirstStartUp = true;

    private static Gson sGson;

    private Context mContext;
    private WorkHandler mWorkHandler;
    private Handler mMainHandler;
    private UpdateReceiver mUpdateReceiver;
    private UpdateReceiver mMediaMountReceiver;
    private Dialog mDialog;
    private ListView mDownloadLv;
    private DownloadAdapter mDownloadAdapter;

    private boolean isUserTriggered = false;
    private int mLocalPackageIndex = 0;
    private int mLocalUpdateType = UPDATE_TYPE_RECOMMEND;
    private List<LocalPackage> mLocalPackages = new ArrayList<>();
    private CustomUpgradeInterface mCustomUpgradeInterface;

    private HashMap<String, InstallProgress> mInstallProgresses = new HashMap<>();

    @Override
    public IBinder onBind(Intent arg0) {
        return mService;
    }

    private final IRomUpgradeService.Stub mService = new IRomUpgradeService.Stub() {
        /**
         * 检查升级
         */
        public void checkUpdate() {
            Log.i(TAG, "checkUpdate");
            if (AppUtils.isConnNetWork(mContext)) {
                if (App.getOtaAgent() != null) {
                    isUserTriggered = true;
                    App.getOtaAgent().checkUpdate(true, new CheckUpdateListener());
                }
            } else {
                Log.e(TAG, "checkUpdate, network is disconnect!");
            }
        }

        /**
         * 安装系统升级包
         *
         * @param packagePath 系统升级包
         */
        public boolean installPackage(String packagePath) {
            try {
                // 保存升级前状态
                saveUpdateFlag(packagePath);

                // Android 5.1 以上版本，将 /storage/emulated/0 路径替换为 /data/media/0,
                // 否则recovery中将无法访问。
                String newPackagePath = packagePath;
                if (packagePath.startsWith(FLASH_ROOT)
                        && android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                    newPackagePath = packagePath.replaceAll(FLASH_ROOT, DATA_ROOT);
                }

                Log.i(TAG, "installPackage, path: " + newPackagePath);

                RecoverySystem.installPackage(mContext, new File(newPackagePath));
            } catch (IOException e) {
                Log.e(TAG, "installPackage, failed: " + e);
                sWorkHandleLocked = false;
                return false;
            }

            return true;
        }

        /**
         * 验证系统升级包有效性
         *
         * @param packagePath 系统升级包路径
         * @return true：有效，false：无效
         */
        public boolean verifyPackage(String packagePath) {
            Log.i(TAG, "verifyPackage, path: " + packagePath);

            try {
                RecoverySystem.verifyPackage(new File(packagePath), null, null);
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "verifyPackage, failed: " + e);
                return false;
            } catch (IOException e) {
                Log.e(TAG, "verifyPackage, failed: " + e);
                return false;
            }
            return true;
        }

        /**
         * 删除系统升级包
         *
         * @param packagePath 系统升级包
         */
        public void deletePackage(String packagePath) {
            Log.i(TAG, "deletePackage, try to delete package");

            File file = new File(packagePath);
            if (file.exists()) {
                file.delete();
            } else {
                Log.w(TAG, "deletePackage, path: " + packagePath + ", file not exists!");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate...");

        mContext = this;
        sGson = new Gson();

        HandlerThread workThread = new HandlerThread("UpdateService: workThread");
        workThread.start();
        mWorkHandler = new WorkHandler(workThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());

        mUpdateReceiver = new UpdateReceiver();
        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
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

        registerInstallBroadcastReceiver();

        checkUpdateFlag();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy...");

        this.unregisterReceiver(mUpdateReceiver);
        this.unregisterReceiver(mMediaMountReceiver);
        unregisterInstallBroadcastReceiver();

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

    /**
     * 运行在子线程中的Handler，执行外部触发的命令
     */
    private class WorkHandler extends Handler {
        WorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COMMAND_INITIAL:
                    Log.i(TAG, "WorkHandler, COMMAND_INITIAL");
                    if (App.getOtaAgent() != null) {
                        // 配置百度自定义升级安装接口
                        mCustomUpgradeInterface = new CustomUpgradeInterface(mContext, App.getProductId());
                        App.getOtaAgent().setCustomUpgrade(mCustomUpgradeInterface);
                    } else {
                        Log.e(TAG, "WorkHandler, IOtaAgent is null");
                    }
                    break;

                case COMMAND_CHECK_LOCAL_UPDATE:
                    Log.i(TAG, "WorkHandler, COMMAND_CHECK_LOCAL_UPDATE");
                    if (sWorkHandleLocked) {
                        Log.i(TAG, "WorkHandler, locked !!!");
                        return;
                    }

                    checkLocalUpdate();
                    break;

                case COMMAND_CHECK_REMOTE_UPDATE:
                    Log.i(TAG, "WorkHandler, COMMAND_CHECK_REMOTE_UPDATE");
                    if (sWorkHandleLocked) {
                        Log.i(TAG, "WorkHandler, locked !!!");
                        return;
                    }

                    if (AppUtils.isConnNetWork(mContext)) {
                        if (App.getOtaAgent() != null) {
                            App.getOtaAgent().checkUpdate(true, new CheckUpdateListener());
                        }
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
                        parseBaiduNewVersion(bundle.getString("infos"));
                    }
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * 解析百度升级信息
     *
     * @param jsonList 百度升级json列表
     */
    private void parseBaiduNewVersion(String jsonList) {
        if (!TextUtils.isEmpty(jsonList)) {
            Type type = new TypeToken<List<NewVersionBean>>() {
            }.getType();
            List<NewVersionBean> newVersions = sGson.fromJson(jsonList, type);

            if (null != newVersions && !newVersions.isEmpty()) {
                sWorkHandleLocked = true;
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showNewVersion(newVersions);
                    }
                });
            }
        }
    }

    /**
     * 检查本地升级
     * <p>
     * 系统升级包：根目录下的 update.zip 文件
     * 应用升级包：exupdate 目录下的apk文件
     */
    private void checkLocalUpdate() {
        mLocalUpdateType = UPDATE_TYPE_RECOMMEND;
        mLocalPackageIndex = 0;
        mLocalPackages.clear();

        for (String root : EXTERNAL_DIRS) {
            Log.i(TAG, "checkLocalUpdate, search external: " + root);

            File rootDir = new File(root);
            if (null != rootDir.listFiles()) {
                for (File file : rootDir.listFiles()) {
                    if (file.isDirectory()) {
                        loadExupdate(new File(file, LOCAL_UPDATE_PATH));
                        loadOtaPackage(file);

                        if (!mLocalPackages.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }

        if (mLocalPackages.isEmpty()) {
            for (String root : INTERNAL_DIRS) {
                Log.i(TAG, "checkLocalUpdate, search internal: " + root);

                File rootDir = new File(root);
                loadExupdate(new File(rootDir, LOCAL_UPDATE_PATH));
                loadOtaPackage(rootDir);

                if (!mLocalPackages.isEmpty()) {
                    break;
                }
            }
        }

        if (!mLocalPackages.isEmpty()) {
            for (LocalPackage localPackage : mLocalPackages) {
                Log.i(TAG, "checkLocalUpdate, found: " + localPackage.toString());
            }

            sWorkHandleLocked = true;
            if (UPDATE_TYPE_SILENT == mLocalUpdateType) {
                installLocalNext();
            } else {
                showNewVersion();
            }
        }
    }

    private boolean loadOtaPackage(File file) {
        if (null != file && file.exists() && file.isDirectory()) {
            File otaFile = new File(file, ROM_OTA_PACKAGE_FILENAME);
            if (otaFile.exists()) {
                mLocalPackages.add(new LocalPackage(LocalPackage.TYPE_ROM, otaFile));
            }
        }

        return !mLocalPackages.isEmpty();
    }

    private boolean loadExupdate(File file) {
        if (null != file && file.exists() && file.isDirectory()) {
            File[] files = file.listFiles(new FileFilter() {
                @Override
                public boolean accept(File tmpFile) {
                    return (!tmpFile.isDirectory() && TextUtils.equals(
                            FileUtils.getFileSuffix(tmpFile.getName()), "apk"));
                }
            });

            if (null != files) {
                for (File apkFile : files) {
                    mLocalPackages.add(new LocalPackage(LocalPackage.TYPE_APP, apkFile));
                }
            }

            File configFile = new File(file, USB_CONFIG_FILENAME);
            if (configFile.exists()) {
                mLocalUpdateType = new UsbConfigManager(configFile).getUpdateType();
            }
        }

        return !mLocalPackages.isEmpty();
    }

    /**
     * 保存升级标志，用于系统升级完成后判别升级结果
     *
     * @param packagePath 系统升级包路径
     * @throws IOException
     */
    private void saveUpdateFlag(String packagePath) {
        try {
            /* rockchip平台tf卡烧录后会写cache/recovery/last_flag文件，并且权限为600，
             * 所以必须通过root权限删除此文件。
             */
            ShellUtils.execCommand("rm -rf " + OTHER_FLAG_FILE, true);
            ShellUtils.execCommand("rm -rf " + UPDATE_FLAG_FILE, true);

            StringBuilder sb = new StringBuilder();
            sb.append("path=").append(packagePath);

            FileUtils.writeFile(OTHER_FLAG_FILE, sb.toString());

            FileUtils.writeFile(UPDATE_FLAG_FILE, "updating$path=" + packagePath);
        } catch (IOException e) {
            Log.e(TAG, "saveUpdateFlag, " + e);
        }
    }

    /**
     * 检查升级标志，用于系统升级完成后判别升级结果
     */
    private void checkUpdateFlag() {
        if (isFirstStartUp) {
            Log.i(TAG, "checkUpdateFlag, first startup!!!");

            isFirstStartUp = false;

            // 检查 last_other_flag
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
                            deletePackage(new File(lastPath));
                        }
                    }
                    OTHER_FLAG_FILE.delete();
                }
            }

            // 检查 last_flag
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
                        showUpgradeSuccess();
                    } else if (TextUtils.equals(COMMAND_FLAG_UPDATING, array[0])) {
                        showUpgradeFailed();
                    }
                }
                UPDATE_FLAG_FILE.delete();
            }
        }
    }

    /**
     * 删除升级包
     *
     * @param file 系统升级包
     */
    private void deletePackage(File file) {
        if (file.exists()) {
            file.delete();
            Log.i(TAG, "deletePackage, path=" + file.getAbsolutePath());
        }
    }

    /**
     * 文件复制
     *
     * @param from 源文件
     * @param to   目标文件
     * @throws Exception
     */
    private void copyFile(DocumentFile from, DocumentFile to) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(from.getUri());
        OutputStream outputStream = getContentResolver().openOutputStream(to.getUri());

        if (null != inputStream && null != outputStream) {
            int count;
            byte[] bytes = new byte[1024];

            while ((count = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, count);
            }

            outputStream.close();
            inputStream.close();
        } else {
            throw new IOException("InputStream or OutputStream is null");
        }
    }

    /**
     * 依次安装本地升级包（包括应用和系统）
     */
    private void installLocalNext() {
        if (mLocalPackageIndex < mLocalPackages.size()) {
            LocalPackage localPackage = mLocalPackages.get(mLocalPackageIndex);
            mLocalPackageIndex++;

            if (localPackage.getFile() != null
                    && localPackage.getFile().exists()) {

                if (localPackage.getType() == LocalPackage.TYPE_APP) {
                    installAppWithCopy(localPackage.getFile());

                } else if (localPackage.getType() == LocalPackage.TYPE_ROM) {
                    installSystem(localPackage.getFile());
                }
            } else {
                installLocalNext();
            }

        } else {
            sWorkHandleLocked = false;
        }
    }

    /**
     * 安装u盘中应用（u盘中apk文件无法直接安装，因此需要复制到内部存储空间后再安装）
     *
     * @param file 应用
     */
    @SuppressLint("CheckResult")
    private void installAppWithCopy(final File file) {
        Observable.create(new ObservableOnSubscribe<File>() {
            @Override
            public void subscribe(ObservableEmitter<File> emitter) throws Exception {
                try {
                    File to = new File(AppUtils.getExternalDir(mContext, "apks")
                            + File.separator + file.getName());

                    copyFile(DocumentFile.fromFile(file), DocumentFile.fromFile(to));

                    emitter.onNext(to);

                } catch (Exception e) {
                    emitter.onError(new Throwable(e.getMessage()));
                }
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(new Observer<File>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(File file) {
                        installApp(file);
                        installLocalNext();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "installAppWithCopy, e: " + e.getMessage());
                        installLocalNext();
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    /**
     * 安装应用
     *
     * @param file 应用
     */
    @SuppressLint("CheckResult")
    private void installApp(final File file) {
        installApp("", file);
    }

    /**
     * 安装应用
     *
     * @param pkgName 包名
     * @param file    应用
     */
    @SuppressLint("CheckResult")
    private void installApp(final String pkgName, final File file) {
        Log.i(TAG, "installApp, pkgName=" + pkgName + " file=" + file.getPath());

        Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
                if (InstallUtil.installSilent(file.getAbsolutePath())) {
                    emitter.onNext(true);
                } else {
                    InstallUtil.install(UpdateService.this, file.getAbsolutePath());
                }
                if (!TextUtils.isEmpty(pkgName)) {
                    mCustomUpgradeInterface.reportSuccess(pkgName);
                }
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean success) throws Exception {
                        if (success) {
                            Toast.makeText(UpdateService.this,
                                    file.getName() + " " + getString(R.string.upgrade_install_success),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(UpdateService.this,
                                    file.getName() + " " + getString(R.string.upgrade_install_failed),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * 安装系统升级
     *
     * @param file 系统升级包
     */
    @SuppressLint("CheckResult")
    private void installSystem(final File file) {
        installSystem("", file);
    }

    /**
     * 安装系统升级
     *
     * @param pkgName 包名（com.android.system）
     * @param file    系统升级包
     */
    @SuppressLint("CheckResult")
    private void installSystem(final String pkgName, final File file) {
        Log.i(TAG, "installSystem, pkgName=" + pkgName + " file=" + file.getPath());

        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                if (mService.verifyPackage(file.getAbsolutePath())) {
                    if (!mService.installPackage(file.getAbsolutePath())) {
                        if (!TextUtils.isEmpty(pkgName)) {
                            mCustomUpgradeInterface.reportFail(pkgName,
                                    ErrorCode.UPGRADE_INSTALL_ERROR,
                                    "Install failed");
                        }
                        emitter.onNext(getString(R.string.upgrade_install_failed));
                    } else {
                        if (!TextUtils.isEmpty(pkgName)) {
                            mCustomUpgradeInterface.reportSuccess(pkgName);
                        }
                    }
                } else {
                    if (!TextUtils.isEmpty(pkgName)) {
                        mCustomUpgradeInterface.reportFail(pkgName,
                                ErrorCode.UPGRADE_SIGN_VERIFY_ERROR,
                                "Verify failed");
                    }
                    emitter.onNext(getString(R.string.upgrade_invalid_package));
                }
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        showInstallSystemFail(file, s);
                    }
                });
    }

    /**
     * 本地升级提示
     */
    private void showNewVersion() {
        StringBuilder sb = new StringBuilder();
        for (LocalPackage localPackage : mLocalPackages) {
            sb.append(localPackage.getFile().getName()).append("\n");
        }

        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage(getString(R.string.upgrade_message) + sb.toString())
                .setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        installLocalNext();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        sWorkHandleLocked = false;
                        dialog.dismiss();
                    }
                }).create();

        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    /**
     * 百度升级提示
     *
     * @param newVersions 升级列表
     */
    private void showNewVersion(final List<NewVersionBean> newVersions) {
        mInstallProgresses.clear();

        StringBuilder sb = new StringBuilder();
        for (NewVersionBean bean : newVersions) {
            sb.append(bean.getPackageName()).append("(").append(bean.getVersion()).append(")\n");
            sb.append(bean.getInfo()).append("\n");

            mInstallProgresses.put(bean.getPackageName(), new InstallProgress(bean));
        }

        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage(getString(R.string.upgrade_message) + sb.toString())
                .setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDownloading();
                        if (App.getOtaAgent() != null) {
                            App.getOtaAgent().downLoadAll(new DownloadListener());
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        sWorkHandleLocked = false;
                        dialog.dismiss();
                    }
                }).create();

        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    /**
     * 下载进度
     */
    private void showDownloading() {
        mDownloadAdapter = new DownloadAdapter(this, new ArrayList<>(mInstallProgresses.values()));

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.layout_download, null);
        mDownloadLv = (ListView) view.findViewById(R.id.list);
        mDownloadLv.setAdapter(mDownloadAdapter);

        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_download)
                .setView(view)
                .setPositiveButton(R.string.hide, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        sWorkHandleLocked = false;
                        if (App.getOtaAgent() != null) {
                            App.getOtaAgent().downLoadAbortAll();
                        }
                        dialog.dismiss();
                    }
                }).create();

        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        mDialog = dialog;
    }

    /**
     * 安装系统升级包失败提示（自定义升级）
     *
     * @param file    升级包
     * @param message 错误信息
     */
    private void showInstallSystemFail(final File file, final String message) {
        showInstallSystemFail(file, "", message);
    }

    /**
     * 安装系统升级包失败提示（百度升级）
     *
     * @param pkgName com.android.system
     * @param message 错误信息
     */
    private void showInstallSystemFail(final String pkgName, final String message) {
        showInstallSystemFail(null, pkgName, message);
    }

    /**
     * 安装系统升级包失败提示
     *
     * @param file    系统升级包
     * @param message 失败信息
     */
    @SuppressLint("CheckResult")
    private void showInstallSystemFail(final File file, final String pkgName, final String message) {
        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage((file != null ? file.getAbsoluteFile() : pkgName) + " " + message)
                .setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (file != null) {
                            installSystem(file);
                        } else {
                            if (App.getOtaAgent() != null) {
                                App.getOtaAgent().upgrade(pkgName, new UpgradeListener());
                            }
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        sWorkHandleLocked = false;
                        if (file != null) {
                            deletePackage(file);
                        }
                        dialog.dismiss();
                    }
                }).create();

        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        Observable.timer(15000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        if (dialog.isShowing()) {
                            sWorkHandleLocked = false;
                            if (file != null) {
                                deletePackage(file);
                            }
                            dialog.dismiss();
                        }
                    }
                });
    }

    /**
     * 系统升级成功提示
     */
    @SuppressLint("CheckResult")
    private void showUpgradeSuccess() {
        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage(R.string.upgrade_success)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();

        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        Observable.timer(5000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    }
                });
    }

    /**
     * 系统升级失败提示
     */
    @SuppressLint("CheckResult")
    private void showUpgradeFailed() {
        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage(R.string.upgrade_failed)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();

        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

        Observable.timer(5000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    }
                });
    }

    /**
     * 百度检查在线升级回调
     */
    private class CheckUpdateListener implements ICheckUpdateListener {

        @Override
        public void onSuccess(String jsonList) {
            Log.i(TAG, "CheckUpdateListener->onSuccess, jsonList=" + jsonList);

            parseBaiduNewVersion(jsonList);

            if (isUserTriggered) {
                Intent intent = new Intent();
                intent.setAction(ACTION_CHECK_UPDATE_RESULT);
                intent.putExtra("result", 1);
                mContext.sendBroadcast(intent);
                isUserTriggered = false;
            }
        }

        @Override
        public void onFail(int errCode, String reason) {
            Log.w(TAG, "CheckUpdateListener->onFail, errCode=" + errCode + ", reason=" + reason);

            if (isUserTriggered) {
                Intent intent = new Intent();
                intent.setAction(ACTION_CHECK_UPDATE_RESULT);
                intent.putExtra("result", 0);
                mContext.sendBroadcast(intent);
                isUserTriggered = false;
            }
        }
    }

    /**
     * 百度升级包下载回调
     */
    private class DownloadListener implements IDownloadListener {

        @Override
        public void onPending(String pkgName) {
            Log.i(TAG, "DownloadListener->onPending, pkgName=" + pkgName);
        }

        @Override
        public void onPrepare(String pkgName) {
            Log.i(TAG, "DownloadListener->onPrepare, pkgName=" + pkgName);
        }

        @Override
        public void onProgress(String pkgName, int sofarBytes, int totalBytes) {
            long progress = sofarBytes / (totalBytes / 100);
            Log.i(TAG, "DownloadListener->progress, pkgName: " + pkgName + " " + sofarBytes
                    + "/" + totalBytes + " " + progress + "%");
            InstallProgress item = mInstallProgresses.get(pkgName);
            if (null != item) {
                item.setProgress((int) progress);
                mInstallProgresses.put(pkgName, item);
                mDownloadAdapter.update(new ArrayList<>(mInstallProgresses.values()));
            }
        }

        @Override
        public void onPaused(String pkgName) {
            Log.i(TAG, "DownloadListener->onPaused, pkgName=" + pkgName);
        }

        @Override
        public void onFailed(String pkgName, int errCode, String reason) {
            Log.e(TAG, "DownloadListener->onFailed errCode=" + errCode
                    + " reason=" + reason);
        }

        @Override
        public void onFinished(String pkgName) {
            Log.i(TAG, "download->onFinished, pkgName=" + pkgName);

            // 标记当前升级包下载完成
            InstallProgress item = mInstallProgresses.get(pkgName);
            if (null != item) {
                item.setProgress(100);
                item.setDownloaded(true);
            }

            // 判断全部升级包是否下载完成
            boolean isAllDownloaded = true;
            for (InstallProgress install : mInstallProgresses.values()) {
                if (!install.isDownloaded()) {
                    isAllDownloaded = false;
                }
            }

            // 所有升级包全部下载完成，销毁下载进度对话框
            if (isAllDownloaded && null != mDialog && mDialog.isShowing()) {
                mDialog.dismiss();
            }

            if (App.getOtaAgent() != null) {
                App.getOtaAgent().upgrade(pkgName, new UpgradeListener());
            } else {
                Log.e(TAG, "DownloadListener->onFinished getOtaAgent is null.");
            }
        }
    }

    /**
     * 百度升级安装回调
     */
    private class UpgradeListener implements IUpgradeListener {

        @Override
        public void onProgress(String pkgName, String stage, int percent) {
            Log.i(TAG, "UpgradeListener->onProgress, pkgName=" + pkgName
                    + " stage=" + stage + " percent=" + percent);
        }

        @Override
        public void onFailed(String pkgName, int errCode, String reason) {
            Log.i(TAG, "UpgradeListener->onFailed, pkgName=" + pkgName
                    + " errCode=" + errCode + " reason=" + reason);
            showInstallSystemFail(pkgName, ErrorString.get(mContext, errCode));
        }

        @Override
        public void onWriteDone(String pkgName) {
            Log.i(TAG, "UpgradeListener->onWriteDone, pkgName=" + pkgName);
        }

        @Override
        public void onSuccess(String pkgName) {
            Log.i(TAG, "UpgradeListener->onSuccess, pkgName=" + pkgName);

            // 标记当前升级包安装完成
            InstallProgress item = mInstallProgresses.get(pkgName);
            if (null != item) {
                item.setInstalled(true);
            }

            // 判断全部升级包是否安装完成
            boolean isAllInstalled = true;
            for (InstallProgress install : mInstallProgresses.values()) {
                if (!install.isInstalled()) {
                    isAllInstalled = false;
                }
            }

            if (isAllInstalled) {
                Log.i(TAG, "UpgradeListener->onSuccess, All packages are installed.");
                sWorkHandleLocked = false;
            }
        }
    }

    /**
     * 自定义百度升级安装接口
     */
    private class CustomUpgradeInterface extends DefaultUpgradeImpl {

        CustomUpgradeInterface(Context context, String pid) {
            super(context, pid);
        }

        @SuppressLint("CheckResult")
        @Override
        protected String installSystem(String pkgName, File file, boolean silence) {
            Log.i(TAG, "CustomUpgradeInterface->installSystem, pkgName=" + pkgName);

            UpdateService.this.installSystem(pkgName, file);

            return Constants.UPGRADE_PENDING;
        }

        @Override
        protected void installApp(String pkgName, File file, boolean silence) {
            Log.i(TAG, "CustomUpgradeInterface->installApp, pkgName=" + pkgName);

            UpdateService.this.installApp(pkgName, file);
        }

        /**
         * 上报安装成功
         *
         * @param pkgName 包名
         */
        protected void reportSuccess(String pkgName) {
            Log.i(TAG, "CustomUpgradeInterface->reportSuccess, pkgName=" + pkgName);

            getListener(pkgName).onSuccess(pkgName);
        }

        /**
         * 上报安装失败
         *
         * @param pkgName 包名
         */
        protected void reportFail(String pkgName, int errCode, String reason) {
            Log.i(TAG, "CustomUpgradeInterface->reportFail, pkgName=" + pkgName);

            getListener(pkgName).onFailed(pkgName, errCode, reason);
        }
    }

    private void registerInstallBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        intentFilter.addDataScheme("package");
        this.registerReceiver(mInstallBroadcastReceiver, intentFilter);
    }

    private void unregisterInstallBroadcastReceiver() {
        this.unregisterReceiver(mInstallBroadcastReceiver);
    }

    private BroadcastReceiver mInstallBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive (Context context, Intent intent){
            if (intent != null
                    && (TextUtils.equals(Intent.ACTION_PACKAGE_ADDED, intent.getAction())
                    || TextUtils.equals(Intent.ACTION_PACKAGE_REPLACED, intent.getAction()))) {
                if (intent.getData() != null) {
                    String packageName = intent.getData().getSchemeSpecificPart();
                    Log.i(TAG, "mInstallBroadcastReceiver, installed-------->" + packageName);

                    InstallProgress installProgress = mInstallProgresses.get(packageName);
                    if (installProgress != null && !installProgress.isAfterExecuted()) {
                        NewVersionBean info = installProgress.getInfo();
                        if (info != null) {
                            switch (info.getAfter()) {
                                case NewVersionBean.AFTER_REBOOT:
                                    Log.i(TAG, "mInstallBroadcastReceiver, AFTER_REBOOT");
                                    AppUtils.reboot(UpdateService.this);
                                    break;
                                case NewVersionBean.AFTER_START_APP:
                                    Log.i(TAG, "mInstallBroadcastReceiver, AFTER_START_APP");
                                    AppUtils.startApp(UpdateService.this, packageName);
                                    break;
                                default:
                                    break;
                            }
                        }
                        installProgress.setAfterExecuted(true);
                        mInstallProgresses.put(packageName, installProgress);
                    }
                }
            }
        }
    };
}
