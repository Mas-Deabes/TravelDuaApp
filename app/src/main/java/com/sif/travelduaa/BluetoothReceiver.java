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
        service.putExtra(DuaaService.EXTRA_CAR, address);
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            Prefs.log(context, "Connected to " + name);
            Prefs.get(context).edit().putLong(Prefs.CONNECTED_AT, System.currentTimeMillis()).apply();
            service.setAction(DuaaService.ACTION_PHONE_CONNECTED);
            Compat.startService(context, service);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            Prefs.log(context, "Disconnected from " + name);
            long since = Prefs.get(context).getLong(Prefs.CONNECTED_AT, 0);
            boolean wasATrip = since > 0 && System.currentTimeMillis() - since >= 2 * 60_000;
            if (Prefs.get(context).getBoolean(Prefs.RETURN_ON, false) && wasATrip) {
                // Trip over: the return duaa plays on the phone (the car is gone).
                service.setAction(DuaaService.ACTION_PHONE_RETURN);
                Compat.startService(context, service);
            } else if (DuaaService.running) {
                service.setAction(DuaaService.ACTION_PHONE_DISCONNECTED);
                context.startService(service);
            }
            Prefs.get(context).edit().remove(Prefs.CONNECTED_AT).apply();
        }
    }
}
