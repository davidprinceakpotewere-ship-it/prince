package com.stjoseph.phone;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    static { System.loadLibrary("native-lib"); System.loadLibrary("node"); }

    public native int startNodeWithArguments(String[] arguments);

    private static final String REPO = "davidprinceakpotewere-ship-it/prince";
    private static final String VERSION_API =
            "https://api.github.com/repos/" + REPO + "/contents/version.json?ref=main";
    private static final String LOCAL_VERSION = "1.3.0";

    private WebView web;
    private TextView status;
    private Button btnRun, btnUpdate;
    private File projectDir;
    private boolean nodeStarted = false;
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        status = findViewById(R.id.status);
        btnRun = findViewById(R.id.btnRun);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnUpdate.setVisibility(View.GONE);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());

        projectDir = new File(getFilesDir(), "nodejs-project");
        copyAssetsIfNeeded();

        btnRun.setOnClickListener(v -> {
            startNode();
            status.setText("Starting the website... tap again in a few seconds if it is not ready.");
            web.postDelayed(() -> web.loadUrl("http://127.0.0.1:5000/"), 3500);
        });
        btnUpdate.setOnClickListener(v -> installApk());

        new CheckUpdates().execute();
    }

    private void copyAssetsIfNeeded() {
        File marker = new File(projectDir, "server.js");
        if (marker.exists()) return;
        projectDir.mkdirs();
        try {
            copyAssetFolder("", projectDir);
        } catch (Exception e) {
            status.setText("Could not prepare website files: " + e.getMessage());
        }
    }

    private void copyAssetFolder(String path, File dest) throws Exception {
        String[] items = getAssets().list(path);
        if (items == null) {
            if (!dest.exists()) {
                try (InputStream in = getAssets().open(path);
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            return;
        }
        dest.mkdirs();
        for (String item : items) {
            String child = path.isEmpty() ? item : path + "/" + item;
            copyAssetFolder(child, new File(dest, item));
        }
    }

    private void startNode() {
        if (nodeStarted) return;
        nodeStarted = true;
        new Thread(() -> {
            try {
                startNodeWithArguments(new String[]{
                        "node",
                        new File(projectDir, "server.js").getAbsolutePath()
                });
            } catch (Throwable t) {
                runOnUiThread(() -> status.setText("Server error: " + t.getMessage()));
            }
        }, "sj-node").start();
    }

    private class CheckUpdates extends AsyncTask<Void, Void, JSONObject> {
        @Override protected JSONObject doInBackground(Void... v) {
            HttpURLConnection c = null;
            try {
                URL u = new URL(VERSION_API);
                c = (HttpURLConnection) u.openConnection();
                c.setRequestProperty("User-Agent", "StJosephAndroid/1.3.0");
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(20000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                if (code != 200) return null;
                String body = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
                JSONObject wrapper = new JSONObject(body);
                String b64 = wrapper.getString("content").replaceAll("\\s", "");
                byte[] raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                return new JSONObject(new String(raw, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return null;
            } finally { if (c != null) c.disconnect(); }
        }

        @Override protected void onPostExecute(JSONObject data) {
            if (data == null) {
                status.setText("Ready. Could not check online updates — you can still run the website.");
                return;
            }
            String remoteApk = "";
            try {
                JSONObject and = data.getJSONObject("android");
                remoteApk = and.optString("apk_version", "");
                String siteVer = data.optString("version", "");
                if (!siteVer.isEmpty() && !siteVer.equals(readLocalSiteVersion())) {
                    status.setText("Website update v" + siteVer + " available. Tap Run Website to refresh.");
                    new DownloadSite(data.optString("download_url", ""), siteVer).execute();
                } else {
                    status.setText("Ready. Website is up to date (v" + (siteVer.isEmpty() ? LOCAL_VERSION : siteVer) + ").");
                }
                if (!remoteApk.isEmpty() && newer(remoteApk, LOCAL_VERSION)) {
                    btnUpdate.setText("Install update v" + remoteApk);
                    btnUpdate.setVisibility(View.VISIBLE);
                    new DownloadApk(and.optString("apk_url", "")).execute();
                }
            } catch (Exception e) {
                status.setText("Ready. Update check could not be read: " + e.getMessage());
            }
        }
    }

    private boolean newer(String remote, String local) {
        try {
            String[] a = remote.replace("v", "").split("\\.");
            String[] b = local.replace("v", "").split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int x = i < a.length ? Integer.parseInt(a[i].trim()) : 0;
                int y = i < b.length ? Integer.parseInt(b[i].trim()) : 0;
                if (x != y) return x > y;
            }
        } catch (Exception ignored) {}
        return !remote.equals(local);
    }

    private String readLocalSiteVersion() {
        try {
            File f = new File(projectDir, "install_version.txt");
            if (f.exists()) return new String(readAll(new java.io.FileInputStream(f)), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {}
        return LOCAL_VERSION;
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toByteArray();
    }

    private File downloadTo(String url, String name) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "StJosephAndroid/1.3.0");
        c.setConnectTimeout(30000); c.setReadTimeout(60000);
        File out = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
        try (InputStream in = c.getInputStream(); FileOutputStream f = new FileOutputStream(out)) {
            byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) f.write(b, 0, n);
        }
        c.disconnect();
        return out;
    }

    private class DownloadSite extends AsyncTask<String, Void, String> {
        private final String url;
        private final String siteVersion;
        DownloadSite(String url, String siteVersion) { this.url = url; this.siteVersion = siteVersion; }
        @Override protected String doInBackground(String... v) {
            if (url == null || url.isEmpty()) return "No download URL";
            try {
                File zip = downloadTo(url, "site-update.zip");
                File tmp = new File(getFilesDir(), "site-update-tmp");
                if (tmp.exists()) deleteRecursive(tmp);
                tmp.mkdirs();
                try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zip))) {
                    ZipEntry e;
                    while ((e = zis.getNextEntry()) != null) {
                        if (e.isDirectory()) continue;
                        File f = new File(tmp, e.getName());
                        f.getParentFile().mkdirs();
                        try (FileOutputStream fout = new FileOutputStream(f)) {
                            byte[] b = new byte[8192]; int n;
                            while ((n = zis.read(b)) > 0) fout.write(b, 0, n);
                        }
                    }
                }
                // The zip may contain one top-level folder; flatten it if so.
                File[] kids = tmp.listFiles();
                File src = tmp;
                if (kids != null && kids.length == 1 && kids[0].isDirectory()) src = kids[0];
                deleteRecursive(projectDir);
                projectDir.mkdirs();
                copyFolder(src, projectDir);
                try (FileOutputStream f = new FileOutputStream(new File(projectDir, "install_version.txt"))) {
                    f.write(siteVersion.getBytes(StandardCharsets.UTF_8));
                }
                return "ok";
            } catch (Exception e) { return e.getMessage(); }
        }
        @Override protected void onPostExecute(String r) {
            status.setText(r.equals("ok") ? "Website updated. Tap Run Website." : "Website update failed: " + r);
        }
    }

    private void copyFolder(File src, File dst) throws Exception {
        File[] kids = src.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            File d = new File(dst, f.getName());
            if (f.isDirectory()) { d.mkdirs(); copyFolder(f, d); }
            else {
                try (InputStream in = new java.io.FileInputStream(f); FileOutputStream out = new FileOutputStream(d)) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
            }
        }
    }

    private void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    private File apkFile;
    private class DownloadApk extends AsyncTask<String, Void, String> {
        private final String url;
        DownloadApk(String url) { this.url = url; }
        @Override protected String doInBackground(String... v) {
            if (url == null || url.isEmpty()) return "No APK URL";
            try { apkFile = downloadTo(url, "StJosephResultChecker.apk"); return "ok"; }
            catch (Exception e) { return e.getMessage(); }
        }
        @Override protected void onPostExecute(String r) {
            if (!r.equals("ok")) status.setText("APK download failed: " + r);
        }
    }

    private void installApk() {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, "Update is still downloading. Try again shortly.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
