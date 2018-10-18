package com.topband.autoupgrade.config;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by shenhaibo on 2018/5/21.
 */
public class UsbConfigManager {
    private final static String TAG = "UsbConfigManager";

    public final static String UPDATE_TYPE = "UPDATE_TYPE";

    private Context mContext;
    private File mConfigFile;

    private Properties mCfgProperties = null;
    private int mUpdateType = -1;

    public UsbConfigManager(Context context, File configFile) {
        mContext = context;
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
//            InputStream is = context.getAssets().open("config.ini");
            InputStream is = new FileInputStream(mConfigFile);
            mCfgProperties.load(is);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mCfgProperties;
    }

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
}
