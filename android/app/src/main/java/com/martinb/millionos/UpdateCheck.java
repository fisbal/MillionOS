package com.martinb.millionos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

/** On start, asks GitHub for the newest MillionOS release and offers it when it is newer than this app. */
final class UpdateCheck {
    private static final String LATEST = "https://api.github.com/repos/fisbal/MillionOS/releases/latest";
    private static final String APK = "https://github.com/fisbal/MillionOS/releases/latest/download/MillionOS.apk";

    private UpdateCheck() {}

    static void run(final Activity a) {
        final int mine = buildOf(versionName(a));
        if (mine <= 1) return; // local builds have no number to compare
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(LATEST).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "MillionOS-Android");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                String body;
                try (InputStream in = c.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                    body = sc.hasNext() ? sc.next() : "";
                }
                final String tag = new JSONObject(body).optString("tag_name", "");
                if (buildOf(tag) <= mine) return;
                new Handler(Looper.getMainLooper()).post(() -> offer(a, tag.replaceFirst("^v", "")));
            } catch (Exception ignored) {
                // Offline or GitHub unreachable: try again next time.
            }
        }).start();
    }

    private static void offer(Activity a, String version) {
        if (a.isFinishing()) return;
        boolean fr = Locale.getDefault().getLanguage().equals("fr");
        new AlertDialog.Builder(a)
                .setTitle(fr ? "Nouvelle version de MillionOS" : "New MillionOS version")
                .setMessage(fr ? "L’app MillionOS " + version + " est prête. Ta progression est gardée."
                        : "The MillionOS app " + version + " is ready. Your progress is kept.")
                .setPositiveButton(fr ? "Mettre à jour" : "Update", (d, w) ->
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK))))
                .setNegativeButton(fr ? "Plus tard" : "Later", null)
                .show();
    }

    private static String versionName(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** "1.0.42" or "v1.0.42" → 42. */
    static int buildOf(String v) {
        if (v == null) return 0;
        String[] parts = v.replaceFirst("^v", "").split("\\.");
        try {
            return Integer.parseInt(parts[parts.length - 1].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
