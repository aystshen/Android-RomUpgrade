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

package com.ayst.romupgrade.entity;

import com.ayst.romupgrade.baidu.NewVersionBean;

public class InstallProgress {
    private NewVersionBean info;
    private int progress;
    private boolean isDownloaded;
    private boolean isInstalled;

    public InstallProgress(NewVersionBean info) {
        this.info = info;
        this.progress = 0;
        this.isDownloaded = false;
        this.isInstalled = false;
    }

    public NewVersionBean getInfo() {
        return info;
    }

    public void setInfo(NewVersionBean info) {
        this.info = info;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public void setInstalled(boolean installed) {
        isInstalled = installed;
    }

    @Override
    public String toString() {
        return "InstallProgress{" +
                "info=" + info.toString() +
                ", progress=" + progress +
                ", isDownloaded=" + isDownloaded +
                ", isInstalled=" + isInstalled +
                '}';
    }
}
