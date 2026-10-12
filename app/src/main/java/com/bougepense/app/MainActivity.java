package com.bougepense.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_FILE = 42;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF6C5CE7);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.FRANCE);
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.1f);
            }
        });

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "Choisir la sauvegarde"), PICK_FILE);
                return true;
            }
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                    .setPositiveButton("OK", (d, w) -> r.confirm()).setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                    .setPositiveButton("Oui", (d, w) -> r.confirm())
                    .setNegativeButton("Non", (d, w) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
            @Override
            public boolean onJsPrompt(WebView v, String url, String msg, String def, JsPromptResult r) {
                final EditText input = new EditText(MainActivity.this);
                if (def != null) input.setText(def);
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setView(input)
                    .setPositiveButton("OK", (d, w) -> r.confirm(input.getText().toString()))
                    .setNegativeButton("Annuler", (d, w) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
    }

    class Bridge {
        @JavascriptInterface public boolean canSpeak() { return ttsReady; }
        @JavascriptInterface public void speak(String text) {
            if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bp");
        }
        @JavascriptInterface public void stop() { if (tts != null) tts.stop(); }
        @JavascriptInterface public void share(String title, String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, title);
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, title));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILE && fileCallback != null) {
            Uri[] r = (res == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(r);
            fileCallback = null;
        } else super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.androidBack ? androidBack() : 'exit'", v -> {
            if (v != null && v.contains("exit")) finish();
        });
    }

    @Override
    protected void onPause() { super.onPause(); if (tts != null) tts.stop(); }

    @Override
    protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }
}
