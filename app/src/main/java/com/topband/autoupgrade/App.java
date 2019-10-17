package com.topband.autoupgrade;

import com.baidu.commonlib.interfaces.IOtaAgent;
import com.baidu.commonlib.interfaces.IOtaSdkHelper;
import com.baidu.otasdk.ota.OtaApplication;
import com.liulishuo.filedownloader.FileDownloader;
import com.topband.autoupgrade.baidu.SystemInfo;
import com.topband.autoupgrade.util.AppUtils;

/**
 * Created by Administrator on 2017/12/13.
 */

public class App extends OtaApplication {
    public static final String PRODUCT_ID = "9552";
    private static final String PRODUCT_SECRET = "MTgxZGMxMzE2NjJiNGViYw==";

    private static IOtaAgent sOtaAgent;

    @Override
    public void onCreate() {
        super.onCreate();

        FileDownloader.setup(this);
    }

    @Override
    protected void initService(IOtaSdkHelper otaSdkHelper) {
        otaSdkHelper.init(AppUtils.getWifiMac(this), new SystemInfo());
        otaSdkHelper.setUpgradePath(AppUtils.getDir(this, "upgrade"));
        otaSdkHelper.setAutoCheck(true);
        otaSdkHelper.setSilentUpgradeTime("23:00", "02:00");

        sOtaAgent = otaSdkHelper.getInst(PRODUCT_ID, PRODUCT_SECRET);
    }

    public static IOtaAgent getOtaAgent() {
        return sOtaAgent;
    }
}
