package com.sif.travelduaa;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home screen widget: one tap plays the selected duaa. */
public class DuaWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        String pkg = c.getPackageName();
        int layout = c.getResources().getIdentifier("widget_play", "layout", pkg);
        int root = c.getResources().getIdentifier("widget_root", "id", pkg);
        if (layout == 0 || root == 0) return;

        Intent play = new Intent(c, DuaaService.class);
        play.setAction(DuaaService.ACTION_PLAY_NOW);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(pkg, layout);
            views.setOnClickPendingIntent(root, Compat.servicePendingIntent(c, play));
            manager.updateAppWidget(id, views);
        }
    }
}
