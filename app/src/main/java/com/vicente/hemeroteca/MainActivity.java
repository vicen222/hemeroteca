package com.vicente.hemeroteca;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_CAM = 11;
    private static final int REQ_GAL = 12;

    private WebView web;
    private Uri camUri;
    private volatile String pending; // texto leído que la página aún no ha recogido

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Window w = getWindow();
        int bg = dark ? Color.parseColor("#0f1114") : Color.parseColor("#f2f2ee");
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (!dark) {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        web = new WebView(this);
        web.setBackgroundColor(bg);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // historial y RM guardados en el móvil
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u)); // enlaces externos al navegador
                return true;
            }
        });

        setContentView(web);
        if (savedInstanceState != null) {
            String cu = savedInstanceState.getString("camUri");
            if (cu != null) camUri = Uri.parse(cu);
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    /** Funciones que la página puede llamar como window.Android.xxx */
    private class Bridge {
        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void saveFile(final String name, final String b64, final String mime) {
            runOnUiThread(() -> doSave(name, b64, mime));
        }

        @JavascriptInterface
        public String takePending() {
            String p = pending;
            pending = null;
            return p == null ? "" : p;
        }

        @JavascriptInterface
        public void pickPhoto(final String source) {
            runOnUiThread(() -> startPick(source));
        }
    }

    /* ---------- Foto de la pizarra ---------- */

    private void startPick(String source) {
        try {
            if ("camera".equals(source)) {
                File dir = new File(getCacheDir(), "fotos");
                dir.mkdirs();
                File f = new File(dir, "pizarra_" + System.currentTimeMillis() + ".jpg");
                camUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                i.setClipData(ClipData.newRawUri("foto", camUri));
                i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(i, REQ_CAM);
            } else {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(i, "Elige la foto de la pizarra"), REQ_GAL);
            }
        } catch (Exception e) {
            jsError("No se pudo abrir la cámara o la galería: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAM && requestCode != REQ_GAL) return;
        if (resultCode != RESULT_OK) {
            jsError("cancel");
            return;
        }
        Uri uri = requestCode == REQ_CAM ? camUri : (data != null ? data.getData() : null);
        if (uri == null) {
            jsError("No ha llegado ninguna foto.");
            return;
        }
        runOcr(uri);
    }

    private void runOcr(Uri uri) {
        try {
            final InputImage img = InputImage.fromFilePath(this, uri);
            TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            rec.process(img)
                    .addOnSuccessListener(text -> sendText(img, text))
                    .addOnFailureListener(e -> jsError("No se pudo leer la foto: " + e.getMessage()));
        } catch (Exception e) {
            jsError("No se pudo abrir la foto: " + e.getMessage());
        }
    }

    private void sendText(InputImage img, Text text) {
        try {
            boolean rotated = img.getRotationDegrees() == 90 || img.getRotationDegrees() == 270;
            JSONObject o = new JSONObject();
            o.put("w", rotated ? img.getHeight() : img.getWidth());
            o.put("h", rotated ? img.getWidth() : img.getHeight());
            JSONArray arr = new JSONArray();
            for (Text.TextBlock b : text.getTextBlocks()) {
                for (Text.Line l : b.getLines()) {
                    JSONObject j = new JSONObject();
                    j.put("t", l.getText());
                    Rect r = l.getBoundingBox();
                    if (r != null) {
                        j.put("x", r.left);
                        j.put("y", r.top);
                        j.put("r", r.right);
                        j.put("b", r.bottom);
                    }
                    arr.put(j);
                }
            }
            o.put("lines", arr);
            pending = o.toString();
            web.evaluateJavascript("window.onPizarra&&window.onPizarra(" + o + ")", null);
        } catch (Exception e) {
            jsError("Error procesando el texto: " + e.getMessage());
        }
    }

    private void jsError(String msg) {
        web.evaluateJavascript("window.onPizarraError&&window.onPizarraError(" + JSONObject.quote(msg) + ")", null);
    }

    /* ---------- Exportar a Excel ---------- */

    private void doSave(String name, String b64, String mime) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            Uri uri = null;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hemeroteca");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(data);
                }
                Toast.makeText(this, "Guardado en Descargas/Hemeroteca", Toast.LENGTH_LONG).show();
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
                Toast.makeText(this, "Guardado en " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
            if (uri != null) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Enviar o guardar el Excel"));
            }
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (camUri != null) outState.putString("camUri", camUri.toString());
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
