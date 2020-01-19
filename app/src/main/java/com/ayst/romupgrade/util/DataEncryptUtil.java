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

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import org.json.JSONObject;

/**
 * Created by ayst.shen@foxmail.com on 2017/12/12.
 */
public class DataEncryptUtil {
    private static final String TAG = "DataEncryptUtil";
    private static Gson gson = new Gson();

    public static <T> String encryptData(T value, String key) {
        if (value == null) {
            Log.e(TAG, "encryptData, value is null");
            return "";
        }
        try {
            JSONObject json = new JSONObject(gson.toJson(value));
            Log.d(TAG, "encryptData, request data: " + json.toString());
            return EncryptUtil.encryptAES(key, json.toString());
        } catch (Exception e) {
            Log.e(TAG, "encryptData, " + e.getMessage());
        }
        return "";
    }

    public static <T> T decryptData(String value, Class<T> tClass, String key) {
        if (!TextUtils.isEmpty(value)) {
            try {
                String decryptString = EncryptUtil.decryptAES(key, value);
                if (!TextUtils.isEmpty(decryptString)) {
                    return gson.fromJson(decryptString, tClass);
                }
            } catch (Exception e) {
                Log.e(TAG, "decryptData, " + e.getMessage());
            }
        }
        return null;
    }
}
