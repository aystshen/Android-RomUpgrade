package com.topband.autoupgrade.http;

import com.topband.autoupgrade.model.ApplyKeyRsp;
import com.topband.autoupgrade.model.ReqBody;
import com.topband.autoupgrade.model.RspBody;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Created by shenhaibo on 2017/9/5.
 */

public interface TopbandApi {
    public final static String BASE_URL = "http://web.topband-cloud.com/api/";

    @POST("applyKey")
    Call<ApplyKeyRsp> getApplyKey(@Body ReqBody reqBody);

    @POST("firmware/upgrade")
    Call<RspBody> getUpgradeInfo(@Body ReqBody reqBody);
}
