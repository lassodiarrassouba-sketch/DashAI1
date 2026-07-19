package com.dashai.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class SitePreviewActivity extends AppCompatActivity {
    public static final String EXTRA_FILE_PATH = "site_file_path";
    public static final String EXTRA_SITE_TITLE = "site_title";
    private static final int REQUEST_SAVE_HTML = 901;

    private String html;
    private String siteTitle;
    private WebView webView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        siteTitle = cleanTitle(getIntent().getStringExtra(EXTRA_SITE_TITLE));
        html = readGeneratedHtml(getIntent().getStringExtra(EXTRA_FILE_PATH));
        if (html == null || html.trim().isEmpty()) {
            Toast.makeText(this, "Le site généré est introuvable.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(8), dp(8), dp(8));
        toolbar.setBackgroundColor(Color.rgb(243, 246, 245));

        MaterialButton backButton = iconButton(R.drawable.ic_back, "Retour");
        backButton.setIconTint(ColorStateList.valueOf(Color.rgb(23, 33, 38)));
        backButton.setOnClickListener(view -> finish());
        toolbar.addView(backButton, fixed(dp(48), dp(48)));

        TextView titleView = new TextView(this);
        titleView.setText(siteTitle);
        titleView.setTextSize(17);
        titleView.setTextColor(Color.rgb(23, 33, 38));
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(6);
        toolbar.addView(titleView, titleParams);

        MaterialButton downloadButton = new MaterialButton(this);
        downloadButton.setText("HTML");
        downloadButton.setIconResource(R.drawable.ic_download);
        downloadButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
        downloadButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(0, 143, 114)));
        downloadButton.setTextColor(Color.WHITE);
        downloadButton.setCornerRadius(dp(8));
        downloadButton.setOnClickListener(view -> requestSaveHtml());
        toolbar.addView(downloadButton, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.enableSlowWholeDocumentDraw();
            settings.setSafeBrowsingEnabled(true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                if (uri == null || uri.getScheme() == null) return false;
                String scheme = uri.getScheme();
                if (!scheme.equals("http") && !scheme.equals("https")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(SitePreviewActivity.this, "Aucun navigateur disponible.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType,
                                          SafeBrowsingResponse callback) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && callback != null) {
                    callback.backToSafety(true);
                }
            }
        });
        webView.loadDataWithBaseURL("https://diasco.local/", html, "text/html", "UTF-8", null);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private MaterialButton iconButton(int icon, String description) {
        MaterialButton button = new MaterialButton(this);
        button.setText(null);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setContentDescription(description);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(description);
        return button;
    }

    private void requestSaveHtml() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/html")
                .putExtra(Intent.EXTRA_TITLE, fileName(siteTitle) + ".html");
        try {
            startActivityForResult(intent, REQUEST_SAVE_HTML);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Aucun gestionnaire de fichiers disponible.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAVE_HTML || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IOException("Flux de sortie indisponible");
            output.write(html.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Site HTML enregistré.", Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, "Impossible d’enregistrer le site.", Toast.LENGTH_LONG).show();
        }
    }

    private String readGeneratedHtml(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return null;
        File file = new File(rawPath);
        try {
            String cachePath = getCacheDir().getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(cachePath + File.separator) || !file.isFile()) return null;
        } catch (IOException exception) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        try (InputStream input = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        } catch (IOException exception) {
            return null;
        }
        return builder.toString();
    }

    private String cleanTitle(String raw) {
        String clean = raw == null ? "" : raw.trim();
        return clean.isEmpty() ? "Site créé par DIASCO" : clean;
    }

    private String fileName(String raw) {
        String clean = raw.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return clean.isEmpty() ? "site-diasco" : clean;
    }

    private LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
