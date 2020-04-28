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

import android.annotation.SuppressLint;
import android.content.Context;

import com.ayst.romupgrade.R;
import com.baidu.commonlib.interfaces.ErrorCode;

import java.util.HashMap;

public class ErrorString {
    @SuppressLint("UseSparseArrays")
    private static HashMap<Integer, Integer> sErrStrMap = new HashMap<>();

    static {
        // checkUpdate回调错误码
        sErrStrMap.put(ErrorCode.CHECK_PARAM_ERROR, R.string.check_param_error);
        sErrStrMap.put(ErrorCode.CHECK_NET_ERROR, R.string.check_net_error);
        sErrStrMap.put(ErrorCode.CHECK_NO_UPDATE, R.string.check_no_update);

        // download回调错误码
        sErrStrMap.put(ErrorCode.DOWNLOAD_PARAM_ERROR, R.string.download_param_error);
        sErrStrMap.put(ErrorCode.DOWNLOAD_NET_ERROR, R.string.download_net_error);
        sErrStrMap.put(ErrorCode.DOWNLOAD_NO_SPACE, R.string.download_no_space);
        sErrStrMap.put(ErrorCode.DOWNLOAD_APP_INSTALLED_ERROR, R.string.download_app_installed_error);
        sErrStrMap.put(ErrorCode.DOWNLOAD_APP_NOT_INSTALL_ERROR, R.string.download_app_not_install_error);
        sErrStrMap.put(ErrorCode.DOWNLOAD_LOW_VERSION_ERROR, R.string.download_low_version_error);
        sErrStrMap.put(ErrorCode.DOWNLOAD_SHA1_VERIFY_ERROR, R.string.download_sha1_verify_error);

        // upgrade回调错误码
        sErrStrMap.put(ErrorCode.UPGRADE_PARAM_ERROR, R.string.upgrade_param_error);
        sErrStrMap.put(ErrorCode.UPGRADE_SHA1_VERIFY_ERROR, R.string.upgrade_sha1_verify_error);
        sErrStrMap.put(ErrorCode.UPGRADE_SIGN_VERIFY_ERROR, R.string.upgrade_sign_verify_error);
        sErrStrMap.put(ErrorCode.UPGRADE_FILE_NOT_EXIST_ERROR, R.string.upgrade_file_not_exist_error);
        sErrStrMap.put(ErrorCode.UPGRADE_NOT_INSTALLED_ERROR, R.string.upgrade_not_installed_error);
        sErrStrMap.put(ErrorCode.UPGRADE_INSTALL_ERROR, R.string.upgrade_install_error);
        sErrStrMap.put(ErrorCode.UPGRADE_UNINSTALL_ERROR, R.string.upgrade_uninstall_error);
        sErrStrMap.put(ErrorCode.UPGRADE_CUSTOM_ERROR, R.string.upgrade_custom_error);
        sErrStrMap.put(ErrorCode.UPGRADE_USER_NOT_CONFIRM, R.string.upgrade_user_not_confirm);
    }

    public static String get(Context context, int errorCode) {
        Integer strId = sErrStrMap.get(errorCode);
        if (strId != null) {
            return context.getString(strId);
        }
        return "";
    }
}
