package com.sif.travelduaa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Head unit mode: the unit has booted or the ignition (ACC) came on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.isHeadUnit(context)) return;
        String action = intent.getAction();
        Prefs.log(context, "Received: " + action);

        Intent service = new Intent(context, DuaaService.class);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // App was just updated: start watching again, but don't play.
            service.setAction(DuaaService.ACTION_HU_WATCH);
        } else {
            service.setAction(DuaaService.ACTION_HU_STARTED);
        }
        Compat.startService(context, service);
    }
}
