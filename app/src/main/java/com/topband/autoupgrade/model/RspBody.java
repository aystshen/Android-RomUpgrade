package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RspBody implements Serializable {

    @SerializedName("status")
    @Expose
    private Integer status;
    @SerializedName("sessionId")
    @Expose
    private String sessionId;
    @SerializedName("message")
    @Expose
    private String message;
    @SerializedName("data")
    @Expose
    private String data;
    private final static long serialVersionUID = -5921132127145004633L;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public RspBody withStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public RspBody withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public RspBody withMessage(String message) {
        this.message = message;
        return this;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public RspBody withData(String data) {
        this.data = data;
        return this;
    }

    @Override
    public String toString() {
        return "RspBody{" +
                "status=" + status +
                ", sessionId='" + sessionId + '\'' +
                ", message='" + message + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}