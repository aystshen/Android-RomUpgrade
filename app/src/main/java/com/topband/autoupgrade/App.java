package com.topband.autoupgrade;

import android.app.Application;

import com.liulishuo.filedownloader.FileDownloader;

/**
 * Created by Administrator on 2017/12/13.
 */

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FileDownloader.setup(this);
    }
}
