package com.topband.autoupgrade.http;

import android.content.Context;
import android.util.Log;

import com.topband.autoupgrade.util.AppUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by shenhaibo on 2017/9/5.
 */

public class HttpHelper {
    private static final String TAG = "HttpHelper";
    private static final int DEFAULT_TIMEOUT = 30;

    private static HttpHelper sHttpHelper;
    private static Context sContext;
    private static HashMap<String, Object> sServiceMap;

    public static HttpHelper instance(Context context) {
        if (null == sHttpHelper) {
            sHttpHelper = new HttpHelper(context);
        }
        return sHttpHelper;
    }

    private HttpHelper(Context context) {
        sServiceMap = new HashMap<>();
        sContext = context;
    }

    @SuppressWarnings("unchecked")
    public <S> S getService(Class<S> serviceClass) {
        if (sServiceMap.containsKey(serviceClass.getName())) {
            return (S) sServiceMap.get(serviceClass.getName());
        } else {
            Object obj = createService(serviceClass);
            sServiceMap.put(serviceClass.getName(), obj);
            return (S) obj;
        }
    }

    @SuppressWarnings("unchecked")
    public <S> S getService(Class<S> serviceClass, OkHttpClient client) {
        if (sServiceMap.containsKey(serviceClass.getName())) {
            return (S) sServiceMap.get(serviceClass.getName());
        } else {
            Object obj = createService(serviceClass, client);
            sServiceMap.put(serviceClass.getName(), obj);
            return (S) obj;
        }
    }

    private <S> S createService(Class<S> serviceClass) {
        //custom OkHttp
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        //time our
        httpClient.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        httpClient.writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        httpClient.readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        //cache
        File httpCacheDirectory = new File(sContext.getCacheDir(), "OkHttpCache");
        httpClient.cache(new Cache(httpCacheDirectory, 10 * 1024 * 1024));
        //Interceptor
        httpClient.addNetworkInterceptor(new LogInterceptor());
        httpClient.addInterceptor(new CacheControlInterceptor());

        return createService(serviceClass, httpClient.build());
    }

    private <S> S createService(Class<S> serviceClass, OkHttpClient client) {
        String baseUrl = "";
        try {
            Field field1 = serviceClass.getField("BASE_URL");
            baseUrl = (String) field1.get(serviceClass);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.getMessage();
            e.printStackTrace();
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        return retrofit.create(serviceClass);
    }

    private class LogInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            long t1 = System.nanoTime();
            Log.i(TAG, String.format("Sending request %s on %s%n%s",
                    request.url(), chain.connection(), request.headers()));

            Response response = chain.proceed(request);
            long t2 = System.nanoTime();

            Log.i(TAG, String.format("Received response for %s in %.1fms%n%s",
                    response.request().url(), (t2 - t1) / 1e6d, response.headers()));
            return response;
        }
    }

    private class CacheControlInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (!AppUtils.isConnNetWork(sContext)) {
                request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build();
            }

            Response response = chain.proceed(request);

            if (AppUtils.isConnNetWork(sContext)) {
                int maxAge = 60 * 60; // read from cache for 1 minute
                response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=" + maxAge)
                        .build();
            } else {
                int maxStale = 60 * 60 * 24 * 28; // tolerate 4-weeks stale
                response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .build();
            }
            return response;
        }
    }
}
