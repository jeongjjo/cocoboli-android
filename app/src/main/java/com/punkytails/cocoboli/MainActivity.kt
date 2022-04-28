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

        //timer
        startTime = System.currentTimeMillis()

        // webview settings
        configureWebView(webView)

        // webview detail setting
        WebView.setWebContentsDebuggingEnabled(true)
        val androidBridge = AndroidBridge(webView, this@MainActivity)
        webView.webChromeClient = ParentWebChromeClient(parentLayout, this)
        webView.addJavascriptInterface(androidBridge, "android")
        // load url
        //webView.post {
            //webView.loadUrl("http://cocoboli.com/main")
        webView.loadUrl("http://cocoboli.com/main")
        //}
    }
    private fun checkPermissions() {
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSIONS)
        }
    }
    /**
     * 안드로이드 백버튼 핸들러 구현
     */
    override fun onBackPressed() {
        // 부모 웹뷰의 백버튼 구현
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

        // 액티비티 종료
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
                //  onReceiveValue 로 파일을 전송한다.
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
                if (filePathCallbackLollipop != null) {   //  resultCode에 RESULT_OK가 들어오지 않으면 null 처리하지 한다.(이렇게 하지 않으면 다음부터 input 태그를 클릭해도 반응하지 않음)
                    filePathCallbackLollipop!!.onReceiveValue(null)
                    filePathCallbackLollipop = null
                }
                if (filePathCallbackNormal != null) {
                    filePathCallbackNormal!!.onReceiveValue(null)
                    filePathCallbackNormal = null
                }
            }
            //사용자가 GPS 활성 시켰는지 검사
            REQUEST_ACCESS_GPS_LOCATION -> if (checkLocationServicesStatus()) {
                if (checkLocationServicesStatus()) {
                    Log.d("@@@", "onActivityResult : GPS 활성화 되있음")
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
     * 사용자 권한 요청 callback 함수
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

            // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면
            var checkResult = true

            // 모든 퍼미션을 허용했는지 체크합니다.
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }


            if (checkResult) {
                //위치 값을 가져올 수 있음
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
                // 거부한 퍼미션이 있다면 앱을 사용할 수 없는 이유를 설명해주고 앱을 종료합니다.2 가지 경우가 있습니다.
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0]) || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])) {
                    // 초기에 권한을 거부한 경우
                    Toast.makeText(
                        this@MainActivity,
                        "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    // 권한을 승인 했다가 거부한 경우
                    Toast.makeText(
                        this@MainActivity,
                        "퍼미션이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해야 합니다. ",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 웹 뷰 기본설정
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
     * 위치 정보 설정 확인
     */
    fun checkLocationServicesStatus(): Boolean {
        val locationManager: LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER))
    }

    /**
     * 위치정보가 미수락 되어있을 경우 설정 화면으로 이동하도록 alert 표시
     */
    fun showDialogForLocationServiceSetting() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage(
            """
                앱을 사용하기 위해서는 위치 서비스가 필요합니다.
                위치 설정을 수정하실래요?
                """.trimIndent()
        )
        builder.setCancelable(true)

        builder.setPositiveButton("설정") { _, _ ->
            val callGPSSettingIntent =
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivityForResult(callGPSSettingIntent, REQUEST_ACCESS_GPS_LOCATION)
        }

        builder.setNegativeButton("취소") { dialog, _ -> dialog.cancel() }
        builder.create().show()
    }

    /**
     * location permission
     */
    fun checkRunTimePermission(web: WebView) {

        //런타임 퍼미션 처리
        // 1. 위치 퍼미션을 가지고 있는지 체크합니다.
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)

        Log.i("TEST1", hasFineLocationPermission.toString())
        Log.i("TEST2", hasCoarseLocationPermission.toString())
        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED && hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            // 2. 이미 퍼미션을 가지고 있다면
            // ( 안드로이드 6.0 이하 버전은 런타임 퍼미션이 필요없기 때문에 이미 허용된 걸로 인식합니다.)

            // 3.  위치 값을 가져올 수 있음
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
            //2. 퍼미션 요청을 허용한 적이 없다면 퍼미션 요청이 필요합니다. 2가지 경우(3-1, 4-1)가 있습니다.
            // 3-1. 사용자가 퍼미션 거부를 한 적이 있는 경우에는
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, REQUIRED_PERMISSIONS[0])) {

                // 3-2. 요청을 진행하기 전에 사용자가에게 퍼미션이 필요한 이유를 설명해줄 필요가 있습니다.
                Toast.makeText(this@MainActivity, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()

                // 3-3. 사용자게에 퍼미션 요청을 합니다. 요청 결과는 onRequestPermissionsResult에서 수신됩니다.
                ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, REQUEST_ACCESS_FINE_LOCATION)
            } else {
                // 4-1. 사용자가 퍼미션 거부를 한 적이 없는 경우에는 퍼미션 요청을 바로 합니다.
                // 요청 결과는 onRequestPermissionsResult 에서 수신됩니다.
                ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, REQUEST_ACCESS_FINE_LOCATION)
            }
        }
    }

    // 카메라 기능 구현
    private fun runCamera(_isCapture: Boolean) {

        Log.i("camera", _isCapture.toString())

        val intentCamera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        val path: File = filesDir
        val file = File(path, "cocoboli.png") // sample.png 는 카메라로 찍었을 때 저장될 파일명이므로 사용자 마음대로

        // File 객체의 URI 를 얻는다.
        cameraImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val strpa = applicationContext.packageName
            FileProvider.getUriForFile(this, "$strpa.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)

        if (!_isCapture) { // 선택팝업 카메라, 갤러리 둘다 띄우고 싶을 때
            val pickIntent = Intent(Intent.ACTION_PICK)
            pickIntent.type = MediaStore.Images.Media.CONTENT_TYPE
            pickIntent.data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val pickTitle = "사진 가져올 방법을 선택하세요."
            val chooserIntent = Intent.createChooser(pickIntent, pickTitle)

            // 카메라 intent 포함시키기..
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(intentCamera))
            startActivityForResult(chooserIntent, FILECHOOSER_LOLLIPOP_REQ_CODE)
        } else { // 바로 카메라 실행..
            startActivityForResult(intentCamera, FILECHOOSER_LOLLIPOP_REQ_CODE)
        }
    }
    private inner class ParentWebChromeClient(val parent: ViewGroup, val context: Context) : WebChromeClient() {
        // For Android 5.0+ 카메라 - input type="file" 태그를 선택했을 때 반응
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri?>?>, fileChooserParams: FileChooserParams): Boolean {
            Log.d("MainActivity", "5.0+")

            // Callback 초기화 (중요!)
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
                    setMessage("카메라 권한 설정 후 이용하실 수 있습니다")
                    setNegativeButton("취소") { dialog, _ ->
                        dialog.dismiss()
                    }
                    setPositiveButton("설정으로 이동") { dialog, _ ->
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
         * 결제를 위한 새 창 열기 처리
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
            newWebView.webViewClient = WebViewClient()
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