package com.sif.travelduaa;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone mode: a Bluetooth device connected or disconnected. */
public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.isPhone(context)) return;

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;
        String address = device.getAddress();

        // Only react to the cars the user added. Headphones, speakers etc. are ignored.
        if (!Prefs.isCar(context, address)) return;
        String name = Prefs.cars(context).get(address.toUpperCase());

        Intent service = new Intent(context, DuaaService.class);
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            Prefs.log(context, "Connected to " + name);
            service.setAction(DuaaService.ACTION_PHONE_CONNECTED);
            Compat.startService(context, service);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            Prefs.log(context, "Disconnected from " + name);
            // Only needed if the duaa is waiting or playing right now.
            if (DuaaService.running) {
                service.setAction(DuaaService.ACTION_PHONE_DISCONNECTED);
                context.startService(service);
            }
        }
    }
}
