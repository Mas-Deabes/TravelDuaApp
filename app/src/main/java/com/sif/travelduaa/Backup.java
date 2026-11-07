package com.sif.travelduaa;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backup and restore: one .zip file with every setting and every recording added on the device.
 *   settings.json      all settings (not the event log)
 *   files/rec_…        recordings added or recorded in the app
 */
public class Backup {

    /** Returns how many recordings were saved. */
    public static int write(Context c, OutputStream os) throws Exception {
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("settings.json"));
            zip.write(settingsJson(c).toString(2).getBytes("UTF-8"));
            zip.closeEntry();

            File[] files = c.getFilesDir().listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().startsWith("rec_")) continue;
                    zip.putNextEntry(new ZipEntry("files/" + f.getName()));
                    try (InputStream in = new FileInputStream(f)) { copy(in, zip); }
                    zip.closeEntry();
                    count++;
                }
            }
        }
        return count;
    }

    /** Replaces settings and adds the recordings from the backup. Returns how many recordings. */
    public static int read(Context c, InputStream is) throws Exception {
        int count = 0;
        JSONObject settings = null;
        try (ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("settings.json")) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    copy(zip, b);
                    settings = new JSONObject(b.toString("UTF-8"));
                } else if (name.startsWith("files/rec_") && !name.contains("..")) {
                    File out = new File(c.getFilesDir(), name.substring("files/".length()));
                    try (OutputStream os = new FileOutputStream(out)) { copy(zip, os); }
                    count++;
                }
            }
        }
        if (settings == null) throw new Exception("This isn't a Travel Duaa backup.");
        restoreSettings(c, settings);
        return count;
    }

    private static JSONObject settingsJson(Context c) throws Exception {
        JSONObject root = new JSONObject();
        for (Map.Entry<String, ?> e : Prefs.get(c).getAll().entrySet()) {
            if (e.getKey().equals(Prefs.LOG)) continue;
            Object v = e.getValue();
            JSONObject item = new JSONObject();
            if (v instanceof Boolean) item.put("t", "b");
            else if (v instanceof Integer) item.put("t", "i");
            else if (v instanceof Long) item.put("t", "l");
            else if (v instanceof Float) item.put("t", "f");
            else if (v instanceof Set) {
                item.put("t", "set");
                v = new JSONArray((Set<?>) v);
            } else item.put("t", "s");
            item.put("v", v);
            root.put(e.getKey(), item);
        }
        return root;
    }

    private static void restoreSettings(Context c, JSONObject root) throws Exception {
        SharedPreferences.Editor ed = Prefs.get(c).edit();
        String log = Prefs.get(c).getString(Prefs.LOG, "");
        ed.clear();
        ed.putString(Prefs.LOG, log);
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject item = root.getJSONObject(k);
            switch (item.getString("t")) {
                case "b": ed.putBoolean(k, item.getBoolean("v")); break;
                case "i": ed.putInt(k, item.getInt("v")); break;
                case "l": ed.putLong(k, item.getLong("v")); break;
                case "f": ed.putFloat(k, (float) item.getDouble("v")); break;
                case "set": {
                    JSONArray a = item.getJSONArray("v");
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    ed.putStringSet(k, set);
                    break;
                }
                default: ed.putString(k, item.getString("v"));
            }
        }
        ed.apply();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
