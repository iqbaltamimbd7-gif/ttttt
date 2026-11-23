package com.sayma.portal

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.LoginStatusCallback
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var callbackManager: CallbackManager
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val portalUrl = "https://saymabuilders.com/portal/"
    private val permissionRequestCode = 7001
    private val fileRequestCode = 7002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.rgb(16, 24, 39)
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        requestRuntimePermissions()
        FacebookSdk.sdkInitialize(applicationContext)
        callbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    deliverFacebookToken(result.accessToken.token)
                }
                override fun onCancel() {
                    deliverFacebookCancelled()
                }
                override fun onError(error: FacebookException) {
                    deliverFacebookError(error.message ?: "Facebook sign-in failed.")
                }
            })

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.addJavascriptInterface(FacebookBridge(), "AndroidFacebook")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                installNativeFacebookHook(view)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val allowed = request.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }.toTypedArray()
                    if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), fileRequestCode)
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safeInsets.top, 0, safeInsets.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        setContentView(root)
        webView.loadUrl(portalUrl)
    }

    private fun installNativeFacebookHook(view: WebView) {
        view.evaluateJavascript(
            """
            (function(){
              function hook(){
                if(window.FB && !window.FB.__saymaNativeHooked){
                  window.FB.__saymaNativeHooked=true;
                  window.FB.login=function(callback){
                    window.__saymaFacebookCallback=callback;
                    if(window.AndroidFacebook){AndroidFacebook.login();}
                  };
                }
              }
              hook();
              if(!window.__saymaFacebookHookTimer){
                window.__saymaFacebookHookTimer=setInterval(hook,500);
              }
            })();
            """.trimIndent(), null
        )
    }

    private fun deliverFacebookToken(token: String) {
        val quoted = JSONObject.quote(token)
        runOnUiThread {
            webView.evaluateJavascript(
                "if(window.__saymaFacebookCallback){window.__saymaFacebookCallback({authResponse:{accessToken:$quoted}});}",
                null
            )
        }
    }

    private fun deliverFacebookCancelled() {
        runOnUiThread {
            webView.evaluateJavascript(
                "if(window.__saymaFacebookCallback){window.__saymaFacebookCallback({});}",
                null
            )
        }
    }

    private fun deliverFacebookError(message: String) {
        val quoted = JSONObject.quote(message)
        runOnUiThread {
            webView.evaluateJavascript(
                "if(window.__saymaFacebookCallback){window.__saymaFacebookCallback({});}",
                null
            )
        }
    }

    inner class FacebookBridge {
        @JavascriptInterface
        fun login() {
            runOnUiThread {
                LoginManager.getInstance().logInWithReadPermissions(
                    this@MainActivity,
                    listOf("public_profile", "email")
                )
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (callbackManager.onActivityResult(requestCode, resultCode, data)) return
        if (requestCode == fileRequestCode) {
            val result = if (resultCode == RESULT_OK && data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
