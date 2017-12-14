package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UpgradeReqData implements Serializable {

    @SerializedName("mac")
    @Expose
    private String mac;
    @SerializedName("productId")
    @Expose
    private String productId;
    @SerializedName("version")
    @Expose
    private Integer version;
    @SerializedName("type")
    @Expose
    private Integer type;
    private final static long serialVersionUID = 8892392628582388570L;

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public UpgradeReqData withMac(String mac) {
        this.mac = mac;
        return this;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public UpgradeReqData withProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public UpgradeReqData withVersion(Integer version) {
        this.version = version;
        return this;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public UpgradeReqData withType(Integer type) {
        this.type = type;
        return this;
    }

    @Override
    public String toString() {
        return "UpgradeReqData{" +
                "mac='" + mac + '\'' +
                ", productId='" + productId + '\'' +
                ", version=" + version +
                ", type=" + type +
                '}';
    }
}