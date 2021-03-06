package com.punkytails.cocoboli

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActionBar
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import android.os.Parcelable
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import java.io.File


class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_BRIDGE_EVENT = "action_bridge_event"
        const val EXTRA_BRIDGE_EVENT = "extra_bridge_event"
        const val EXTRA_FINISH_APP = "finish_app"
        const val EXTRA_FINISH_SPLASH = "finish_splash"
        private const val REQUEST_PERMISSIONS = 1000
    }
    // camera permission
    val REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_ENABLED = 1000
    val REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_DISABLED =  1001

    // location permission
    val REQUEST_ACCESS_FINE_LOCATION = 100
    val REQUEST_ACCESS_GPS_LOCATION = 2001

    //file setting
    var filePathCallbackNormal: ValueCallback<Uri>? = null
    var filePathCallbackLollipop: ValueCallback<Array<Uri?>?>? = null
    val FILECHOOSER_NORMAL_REQ_CODE = 2001
    val FILECHOOSER_LOLLIPOP_REQ_CODE = 2002
    private var cameraImageUri: Uri? = null

    // permission list
    var REQUIRED_PERMISSIONS = arrayOf<String>(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    lateinit var gpsTracker: GpsTracker

    lateinit var parentLayout: ViewGroup
    lateinit var webView: WebView
    private var popUpWebView: WebView? = null

    private var startTime: Long = 0

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action == ACTION_BRIDGE_EVENT) {
                when(intent.getStringExtra(EXTRA_BRIDGE_EVENT)) {
                    EXTRA_FINISH_SPLASH -> {

                    }
                    EXTRA_FINISH_APP -> {
                        finish()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.statusBarColor = Color.parseColor("#ffffff")
        if(Build.VERSION.SDK_INT >= 23){
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        //startActivity(Intent(this, MainActivity::class.java))
        startActivity(Intent(this, SplashActivity::class.java))
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(ACTION_BRIDGE_EVENT))
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContentView(R.layout.activity_main)
        parentLayout = findViewById(R.id.parent_layout)
        webView = findViewById(R.id.webView)

        var intentData = intent.dataString
        if(intentData != null) {
            intentData = intentData.removePrefix("cocoboli://cocoboli.com/")
            intentData = intentData.replace("/", "=")
        }

        //timer
        startTime = System.currentTimeMillis()

        // webview settings
        configureWebView(webView)

        // webview detail setting
        WebView.setWebContentsDebuggingEnabled(true)
        val androidBridge = AndroidBridge(webView, this@MainActivity)
        webView.webChromeClient = ParentWebChromeClient(parentLayout, this)
        webView.addJavascriptInterface(androidBridge, "android")
        if(intentData != null) {
            webView.loadUrl("https://cocoboli.com/main?$intentData")
        } else {
            webView.loadUrl("https://cocoboli.com/main")
        }
    }
    private fun checkPermissions() {
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSIONS)
        }
    }
    /**
     * ??????????????? ????????? ????????? ??????
     */
    override fun onBackPressed() {
        // ?????? ????????? ????????? ??????
        if(popUpWebView != null) {
            if(popUpWebView!!.canGoBack()) {
                popUpWebView!!.goBack()
            } else {
                popUpWebView!!.loadUrl("javascript:window.close()")
            }
        } else {
            webView.loadUrl("javascript:onBackPressed()")
        }

        /*if((System.currentTimeMillis() - lastTimeBackPressed) < 1500){
            finish()
            return
        }
        else {
            webView.loadUrl("javascript:onBackPressed()")
        }
        lastTimeBackPressed = System.currentTimeMillis()*/

        // ???????????? ??????
        //super.onBackPressed()
    }
    /**
     * On activity result setting (permission and kakao login)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        var dataValue = data
        when (requestCode) {
            FILECHOOSER_NORMAL_REQ_CODE -> if (resultCode == RESULT_OK) {
                if (filePathCallbackNormal == null) return
                val result =
                    if (dataValue == null || resultCode != RESULT_OK) null else dataValue.data
                //  onReceiveValue ??? ????????? ????????????.
                filePathCallbackNormal!!.onReceiveValue(result)
                filePathCallbackNormal = null
            }
            FILECHOOSER_LOLLIPOP_REQ_CODE -> if (resultCode == RESULT_OK) {
                if (filePathCallbackLollipop == null) return
                if (dataValue == null) dataValue = Intent()
                if (dataValue.data == null) dataValue.data = cameraImageUri
                filePathCallbackLollipop!!.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, dataValue)
                )
                filePathCallbackLollipop = null
            } else {
                if (filePathCallbackLollipop != null) {   //  resultCode??? RESULT_OK??? ???????????? ????????? null ???????????? ??????.(????????? ?????? ????????? ???????????? input ????????? ???????????? ???????????? ??????)
                    filePathCallbackLollipop!!.onReceiveValue(null)
                    filePathCallbackLollipop = null
                }
                if (filePathCallbackNormal != null) {
                    filePathCallbackNormal!!.onReceiveValue(null)
                    filePathCallbackNormal = null
                }
            }
            //???????????? GPS ?????? ???????????? ??????
            REQUEST_ACCESS_GPS_LOCATION -> if (checkLocationServicesStatus()) {
                if (checkLocationServicesStatus()) {
                    Log.d("@@@", "onActivityResult : GPS ????????? ?????????")
                    checkRunTimePermission(webView)
                    return
                }
            }
            else -> {

            }
        }

        /*if (Session.getCurrentSession().handleActivityResult(requestCode, resultCode, data)) {
            return
        }*/
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * ????????? ?????? ?????? callback ??????
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        when(requestCode) {
            REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_ENABLED, REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_DISABLED -> {
//                val isCapture = requestCode == REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_ENABLED
//                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    runCamera(isCapture)
//                }
//                else {
//                    filePathCallbackLollipop?.onReceiveValue(null)
//                    filePathCallbackLollipop = null
//                }
            }
            else -> {}
        }

        if (requestCode == REQUEST_ACCESS_FINE_LOCATION && grantResults.size == REQUIRED_PERMISSIONS.size) {

            // ?????? ????????? PERMISSIONS_REQUEST_CODE ??????, ????????? ????????? ???????????? ??????????????????
            var checkResult = true

            // ?????? ???????????? ??????????????? ???????????????.
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }


            if (checkResult) {
                //?????? ?????? ????????? ??? ??????
                val obj = JSONObject()
                gpsTracker = GpsTracker(this@MainActivity)
                val latitude = gpsTracker.getLatitude()
                val longitude = gpsTracker.getLongitude()
                obj.put("lat", latitude)
                obj.put("lng", longitude)
                webView.post {
                    run {
                        webView.loadUrl("javascript:currentLocation($obj)")
                    }
                }

            } else {
                // ????????? ???????????? ????????? ?????? ????????? ??? ?????? ????????? ??????????????? ?????? ???????????????.2 ?????? ????????? ????????????.
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0]) || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])) {
                    // ????????? ????????? ????????? ??????
                    Toast.makeText(
                        this@MainActivity,
                        "???????????? ?????????????????????. ?????? ?????? ???????????? ???????????? ??????????????????.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    // ????????? ?????? ????????? ????????? ??????
                    Toast.makeText(
                        this@MainActivity,
                        "???????????? ?????????????????????. ??????(??? ??????)?????? ???????????? ???????????? ?????????. ",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * ??? ??? ????????????
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
        val web = wv.settings
        web.setAppCacheEnabled(true)
        web.setAppCachePath(cacheDir.path)
        web.setSupportMultipleWindows(true)
        web.javaScriptEnabled = true
        web.allowContentAccess = true
        web.allowFileAccess = true
        web.domStorageEnabled = true
        web.useWideViewPort = true
        //web.textSize = WebSettings.TextSize.NORMAL
        web.textZoom = 100
        web.javaScriptCanOpenWindowsAutomatically = true
        web.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.101 Mobile Safari/537.36"
        web.cacheMode = WebSettings.LOAD_DEFAULT
    }
    /**
     * ?????? ?????? ?????? ??????
     */
    fun checkLocationServicesStatus(): Boolean {
        val locationManager: LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER))
    }

    /**
     * ??????????????? ????????? ???????????? ?????? ?????? ???????????? ??????????????? alert ??????
     */
    fun showDialogForLocationServiceSetting() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("?????? ????????? ????????????")
        builder.setMessage(
            """
                ?????? ???????????? ???????????? ?????? ???????????? ???????????????.
                ?????? ????????? ???????????????????
                """.trimIndent()
        )
        builder.setCancelable(true)

        builder.setPositiveButton("??????") { _, _ ->
            val callGPSSettingIntent =
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivityForResult(callGPSSettingIntent, REQUEST_ACCESS_GPS_LOCATION)
        }

        builder.setNegativeButton("??????") { dialog, _ -> dialog.cancel() }
        builder.create().show()
    }

    /**
     * location permission
     */
    fun checkRunTimePermission(web: WebView) {

        //????????? ????????? ??????
        // 1. ?????? ???????????? ????????? ????????? ???????????????.
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)

        Log.i("TEST1", hasFineLocationPermission.toString())
        Log.i("TEST2", hasCoarseLocationPermission.toString())
        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED && hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            // 2. ?????? ???????????? ????????? ?????????
            // ( ??????????????? 6.0 ?????? ????????? ????????? ???????????? ???????????? ????????? ?????? ????????? ?????? ???????????????.)

            // 3.  ?????? ?????? ????????? ??? ??????
            val obj = JSONObject()
            gpsTracker = GpsTracker(this@MainActivity)
            val latitude = gpsTracker.getLatitude()
            val longitude = gpsTracker.getLongitude()
            obj.put("lat", latitude)
            obj.put("lng", longitude)
            web.post {
                run {
                    web.loadUrl("javascript:currentLocation($obj)")
                }
            }


        } else {
            //2. ????????? ????????? ????????? ?????? ????????? ????????? ????????? ???????????????. 2?????? ??????(3-1, 4-1)??? ????????????.
            // 3-1. ???????????? ????????? ????????? ??? ?????? ?????? ????????????
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, REQUIRED_PERMISSIONS[0])) {

                // 3-2. ????????? ???????????? ?????? ?????????????????? ???????????? ????????? ????????? ???????????? ????????? ????????????.
                Toast.makeText(this@MainActivity, "??? ?????? ??????????????? ?????? ?????? ????????? ???????????????.", Toast.LENGTH_LONG).show()

                // 3-3. ??????????????? ????????? ????????? ?????????. ?????? ????????? onRequestPermissionsResult?????? ???????????????.
                ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, REQUEST_ACCESS_FINE_LOCATION)
            } else {
                // 4-1. ???????????? ????????? ????????? ??? ?????? ?????? ???????????? ????????? ????????? ?????? ?????????.
                // ?????? ????????? onRequestPermissionsResult ?????? ???????????????.
                ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, REQUEST_ACCESS_FINE_LOCATION)
            }
        }
    }

    // ????????? ?????? ??????
    private fun runCamera(_isCapture: Boolean) {

        Log.i("camera", _isCapture.toString())

        val intentCamera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        val path: File = filesDir
        val file = File(path, "cocoboli.png") // sample.png ??? ???????????? ????????? ??? ????????? ?????????????????? ????????? ????????????

        // File ????????? URI ??? ?????????.
        cameraImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val strpa = applicationContext.packageName
            FileProvider.getUriForFile(this, "$strpa.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)

        if (!_isCapture) { // ???????????? ?????????, ????????? ?????? ????????? ?????? ???
            val pickIntent = Intent(Intent.ACTION_PICK)
            pickIntent.type = MediaStore.Images.Media.CONTENT_TYPE
            pickIntent.data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val pickTitle = "?????? ????????? ????????? ???????????????."
            val chooserIntent = Intent.createChooser(pickIntent, pickTitle)

            // ????????? intent ???????????????..
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(intentCamera))
            startActivityForResult(chooserIntent, FILECHOOSER_LOLLIPOP_REQ_CODE)
        } else { // ?????? ????????? ??????..
            startActivityForResult(intentCamera, FILECHOOSER_LOLLIPOP_REQ_CODE)
        }
    }
    private inner class ParentWebChromeClient(val parent: ViewGroup, val context: Context) : WebChromeClient() {
        // For Android 5.0+ ????????? - input type="file" ????????? ???????????? ??? ??????
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri?>?>, fileChooserParams: FileChooserParams): Boolean {
            Log.d("MainActivity", "5.0+")

            // Callback ????????? (??????!)
            if (filePathCallbackLollipop != null) {
                filePathCallbackLollipop!!.onReceiveValue(null)
                filePathCallbackLollipop = null
            }
            //filePathCallbackLollipop = filePathCallback
            return if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                /*if(fileChooserParams.isCaptureEnabled)
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_ENABLED)
                else
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION_WITH_CAPTURE_DISABLED)*/
                AlertDialog.Builder(this@MainActivity).apply {
                    setMessage("????????? ?????? ?????? ??? ???????????? ??? ????????????")
                    setNegativeButton("??????") { dialog, _ ->
                        dialog.dismiss()
                    }
                    setPositiveButton("???????????? ??????") { dialog, _ ->
                        dialog.dismiss()
                        //val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        startActivity(intent)
                    }
                }.create().show()
                filePathCallback.onReceiveValue(null)
                true
            }
            else {
                filePathCallbackLollipop = filePathCallback
                runCamera(fileChooserParams.isCaptureEnabled)
                true
            }
        }

        /**
         * ????????? ?????? ??? ??? ?????? ??????
         * */
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val newWebView = WebView(this@MainActivity)
            newWebView.settings.apply {
                javaScriptEnabled = true
            }
            newWebView.webChromeClient = object: WebChromeClient() {
                override fun onCloseWindow(window: WebView?) {
                    super.onCloseWindow(window)
                    webView.removeView(newWebView)
                    popUpWebView = null
                    newWebView.destroy()
                }
            }
            newWebView.webViewClient = object: WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    if(url.startsWith("http") || url.startsWith("https")) {
                        return false
                    } else {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            if(packageManager.resolveActivity(intent, 0) == null) {
                                val marketUri =
                                    if(intent.`package` != null) {
                                        Uri.parse("https://play.google.com/store/apps/details?id=${intent.`package`}")
                                    } else {
                                        if(intent.scheme == "kakaotalk") {
                                            Uri.parse("https://play.google.com/store/apps/details?id=com.kakao.talk")
                                        } else {
                                            Uri.parse("https://play.google.com/store/apps/details?id=${intent.`package`}")
                                        }
                                    }
                                val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
                                startActivity(marketIntent)
                            } else {
                                startActivity(intent)
                            }
                            return true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            return true
                        }
                    }
                    //return super.shouldOverrideUrlLoading(view, request)
                }
            }
            newWebView.layoutParams = webView.layoutParams
            popUpWebView = newWebView
            webView.addView(newWebView)

            return if(resultMsg != null) {
                val webViewTransport = resultMsg.obj as WebView.WebViewTransport
                webViewTransport.webView = newWebView
                resultMsg.sendToTarget()
                true
            } else {
                false
            }
            //return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
        }
    }
}