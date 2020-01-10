package com.ayst.romupgrade.util.filecopy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/**
 * Asynchronous task to copy a file.
 *
 * @author ayst.shen@foxmail.com
 */
public class FileCopyTask extends AsyncTask<FileCopyTaskParam, Integer, Void> {
    private static final String TAG = "FileCopyTask";

    private WeakReference<Context> mWeakContext;
    private FileCopyTaskParam mParam;

    public FileCopyTask(Context context) {
        mWeakContext = new WeakReference<>(context);
    }

    @Override
    protected void onPreExecute() {
    }

    private void copyFile(DocumentFile from, DocumentFile to) throws Exception {
        long size = from.length();

        if (mWeakContext.get() != null) {
            InputStream inputStream = mWeakContext.get().getContentResolver().openInputStream(from.getUri());
            OutputStream outputStream = mWeakContext.get().getContentResolver().openOutputStream(to.getUri());

            if (null != inputStream && null != outputStream) {
                byte[] bytes = new byte[1024];
                int count;
                long total = 0;

                while ((count = inputStream.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, count);
                    if (size > 0) {
                        total += count;
                        publishProgress((int) (total * 100 / size));
                    }
                }

                outputStream.close();
                inputStream.close();
            } else {
                throw new IOException("InputStream or OutputStream is null");
            }
        } else {
            throw new Exception("Context is null");
        }
    }

    @Override
    protected Void doInBackground(FileCopyTaskParam... params) {
        long time = System.currentTimeMillis();
        mParam = params[0];
        try {
            copyFile(DocumentFile.fromFile(mParam.from), DocumentFile.fromFile(mParam.to));
        } catch (Exception e) {
            Log.e(TAG, "could not copy file, e: ", e);
            if (null != mParam.listener) {
                mParam.listener.error(e);
            }
        }

        Log.i(TAG, "copy time: " + (System.currentTimeMillis() - time));
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        if (null != mParam.listener) {
            mParam.listener.completed(mParam.to);
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (null != mParam.listener) {
            mParam.listener.progress(values[0]);
        }
    }
}
