package com.sif.travelduaa;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * In-app updates from the project's GitHub Releases.
 *
 * The GitHub build sets the repo name (e.g. "sif/TravelDuaApp") into the app, and names each
 * release "build-N". The app compares N with its own build number, downloads TravelDuaa.apk,
 * and hands it to Android's installer, which asks the user to confirm.
 *
 * Needs a public repo: GitHub doesn't let apps read releases of a private repo without a token.
 */
public class Updater {

    public interface Result { void done(Release release, String error); }

    public static class Release {
        public int build;
        public String apkUrl;
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** "owner/repo", set by the GitHub build. Empty for local builds. */
    public static String repo(Context c) {
        int id = c.getResources().getIdentifier("github_repo", "string", c.getPackageName());
        return id == 0 ? "" : c.getString(id);
    }

    @SuppressWarnings("deprecation")
    public static int currentBuild(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Checks GitHub in the background; calls back on the main thread. */
    public static void check(Context c, Result cb) {
        Context app = c.getApplicationContext();
        String repo = repo(app);
        if (repo.isEmpty()) {
            cb.done(null, "This copy was built on your PC, not by GitHub, so it can't check for updates.");
            return;
        }
        new Thread(() -> {
            Release r = null;
            String err = null;
            try {
                HttpURLConnection con = open("https://api.github.com/repos/" + repo + "/releases/latest");
                con.setRequestProperty("Accept", "application/vnd.github+json");
                int code = con.getResponseCode();
                if (code == 404) {
                    err = "No releases found. Updates need the GitHub repo to be public.";
                } else if (code != 200) {
                    err = "GitHub answered " + code + ". Try again later.";
                } else {
                    JSONObject json = new JSONObject(readAll(con.getInputStream()));
                    r = new Release();
                    r.build = Integer.parseInt(("0" + json.optString("tag_name", "").replaceAll("[^0-9]", "")));
                    JSONArray assets = json.optJSONArray("assets");
                    for (int i = 0; assets != null && i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name").endsWith(".apk")) r.apkUrl = a.optString("browser_download_url");
                    }
                    if (r.apkUrl == null) err = "The latest release has no APK attached.";
                }
                con.disconnect();
            } catch (Exception e) {
                err = "Couldn't reach GitHub. Check the internet connection.";
            }
            Release fr = err == null ? r : null;
            String fe = err;
            if (fr != null) {
                Prefs.get(app).edit()
                        .putLong(Prefs.UPDATE_CHECKED, System.currentTimeMillis())
                        .putInt(Prefs.UPDATE_AVAILABLE, fr.build > currentBuild(app) ? fr.build : 0)
                        .apply();
            }
            MAIN.post(() -> cb.done(fr, fe));
        }).start();
    }

    public interface Progress { void update(int percent); }

    /** Downloads the APK and starts Android's installer. Progress and result on the main thread. */
    public static void downloadAndInstall(Context c, Release r, Progress progress, Result cb) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                File apk = new File(app.getCacheDir(), "update.apk");
                HttpURLConnection con = open(r.apkUrl);
                int total = con.getContentLength();
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[64 * 1024];
                    int n, done = 0, lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100L / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                MAIN.post(() -> progress.update(pct));
                            }
                        }
                    }
                }
                con.disconnect();
                install(app, apk);
                Prefs.log(app, "Update downloaded (build " + r.build + "), asking Android to install it");
            } catch (Exception e) {
                err = "Download failed: " + e.getMessage();
            }
            String fe = err;
            MAIN.post(() -> cb.done(r, fe));
        }).start();
    }

    /** Hands the APK to Android's package installer. Android shows its own "Install?" screen. */
    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream out = s.openWrite("travelduaa", 0, apk.length());
                 InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent done = new Intent(c, InstallResult.class);
            PendingIntent p = PendingIntent.getBroadcast(c, 7, done,
                    PendingIntent.FLAG_UPDATE_CURRENT | Compat.mutableFlag());
            s.commit(p.getIntentSender());
        }
    }

    /** Android reports back here: either "ask the user" or the final result. */
    public static class InstallResult extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirm);
                    } catch (Exception e) {
                        Prefs.log(context, "Couldn't show the install screen: " + e.getMessage());
                    }
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                Prefs.log(context, "Update installed");
            } else {
                Prefs.log(context, "Update not installed: "
                        + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setRequestProperty("User-Agent", "TravelDuaa-app");
        // GitHub sends downloads to another host; follow that redirect by hand if needed.
        int code = con.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String next = con.getHeaderField("Location");
            con.disconnect();
            return open(next);
        }
        return con;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
