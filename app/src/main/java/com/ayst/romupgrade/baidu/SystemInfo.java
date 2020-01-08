package com.ayst.romupgrade.baidu;

import com.baidu.commonlib.interfaces.ISystemInfo;
import com.ayst.romupgrade.util.AppUtils;

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
