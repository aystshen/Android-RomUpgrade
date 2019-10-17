package com.topband.autoupgrade.baidu;

import com.baidu.commonlib.interfaces.ISystemInfo;
import com.topband.autoupgrade.util.AppUtils;

public class SystemInfo implements ISystemInfo {
    @Override
    public String getVersion() {
        return AppUtils.getSwVersionName();
    }

    @Override
    public String getModel() {
        return AppUtils.getProductName();
    }

    @Override
    public String getCPU() {
        return AppUtils.getPlatform();
    }
}
