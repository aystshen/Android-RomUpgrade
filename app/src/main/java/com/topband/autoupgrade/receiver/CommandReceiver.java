package com.topband.autoupgrade.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.topband.autoupgrade.service.CommandService;
import com.topband.autoupgrade.service.UpdateService;

public class CommandReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Intent commandIntent;

        if (CommandService.ACTION_COMMAND.equals(action)) {
            commandIntent = new Intent(context, CommandService.class);
            commandIntent.putExtra("command", intent.getIntExtra("command", CommandService.COMMAND_NULL));
            commandIntent.putExtra("delay", intent.getIntExtra("delay", CommandService.DEFAULT_DELAY_TIME));
            Bundle bundle = intent.getExtras();
            if (null != bundle) {
                commandIntent.putExtras(bundle);
            }
            context.startService(commandIntent);
        }
    }
}
