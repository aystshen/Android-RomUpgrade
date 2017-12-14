package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApplyKeyReqData implements Serializable {

    @SerializedName("source")
    @Expose
    private String source;
    @SerializedName("companyId")
    @Expose
    private String companyId;
    @SerializedName("userName")
    @Expose
    private String userName;
    @SerializedName("softwareVersion")
    @Expose
    private String softwareVersion;
    @SerializedName("sysVersion")
    @Expose
    private String sysVersion;
    @SerializedName("hardwareVersion")
    @Expose
    private String hardwareVersion;
    @SerializedName("language")
    @Expose
    private String language;
    private final static long serialVersionUID = 505439817402688960L;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public ApplyKeyReqData withSource(String source) {
        this.source = source;
        return this;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public ApplyKeyReqData withCompanyId(String companyId) {
        this.companyId = companyId;
        return this;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public ApplyKeyReqData withUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public ApplyKeyReqData withSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
        return this;
    }

    public String getSysVersion() {
        return sysVersion;
    }

    public void setSysVersion(String sysVersion) {
        this.sysVersion = sysVersion;
    }

    public ApplyKeyReqData withSysVersion(String sysVersion) {
        this.sysVersion = sysVersion;
        return this;
    }

    public String getHardwareVersion() {
        return hardwareVersion;
    }

    public void setHardwareVersion(String hardwareVersion) {
        this.hardwareVersion = hardwareVersion;
    }

    public ApplyKeyReqData withHardwareVersion(String hardwareVersion) {
        this.hardwareVersion = hardwareVersion;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public ApplyKeyReqData withLanguage(String language) {
        this.language = language;
        return this;
    }

    @Override
    public String toString() {
        return "ApplyKeyReqData{" +
                "source='" + source + '\'' +
                ", companyId='" + companyId + '\'' +
                ", userName='" + userName + '\'' +
                ", softwareVersion='" + softwareVersion + '\'' +
                ", sysVersion='" + sysVersion + '\'' +
                ", hardwareVersion='" + hardwareVersion + '\'' +
                ", language='" + language + '\'' +
                '}';
    }
}