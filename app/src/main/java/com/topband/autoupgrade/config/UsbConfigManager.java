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
    public final static String UPDATE_TYPE_RECOMMEND = "1";
    public final static String UPDATE_TYPE_FORCE = "2";


    private Context mContext;
    private File mConfigFile;

    private Properties mCfgProperties = null;
    private String mUpdateType = "";

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

    public String getUpdateType() {
        if (TextUtils.isEmpty(mUpdateType)) {
            Properties properties = getProperties();
            mUpdateType = properties.getProperty(UPDATE_TYPE, UPDATE_TYPE_RECOMMEND);
            Log.i(TAG, "getUpdateType, value:" + mUpdateType);
        }
        return mUpdateType;
    }
}
