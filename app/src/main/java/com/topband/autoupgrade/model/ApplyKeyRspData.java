package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApplyKeyRspData implements Serializable {

    @SerializedName("sessionId")
    @Expose
    private String sessionId;
    @SerializedName("key")
    @Expose
    private String key;
    private final static long serialVersionUID = -5154298021210999586L;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ApplyKeyRspData withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public ApplyKeyRspData withKey(String key) {
        this.key = key;
        return this;
    }

    @Override
    public String toString() {
        return "ApplyKeyRspData{" +
                "sessionId='" + sessionId + '\'' +
                ", key='" + key + '\'' +
                '}';
    }
}