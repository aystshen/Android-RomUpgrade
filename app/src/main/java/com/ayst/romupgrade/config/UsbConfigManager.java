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

package com.ayst.romupgrade.config;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Parse the config.ini configuration file in the U disk
 * Created by ayst.shen@foxmail.com on 2018/5/21.
 */
public class UsbConfigManager {
    private final static String TAG = "UsbConfigManager";

    /**
     * 升级类型，1：推荐升级，2：静默升级
     */
    public final static String UPDATE_TYPE = "UPDATE_TYPE";

    /**
     * OTA升级包版本号，如：1.0.0
     */
    public final static String PACKAGE_VERSION = "PACKAGE_VERSION";

    private File mConfigFile;
    private Properties mCfgProperties = null;
    private int mUpdateType = -1;
    private String mPackageVersion = "";

    public UsbConfigManager(File configFile) {
        mConfigFile = configFile;
        if (mConfigFile.exists()) {
            getProperties();
        }
    }

    private Properties getProperties() {
        if (mCfgProperties != null) {
            return mCfgProperties;
        }
        mCfgProperties = new Properties();
        try {
            InputStream is = new FileInputStream(mConfigFile);
            mCfgProperties.load(is);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mCfgProperties;
    }

    /**
     * 获取升级类型
     *
     * @return 1：推荐升级，2：静默升级
     */
    public int getUpdateType() {
        if (-1 == mUpdateType) {
            Properties properties = getProperties();
            String updateTypeStr = properties.getProperty(UPDATE_TYPE, "1");
            try {
                mUpdateType = Integer.parseInt(updateTypeStr);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            Log.i(TAG, "getUpdateType, value:" + mUpdateType);
        }

        return mUpdateType;
    }

    /**
     * 获取升级包版本号
     *
     * @return 版本号
     */
    public String getPackageVersion() {
        if (TextUtils.isEmpty(mPackageVersion)) {
            Properties properties = getProperties();
            mPackageVersion = properties.getProperty(PACKAGE_VERSION, "1.0.0");
            Log.i(TAG, "getPackageVersion, value:" + mPackageVersion);
        }

        return mPackageVersion;
    }
}
