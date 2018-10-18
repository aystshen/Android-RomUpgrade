package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UpgradeRspData implements Serializable {
    @SerializedName("pakgUrl")
    @Expose
    private String pakgUrl;
    @SerializedName("upgradeType")
    @Expose
    private Integer upgradeType;
    @SerializedName("productNo")
    @Expose
    private String productNo;
    @SerializedName("version")
    @Expose
    private Integer version;
    @SerializedName("versionName")
    @Expose
    private String versionName;
    @SerializedName("desc")
    @Expose
    private String desc;
    private final static long serialVersionUID = 669286244262597101L;

    public String getPakgUrl() {
        return pakgUrl;
    }

    public void setPakgUrl(String pakgUrl) {
        this.pakgUrl = pakgUrl;
    }

    public UpgradeRspData withPakgUrl(String pakgUrl) {
        this.pakgUrl = pakgUrl;
        return this;
    }

    public Integer getUpgradeType() {
        return upgradeType;
    }

    public void setUpgradeType(Integer upgradeType) {
        this.upgradeType = upgradeType;
    }

    public UpgradeRspData withUpgradeType(Integer upgradeType) {
        this.upgradeType = upgradeType;
        return this;
    }

    public String getProductNo() {
        return productNo;
    }

    public void setProductNo(String productNo) {
        this.productNo = productNo;
    }

    public UpgradeRspData withProductNo(String productNo) {
        this.productNo = productNo;
        return this;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public UpgradeRspData withVersion(Integer version) {
        this.version = version;
        return this;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public UpgradeRspData withVersionName(String versionName) {
        this.versionName = versionName;
        return this;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public UpgradeRspData withDesc(String desc) {
        this.desc = desc;
        return this;
    }

    @Override
    public String toString() {
        return "UpgradeRspData{" +
                "pakgUrl='" + pakgUrl + '\'' +
                ", upgradeType=" + upgradeType +
                ", productNo='" + productNo + '\'' +
                ", version=" + version +
                ", versionName='" + versionName + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}