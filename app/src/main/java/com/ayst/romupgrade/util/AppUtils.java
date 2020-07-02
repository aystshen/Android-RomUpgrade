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

package com.ayst.romupgrade.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by ayst.shen@foxmail.com on 2016/4/6.
 */
public class AppUtils {
    private final static String TAG = "AppUtils";

    private static final String KEY_IS_FIRST = "is_first_run";

    // Application version
    private static String mVersionName = "";
    private static int mVersionCode = -1;

    // Firmware version
    private static String mFwVersion = "";

    // MAC
    private static String mEth0Mac = "";
    private static String mWifiMac = "";
    private static String mMac = "";
    private static String mMacNoColon = "";

    // Screen
    private static int mScreenWidth = -1;
    private static int mScreenHeight = -1;

    // Storage
    private static String sRootDir = "";

    // Device id
    private static String sDeviceId = "";

    /**
     * Is first run
     *
     * @param context Context
     * @return true: First run, false: Not the first time
     */
    public static boolean isFirstRun(Context context) {
        boolean isFirst = SPUtils.getInstance(context).getData(KEY_IS_FIRST, true);
        if (isFirst) {
            SPUtils.getInstance(context).saveData(KEY_IS_FIRST, false);
        }
        return isFirst;
    }

    /**
     * Get application version name
     *
     * @param context Context
     * @return version name
     */
    public static String getVersionName(Context context) {
        if (TextUtils.isEmpty(mVersionName)) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0);
                mVersionName = info.versionName;
                mVersionCode = info.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return mVersionName;
    }

    /**
     * Get application version code
     *
     * @param context Context
     * @return version code
     */
    public static int getVersionCode(Context context) {
        if (-1 == mVersionCode) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0);
                mVersionName = info.versionName;
                mVersionCode = info.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return mVersionCode;
    }

    /**
     * Get firmware version
     *
     * @return version
     */
    public static String getFwVersion() {
        if (TextUtils.isEmpty(mFwVersion)) {
            mFwVersion = getProperty("ro.fw.version",
                    getProperty("ro.topband.sw.version", "1.0.0"));
        }
        return mFwVersion;
    }

    /**
     * Get serial number
     *
     * @return serial number
     */
    @SuppressLint("HardwareIds")
    public static String getSerialNo() {
        String sn = android.os.Build.SERIAL;
        if (TextUtils.isEmpty(sn)) {
            sn = getProperty("ro.serialno", "");
            if (TextUtils.isEmpty(sn)) {
                sn = "unknown";
            }
        }

        return sn;
    }

    /**
     * Get cpu serial
     *
     * @return success: cpu serial, failed: "0000000000000000"
     */
    public static String getCPUSerial() {
        String cpuAddress = "0000000000000000";

        try {
            Process process = Runtime.getRuntime().exec("cat /proc/cpuinfo");
            InputStreamReader is = new InputStreamReader(process.getInputStream());
            LineNumberReader input = new LineNumberReader(is);

            String str;
            while ((str = input.readLine()) != null) {
                if (!TextUtils.isEmpty(str)) {
                    if (str.contains("Serial")) {
                        String cpuStr = str.substring(str.indexOf(":") + 1);
                        cpuAddress = cpuStr.trim();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getCPUSerial, " + e.getMessage());
        }

        return cpuAddress;
    }

    /**
     * Get device id
     *
     * @return device id
     */
    public static String getDeviceId() {
        if (TextUtils.isEmpty(sDeviceId)) {
            sDeviceId = getSerialNo();
        }

        return sDeviceId;
    }

    /**
     * Get current country
     *
     * @return country
     */
    public static String getCountry() {
        return Locale.getDefault().getCountry();
    }

    /**
     * Get current language
     *
     * @return language
     */
    public static String getLanguage() {
        return Locale.getDefault().getLanguage();
    }

    /**
     * Whether the network is connected
     *
     * @param context Context
     * @return true: connected, false: disconnected
     */
    public static boolean isConnNetWork(Context context) {
        ConnectivityManager conManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conManager.getActiveNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnected());
    }

    /**
     * Whether WiFi is connected
     *
     * @param context Context
     * @return true: connected, false: disconnected
     */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager conManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = conManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return ((wifiNetworkInfo != null) && wifiNetworkInfo.isConnected());
    }

    /**
     * Get Ethernet MAC
     *
     * @param context Context
     * @return Mac
     */
    public static String getEth0Mac(Context context) {
        if (TextUtils.isEmpty(mEth0Mac)) {
            try {
                int numRead = 0;
                char[] buf = new char[1024];
                StringBuffer strBuf = new StringBuffer(1000);
                BufferedReader reader = new BufferedReader(new FileReader(
                        "/sys/class/net/eth0/address"));
                while ((numRead = reader.read(buf)) != -1) {
                    String readData = String.valueOf(buf, 0, numRead);
                    strBuf.append(readData);
                }
                mEth0Mac = strBuf.toString().replaceAll("\r|\n", "");
                reader.close();
            } catch (IOException ex) {
                Log.w(TAG, "eth0 mac not exist");
            }
        }
        return mEth0Mac;
    }

    /**
     * Get WiFi MAC
     *
     * @param context Context
     * @return Mac
     */
    @SuppressLint("HardwareIds")
    public static String getWifiMac(Context context) {
        if (TextUtils.isEmpty(mWifiMac)) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            mWifiMac = wifiInfo.getMacAddress();
        }
        return mWifiMac;
    }

    /**
     * Get MAC, get the Ethernet MAC first, then get the WiFi MAC if it is empty.
     *
     * @param context Context
     * @return Mac
     */
    public static String getMac(Context context) {
        if (TextUtils.isEmpty(mMac)) {
            mMac = getEth0Mac(context);
            if (TextUtils.isEmpty(mMac)) {
                mMac = getWifiMac(context);
            }
        }
        return mMac;
    }

    /**
     * Get the MAC with the colon removed
     *
     * @param context Context
     * @return Mac
     */
    public static String getMacNoColon(Context context) {
        if (TextUtils.isEmpty(mMacNoColon)) {
            String mac = getMac(context);
            if (!TextUtils.isEmpty(mac)) {
                mMacNoColon = mac.replace(":", "");
            }
        }
        return mMacNoColon;
    }

    /**
     * Get screen width
     *
     * @param context Activity
     * @return screen width
     */
    public static int getScreenWidth(Activity context) {
        if (-1 == mScreenWidth) {
            mScreenWidth = context.getWindowManager().getDefaultDisplay().getWidth();
        }
        return mScreenWidth;
    }

    /**
     * Get screen height
     *
     * @param context Activity
     * @return screen height
     */
    public static int getScreenHeight(Activity context) {
        if (-1 == mScreenHeight) {
            mScreenHeight = context.getWindowManager().getDefaultDisplay().getHeight();
        }
        return mScreenHeight;
    }

    /**
     * Get property
     *
     * @param key          property key
     * @param defaultValue default value
     * @return property value
     */
    @SuppressLint("PrivateApi")
    public static String getProperty(String key, String defaultValue) {
        String value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String) (get.invoke(c, key, defaultValue));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    /**
     * Set property
     *
     * @param key   property key
     * @param value property value
     */
    @SuppressLint("PrivateApi")
    public static void setProperty(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isExternalStorageMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * /storage/emulated/0/"packagename"
     *
     * @param context Context
     * @return path
     */
    public static String getExternalRootDir(Context context) {
        if (sRootDir.isEmpty()) {
            File sdcardDir = null;
            try {
                if (isExternalStorageMounted()) {
                    sdcardDir = Environment.getExternalStorageDirectory();
                    Log.i(TAG, "Environment.MEDIA_MOUNTED :" + sdcardDir.getAbsolutePath()
                            + " R:" + sdcardDir.canRead() + " W:" + sdcardDir.canWrite());

                    if (sdcardDir.canWrite()) {
                        String dir = sdcardDir.getAbsolutePath() + File.separator + context.getPackageName();
                        File file = new File(dir);
                        if (!file.exists()) {
                            Log.i(TAG, "getExternalRootDir, dir not exist and make dir");
                            file.mkdirs();
                        }
                        sRootDir = dir;
                        return sRootDir;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sRootDir;
    }

    /**
     * /storage/emulated/0/"packagename"/"dirName"
     *
     * @param context Context
     * @param dirName relative path
     * @return full path
     */
    public static String getExternalDir(Context context, String dirName) {
        String dir = getExternalRootDir(context) + File.separator + dirName;
        File file = new File(dir);
        if (!file.exists()) {
            Log.i(TAG, "getDir, dir not exist and make dir");
            file.mkdirs();
        }
        return dir;
    }

    /**
     * /storage/emulated/0/Android/data/"packagename"/cache/"dirName"
     *
     * @param context Context
     * @param dirName relative path
     * @return full path
     */
    public static String getExternalCacheDir(Context context, String dirName) {
        String dir = "";
        if (isExternalStorageMounted()) {
            dir = context.getExternalCacheDir().getAbsolutePath() + File.separator + dirName;
            File file = new File(dir);
            if (!file.exists()) {
                Log.i(TAG, "getExternalCacheDir, dir not exist and make dir");
                file.mkdirs();
            }
        }
        return dir;
    }

    /**
     * /data/user/0/"packagename"/cache/"dirName"
     *
     * @param context Context
     * @param dirName relative path
     * @return full path
     */
    public static String getCacheDir(Context context, String dirName) {
        String dir = context.getCacheDir().getAbsolutePath() + File.separator + dirName;
        File file = new File(dir);
        if (!file.exists()) {
            Log.i(TAG, "getCacheDir, dir not exist and make dir");
            file.mkdirs();
        }
        return dir;
    }

    /**
     * Get all external storage paths
     *
     * @param context context
     * @return storage paths
     */
    public static List<String> getStorageList(Context context) {
        List<String> paths;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            paths = getStorageVolumeList(context);
        } else {
            paths = getMountPathList();
        }

        if (paths.isEmpty() && isExternalStorageMounted()) {
            paths.add(Environment.getExternalStorageDirectory()
                    .getAbsolutePath());
        }
        return paths;
    }

    /**
     * Get all external storage paths, for lower than Android N
     *
     * @return storage paths
     */
    private static List<String> getMountPathList() {
        List<String> paths = new ArrayList<String>();

        try {
            Process p = Runtime.getRuntime().exec("cat /proc/mounts");
            BufferedInputStream inputStream = new BufferedInputStream(p.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Log.i(TAG, "getMountPathList, " + line);

                // /data/media /storage/emulated/0 sdcardfs rw,nosuid,nodev,relatime,uid=1023,gid=1023 0 0
                String[] temp = TextUtils.split(line, " ");
                String result = temp[1];
                File file = new File(result);
                if (file.isDirectory() && file.canRead() && file.canWrite()) {
                    Log.d(TAG, "getMountPathList, add --> " + file.getAbsolutePath());
                    paths.add(result);
                }

                if (p.waitFor() != 0 && p.exitValue() == 1) {
                    Log.e(TAG, "getMountPathList, cmd execute failed!");
                }
            }
            bufferedReader.close();
            inputStream.close();

        } catch (Exception e) {
            Log.e(TAG, "getMountPathList, failed, " + e.toString());
        }

        return paths;
    }

    /**
     * Get all external storage paths, for higher than Android N
     *
     * @param context context
     * @return storage paths
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static List<String> getStorageVolumeList(Context context) {
        List<String> paths = new ArrayList<String>();
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = storageManager.getStorageVolumes();

        try {
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");

            for (StorageVolume storageVolume : volumes) {
                String storagePath = (String) getPath.invoke(storageVolume);
                boolean isRemovableResult = (boolean) isRemovable.invoke(storageVolume);
                String description = storageVolume.getDescription(context);
                paths.add(storagePath);

                Log.d(TAG, "getStorageVolumeList, storagePath=" + storagePath
                        + ", isRemovableResult=" + isRemovableResult + ", description=" + description);
            }
        } catch (Exception e) {
            Log.e(TAG, "getStorageVolumeList, failed, " + e);
        }

        return paths;
    }

    /**
     * reboot
     *
     * @param context Context
     */
    public static void reboot(Context context) {
        Intent intent = new Intent(Intent.ACTION_REBOOT);
        intent.putExtra("nowait", 1);
        intent.putExtra("interval", 1);
        intent.putExtra("window", 0);
        context.sendBroadcast(intent);
    }

    /**
     * shutdown
     *
     * @param context Context
     */
    public static void shutdown(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN");
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * start app
     *
     * @param context     Context
     * @param packageName PackageName
     */
    public static void startApp(@NonNull Context context, @NonNull String packageName) {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(packageName);
        if (intent != null) {
            context.startActivity(intent);
        } else {
            Log.e(TAG, "startApp, Package does not exist.");
        }
    }
}
