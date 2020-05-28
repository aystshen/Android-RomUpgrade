/*
 * Copyright(c) 2020 Bob Shen <ayst.shen@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ayst.romupgrade.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ayst.romupgrade.R;
import com.ayst.romupgrade.entity.InstallProgress;

import java.util.List;

public class DownloadAdapter extends BaseAdapter {
    private LayoutInflater mInflater = null;
    private List<InstallProgress> mData = null;

    public DownloadAdapter(Context context, List<InstallProgress> data) {
        mInflater = LayoutInflater.from(context);
        mData = data;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < mData.size()) {
            return mData.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ViewHolder holder;
        if (view == null) {
            view = mInflater.inflate(R.layout.layout_download_item, null);
            holder = new ViewHolder();
            holder.mTitleTv = (TextView) view.findViewById(R.id.title);
            holder.mDownloadPgr = (ProgressBar) view.findViewById(R.id.progress);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        holder.mTitleTv.setText(mData.get(position).getInfo().getPackageName() +
                " (" + mData.get(position).getInfo().getVersion() + ")");
        holder.mDownloadPgr.setProgress(mData.get(position).getProgress());

        return view;
    }

    public void update(List<InstallProgress> data) {
        mData = data;
        notifyDataSetChanged();
    }

    private final class ViewHolder {
        private TextView mTitleTv = null;
        private ProgressBar mDownloadPgr = null;
    }
}
