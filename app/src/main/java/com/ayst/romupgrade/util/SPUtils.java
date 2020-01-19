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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * Created by ayst.shen@foxmail.com on 17/8/15.
 */
public class SPUtils {
    private static final String SP = "auto_upgrade";

    private static SPUtils instance;
    private static SharedPreferences mSp = null;

    private SPUtils(Context context) {
        mSp = context.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static SPUtils getInstance(Context context) {
        if (instance == null)
            instance = new SPUtils(context);
        return instance;
    }

    /**
     * Save data
     *
     * @param key preference key
     * @param value preference value
     */
    public void saveData(String key, String value) {
        Editor editor = mSp.edit();
        editor.putString(key, value);
        editor.apply();
    }

    /**
     * Save data
     *
     * @param key preference key
     * @param value preference value
     */
    public void saveData(String key, boolean value) {
        Editor editor = mSp.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    /**
     * Save data
     *
     * @param key preference key
     * @param value preference value
     */
    public void saveData(String key, int value) {
        Editor editor = mSp.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    /**
     * Save data
     *
     * @param key preference key
     * @param value preference value
     */
    public void saveData(String key, float value) {
        Editor editor = mSp.edit();
        editor.putFloat(key, value);
        editor.apply();
    }

    /**
     * Get data
     *
     * @param key preference key
     * @param defValue default value
     * @return value
     */
    public String getData(String key, String defValue) {
        return mSp.getString(key, defValue);
    }

    /**
     * Get data
     *
     * @param key preference key
     * @param defValue default value
     * @return value
     */
    public boolean getData(String key, boolean defValue) {
        return mSp.getBoolean(key, defValue);
    }

    /**
     * Get data
     *
     * @param key preference key
     * @param defValue default value
     * @return value
     */
    public int getData(String key, int defValue) {
        return mSp.getInt(key, defValue);
    }

    /**
     * Get data
     *
     * @param key preference key
     * @param defValue default value
     * @return value
     */
    public float getData(String key, float defValue) {
        return mSp.getFloat(key, defValue);
    }

}
