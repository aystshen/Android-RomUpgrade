package com.topband.autoupgrade;

import com.baidu.commonlib.interfaces.IOtaAgent;
import com.baidu.commonlib.interfaces.IOtaSdkHelper;
import com.baidu.otasdk.ota.OtaApplication;
import com.liulishuo.filedownloader.FileDownloader;
import com.topband.autoupgrade.baidu.SystemInfo;
import com.topband.autoupgrade.util.AppUtils;

/**
 * Created by ayst.shen@foxmail.com on 2017/12/13.
 */
public class App extends OtaApplication {
    /**
     * Baidu otask product id and secret
     */
    private static final String DEFAULT_PRODUCT_ID = "9552";
    private static final String DEFAULT_PRODUCT_SECRET = "MTgxZGMxMzE2NjJiNGViYw==";

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
        otaSdkHelper.init(AppUtils.getWifiMac(this), new SystemInfo());
        otaSdkHelper.setUpgradePath(AppUtils.getDir(this, "upgrade"));
        otaSdkHelper.setAutoCheck(true);
        otaSdkHelper.setSilentUpgradeTime("00:00", "24:00");

        // Read the id and secret of different products from the property.
        sProductId = AppUtils.getProperty("ro.baidu.product.id", sProductId);
        sProductSecret = AppUtils.getProperty("ro.baidu.product.secret", sProductSecret);

        sOtaAgent = otaSdkHelper.getInst(sProductId, sProductSecret);
    }

    public static IOtaAgent getOtaAgent() {
        return sOtaAgent;
    }

    public static String getProductId() {
        return sProductId;
    }
}
