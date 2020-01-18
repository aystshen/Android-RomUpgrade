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
}
