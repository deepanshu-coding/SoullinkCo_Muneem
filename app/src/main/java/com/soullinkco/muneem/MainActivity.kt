package com.soullinkco.muneem

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.os.Message
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val smsPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        Thread { SmsScanner.scan(this); runOnUiThread { pushToPage() } }.start()
    }

    private fun pushToPage() {
        if (::webView.isInitialized) webView.evaluateJavascript("window.__drain&&window.__drain()", null)
    }

    // index.html ko https://appassets.androidplatform.net/ se serve karte hain,
    // taaki localStorage (transactions, invoices) properly persist ho.
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.host == HOST) return false
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                view.evaluateJavascript(SHIM_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(SHIM_JS, null)
            }
        }

        // window.open(...) (jaise WhatsApp link) ko system intent me bhejta hai.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        openExternal(request.url)
                        v.destroy()
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl("https://$HOST/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        Inbox.onNew = { runOnUiThread { pushToPage() } }
        Thread { SmsScanner.scan(this); runOnUiThread { pushToPage() } }.start()
    }

    override fun onPause() {
        Inbox.onNew = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Is link ko kholne wala app nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    /** JS se call hone wale native helpers (file save + share). */
    inner class Bridge {

        @JavascriptInterface
        fun hasSms(): Boolean = SmsScanner.hasPermission(this@MainActivity) &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

        @JavascriptInterface
        fun hasNotifAccess(): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)

        @JavascriptInterface
        fun requestSms() {
            runOnUiThread {
                smsPermLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            }
        }

        @JavascriptInterface
        fun requestNotifAccess() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Settings > Notification access khol kar allow karo", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun drain(): String = Inbox.drain(this@MainActivity)

        @JavascriptInterface
        fun pendingCount(): Int = Inbox.count(this@MainActivity)

        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("insert failed")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Downloads me save ho gaya: $safeName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Save nahi ho paya: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun shareFile(name: String, mime: String, base64: String, text: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, safeName)
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "$packageName.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime.ifEmpty { "application/octet-stream" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(send, "Share invoice"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Share nahi ho paya: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"

        /**
         * Page ka JS blob-download (<a download>) aur navigator.share(files) WebView me
         * kaam nahi karta, isliye inhe native bridge pe redirect karte hain.
         */
        private const val SHIM_JS = """
(function(){
  if (window.__nativeShim) return;
  window.__nativeShim = true;
  function toB64(blob){
    return new Promise(function(res, rej){
      var r = new FileReader();
      r.onload = function(){ res(String(r.result).split(',')[1] || ''); };
      r.onerror = rej;
      r.readAsDataURL(blob);
    });
  }
  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){
    var a = this;
    if (a.hasAttribute('download') && String(a.href).indexOf('blob:') === 0) {
      fetch(a.href).then(function(r){ return r.blob(); }).then(function(b){
        return toB64(b).then(function(d){
          AndroidBridge.saveFile(a.getAttribute('download') || 'file', b.type || 'application/octet-stream', d);
        });
      });
      return;
    }
    return origClick.call(a);
  };
  navigator.canShare = function(d){ return !!(d && d.files && d.files.length); };
  navigator.share = function(d){
    var f = d && d.files && d.files[0];
    if (!f) return Promise.reject(new Error('Nothing to share'));
    return toB64(f).then(function(b64){
      AndroidBridge.shareFile(f.name, f.type || 'application/octet-stream', b64, (d && d.text) || '');
    });
  };
})();
"""
    }
}
