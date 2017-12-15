package com.topband.autoupgrade.http;

import android.content.Context;
import android.util.Log;

import com.topband.autoupgrade.model.ApplyKeyReqData;
import com.topband.autoupgrade.model.ApplyKeyRsp;
import com.topband.autoupgrade.model.ApplyKeyRspData;
import com.topband.autoupgrade.model.ReqBody;
import com.topband.autoupgrade.util.AppUtils;
import com.topband.autoupgrade.util.DataEncryptUtil;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by Administrator on 2017/12/13.
 */

public class SessionIDManager {
    private static final String TAG = "SessionIDManager";

    private static final String DEFAULT_KEY = "4kCdjpAr1ym50dQE";

    private static Context sContext = null;
    private static SessionIDManager sSessionIDManager = null;
    private static ApplyKeyRspData sData = null;

    private SessionIDManager(Context context) {
        sContext = context;
    }

    public static SessionIDManager instance(Context context) {
        if (null == sSessionIDManager) {
            sSessionIDManager = new SessionIDManager(context);
        }
        return sSessionIDManager;
    }

    public String getSessionID() {
        if (null != sData) {
            return sData.getSessionId();
        }
        return "";
    }

    public String getKey() {
        if (null != sData) {
            return sData.getKey();
        }
        return DEFAULT_KEY;
    }

    public synchronized void requestSessionID(final SessionIDListener listener) {
        ApplyKeyReqData reqData = new ApplyKeyReqData().withSource("AutoUpgrade")
                .withCompanyId("topband")
                .withUserName("")
                .withSoftwareVersion(AppUtils.getVersionName(sContext))
                .withSysVersion(AppUtils.getAndroidVersion())
                .withHardwareVersion(AppUtils.getHwVersionName())
                .withLanguage("english");
        String data = DataEncryptUtil.encryptData(reqData, getKey());
        ReqBody reqBody = new ReqBody().withSn(AppUtils.getUUID())
                .withTime(String.valueOf(System.currentTimeMillis()))
                .withSessionId(getSessionID())
                .withData(data);
        Log.d(TAG, "requestSessionID, " + reqBody.toString() + ", " + reqData.toString());
        HttpHelper.instance(sContext).getService(TopbandApi.class)
                .getApplyKey(reqBody)
                .enqueue(new Callback<ApplyKeyRsp>() {
                    @Override
                    public void onResponse(Call<ApplyKeyRsp> call, Response<ApplyKeyRsp> response) {
                        ApplyKeyRsp applyKeyRsp = response.body();
                        if (null != applyKeyRsp) {
                            Log.i(TAG, "requestSessionID->onResponse, " + applyKeyRsp.toString());
                            if (applyKeyRsp.getStatus() == 0) {
                                // success
                                sData = DataEncryptUtil.decryptData(applyKeyRsp.getData(), ApplyKeyRspData.class, getKey());
                                if (null != sData) {
                                    Log.d(TAG, "requestSessionID->onResponse, sData=" + sData.toString());
                                    if (null != listener) {
                                        listener.onSuccess(sData);
                                    }
                                    return;
                                } else {
                                    Log.e(TAG, "requestSessionID->onResponse, sData is null!");
                                }
                            }
                        } else {
                            Log.e(TAG, "requestSessionID->onResponse, body is null!");
                        }
                        if (null != listener) {
                            listener.onFailed((null != applyKeyRsp) ? applyKeyRsp.getStatus() : -1);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApplyKeyRsp> call, Throwable t) {
                        Log.e(TAG, "requestSessionID->onFailure");
                        if (null != listener) {
                            listener.onFailed(1);
                        }
                    }
                });
    }

    public interface SessionIDListener {
        public void onSuccess(ApplyKeyRspData data);

        public void onFailed(int errCode);
    }
}
