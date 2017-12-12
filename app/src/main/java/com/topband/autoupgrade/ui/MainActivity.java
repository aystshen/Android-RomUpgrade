package com.topband.autoupgrade.ui;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.topband.autoupgrade.service.UpdateService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startService(new Intent(this, UpdateService.class));
    }
}
