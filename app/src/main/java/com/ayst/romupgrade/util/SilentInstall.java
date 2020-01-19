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

package com.ayst.romupgrade.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Created by Administrator on 2018/4/25.
 */
public class SilentInstall {
    private static final String TAG = "SilentInstall";

    /**
     * Silent install
     *
     * @param apkPath The apk file path
     * @return true: success false: failed
     */
    public static boolean install(Context context, String apkPath) {
        boolean result = false;
        BufferedReader errorStream = null;
        DataOutputStream dataOutputStream = null;

        try {
            Process process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());

            String command = "pm install -r " + apkPath + "\n";
            dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            dataOutputStream.flush();
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();

            process.waitFor();
            errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String msg = "";
            String line;
            while ((line = errorStream.readLine()) != null) {
                msg += line;
            }
            Log.d(TAG, "install msg is " + msg);
            /* Installation is considered a Failure if the result contains
            the Failure character, or a success if it is not.
             */
            if (!msg.contains("Failure")) {
                result = true;
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                if (errorStream != null) {
                    errorStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return result;
    }
}
