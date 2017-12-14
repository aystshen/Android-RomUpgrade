package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ReqBody implements Serializable {
    @SerializedName("sn")
    @Expose
    private String sn;
    @SerializedName("time")
    @Expose
    private String time;
    @SerializedName("sessionId")
    @Expose
    private String sessionId;
    @SerializedName("data")
    @Expose
    private String data;
    private final static long serialVersionUID = -5406540077737877483L;

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public ReqBody withSn(String sn) {
        this.sn = sn;
        return this;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public ReqBody withTime(String time) {
        this.time = time;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ReqBody withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public ReqBody withData(String data) {
        this.data = data;
        return this;
    }

    @Override
    public String toString() {
        return "ReqBody{" +
                "sn='" + sn + '\'' +
                ", time='" + time + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", data='" + data + '\'' +
                '}';
    }

}