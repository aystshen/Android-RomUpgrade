package com.ayst.romupgrade;

import android.util.Log;

import com.baidu.commonlib.interfaces.IOtaAgent;
import com.baidu.commonlib.interfaces.IOtaSdkHelper;
import com.baidu.otasdk.ota.OtaApplication;
import com.liulishuo.filedownloader.FileDownloader;
import com.ayst.romupgrade.baidu.SystemInfo;
import com.ayst.romupgrade.util.AppUtils;

/**
 * Created by ayst.shen@foxmail.com on 2017/12/13.
 */
public class App extends OtaApplication {
    private static final String TAG = "App";

    /**
     * Baidu otask product id and secret
     */
    private static final String DEFAULT_PRODUCT_ID = "9732";
    private static final String DEFAULT_PRODUCT_SECRET = "ZWMzZjQ0M2Q0Y2IyZjg2NA==";

    private static IOtaAgent sOtaAgent;
    private static String sProductId = DEFAULT_PRODUCT_ID;
    private static String sProductSecret = DEFAULT_PRODUCT_SECRET;

    @Override
    public void onCreate() {
        super.onCreate();

        FileDownloader.setup(this);
    }

    /**
     * Initialize Baidu otasdk
     * @param otaSdkHelper
     */
    @Override
    protected void initService(IOtaSdkHelper otaSdkHelper) {
        otaSdkHelper.init(AppUtils.getDeviceId(), new SystemInfo());
        otaSdkHelper.setUpgradePath(AppUtils.getDir(this, "upgrade"));
        otaSdkHelper.setAutoCheck(true);
        otaSdkHelper.setSilentUpgradeTime("00:00", "24:00");

        // Read the id and secret of different products from the property.
        sProductId = AppUtils.getProperty("ro.baidu.product.id", sProductId);
        sProductSecret = AppUtils.getProperty("ro.baidu.product.secret", sProductSecret);

        sOtaAgent = otaSdkHelper.getInst(sProductId, sProductSecret);

        Log.i(TAG, "initService, product id: " + sProductId
                + " device id: " + AppUtils.getDeviceId());
    }

    public static IOtaAgent getOtaAgent() {
        return sOtaAgent;
    }

    public static String getProductId() {
        return sProductId;
    }
}
