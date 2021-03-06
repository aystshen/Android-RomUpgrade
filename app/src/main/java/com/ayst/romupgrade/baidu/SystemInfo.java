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

package com.ayst.romupgrade.baidu;

import android.os.Build;

import com.baidu.commonlib.interfaces.ISystemInfo;
import com.ayst.romupgrade.util.AppUtils;

public class SystemInfo implements ISystemInfo {
    @Override
    public String getVersion() {
        return AppUtils.getFwVersion();
    }

    @Override
    public String getModel() {
        return Build.MODEL;
    }

    @Override
    public String getCPU() {
        return Build.BOARD;
    }
}
