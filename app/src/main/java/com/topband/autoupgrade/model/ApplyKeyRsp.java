
package com.topband.autoupgrade.model;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApplyKeyRsp implements Serializable {

    @SerializedName("status")
    @Expose
    private Integer status;
    @SerializedName("data")
    @Expose
    private String data;
    private final static long serialVersionUID = 4883031359397913373L;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public ApplyKeyRsp withStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public ApplyKeyRsp withData(String data) {
        this.data = data;
        return this;
    }

    @Override
    public String toString() {
        return "ApplyKeyRsp{" +
                "status=" + status +
                ", data='" + data + '\'' +
                '}';
    }

}