package ir.medu.festivalguidelines;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String APP_HOST = "app.local";
    private static final int SAVE_PDF_REQUEST = 405;
    private WebView webView;
    private String pendingSaveFile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new LocalAssetClient());
        webView.addJavascriptInterface(new FileBridge(), "AndroidFiles");
        webView.loadUrl("https://" + APP_HOST + "/splash.html");
    }

    private class FileBridge {
        @JavascriptInterface public void savePdf(String fileName, String title) {
            if (!validPdf(fileName)) return;
            runOnUiThread(() -> {
                pendingSaveFile = fileName;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_TITLE, safeTitle(title) + ".pdf");
                startActivityForResult(intent, SAVE_PDF_REQUEST);
            });
        }

        @JavascriptInterface public void sharePdf(String fileName, String title) {
            if (!validPdf(fileName)) return;
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists() && !dir.mkdirs()) return;
                    File out = new File(dir, fileName);
                    copyAssetToFile("pdfs/" + fileName, out);
                    Uri uri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".files", out);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/pdf");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_SUBJECT, title);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "اشتراک‌گذاری شیوه‌نامه"));
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface public void printPdf(String fileName, String title) {
            if (!validPdf(fileName)) return;
            runOnUiThread(() -> {
                PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
                if (manager != null) manager.print(title, new AssetPdfPrintAdapter(fileName, title), null);
            });
        }
    }

    private class AssetPdfPrintAdapter extends PrintDocumentAdapter {
        private final String fileName;
        private final String title;
        AssetPdfPrintAdapter(String fileName, String title) { this.fileName = fileName; this.title = title; }

        @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                       CancellationSignal cancellationSignal, LayoutResultCallback callback,
                                       Bundle extras) {
            if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(safeTitle(title) + ".pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        }

        @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                                      CancellationSignal cancellationSignal, WriteResultCallback callback) {
            new Thread(() -> {
                try (InputStream in = getAssets().open("pdfs/" + fileName);
                     OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (cancellationSignal.isCanceled()) { callback.onWriteCancelled(); return; }
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) {
                    callback.onWriteFailed("خطا در آماده‌سازی فایل PDF");
                }
            }).start();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_PDF_REQUEST || resultCode != RESULT_OK || data == null ||
                data.getData() == null || !validPdf(pendingSaveFile)) return;
        try (InputStream in = getAssets().open("pdfs/" + pendingSaveFile);
             OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) return;
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        } catch (Exception ignored) { }
    }

    private boolean validPdf(String fileName) {
        return fileName != null && fileName.matches("[a-z]+\\.pdf");
    }

    private String safeTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "شیوه‌نامه جشنواره";
        return title.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
    }

    private void copyAssetToFile(String assetPath, File out) throws Exception {
        try (InputStream in = getAssets().open(assetPath); OutputStream os = new FileOutputStream(out)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
        }
    }

    private class LocalAssetClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!"https".equals(uri.getScheme()) || !APP_HOST.equals(uri.getHost())) {
                return super.shouldInterceptRequest(view, request);
            }
            String path = uri.getPath();
            if (path == null) return notFound();
            path = Uri.decode(path);
            while (path.startsWith("/")) path = path.substring(1);
            if (path.isEmpty()) path = "index.html";
            if (path.contains("..") || path.contains("\\")) return notFound();
            try {
                InputStream input = getAssets().open(path);
                return new WebResourceResponse(mimeType(path), encoding(path), input);
            } catch (Exception ignored) {
                return notFound();
            }
        }

        private WebResourceResponse notFound() {
            return new WebResourceResponse("text/plain", "UTF-8", 404,
                    "Not Found", null, new ByteArrayInputStream(new byte[0]));
        }

        private String mimeType(String path) {
            String p = path.toLowerCase(Locale.ROOT);
            if (p.endsWith(".html")) return "text/html";
            if (p.endsWith(".css")) return "text/css";
            if (p.endsWith(".js")) return "application/javascript";
            if (p.endsWith(".json")) return "application/json";
            if (p.endsWith(".pdf")) return "application/pdf";
            if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
            if (p.endsWith(".png")) return "image/png";
            return "application/octet-stream";
        }

        private String encoding(String path) {
            String p = path.toLowerCase(Locale.ROOT);
            return (p.endsWith(".html") || p.endsWith(".css") ||
                    p.endsWith(".js") || p.endsWith(".json")) ? "UTF-8" : null;
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
