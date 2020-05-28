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

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class NewVersionBean implements Serializable {

    /**
     * 安装完成后动作
     */
    public static final int AFTER_NONE = 0;        // 什么也不做
    public static final int AFTER_REBOOT = 1;      // 安装后重启
    public static final int AFTER_START_APP = 2;   // 安装后启动APP

    /**
     * package : com.android.system
     * version : 1.0.51.0
     * info : infos
     * taskid : 60900
     * detail : {"after":"2"}
     * filesize : 514044607
     * updtype : 1
     */

    @SerializedName("package")
    private String packageName;
    @SerializedName("version")
    private String version;
    @SerializedName("info")
    private String info;
    @SerializedName("taskid")
    private String taskId;
    @SerializedName("detail")
    private String detail;
    @SerializedName("filesize")
    private int fileSize;
    @SerializedName("updtype")
    private int updateType;

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public int getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public int getUpdateType() {
        return updateType;
    }

    public void setUpdateType(int updateType) {
        this.updateType = updateType;
    }

    public int getAfter() {
        if (!TextUtils.isEmpty(detail)) {
            try {
                JSONObject detailObject = new JSONObject(detail);
                return detailObject.getInt("after");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return AFTER_NONE;
    }

    @Override
    public String toString() {
        return "NewVersionBean{" +
                "packageName='" + packageName + '\'' +
                ", version='" + version + '\'' +
                ", info='" + info + '\'' +
                ", taskId='" + taskId + '\'' +
                ", detail='" + detail + '\'' +
                ", fileSize=" + fileSize +
                ", updateType=" + updateType +
                '}';
    }
}
