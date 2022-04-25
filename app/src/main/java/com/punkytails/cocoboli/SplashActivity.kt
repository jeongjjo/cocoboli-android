package com.punkytails.cocoboli

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TIME_OUT: Long = 20000
        private const val REQUEST_PERMISSIONS = 1000
    }

    private val handler by lazy{ Handler(mainLooper) }

    private var isCalledFinishSplash = false
    private var isActivityRunning = true

    private val broadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i("SplashBroadCast", intent.toString())
            if(intent?.action == MainActivity.ACTION_BRIDGE_EVENT
                && intent.getStringExtra(MainActivity.EXTRA_BRIDGE_EVENT) == MainActivity.EXTRA_FINISH_SPLASH) {
                Log.d("splash", "called finish splash")
                isCalledFinishSplash = true
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(MainActivity.ACTION_BRIDGE_EVENT))
        checkPermissions()
        handler.postDelayed({finish()}, 1000)
    }

    override fun onPause() {
        super.onPause()
        isActivityRunning = false
    }

    override fun onResume() {
        super.onResume()
        isActivityRunning = true
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    // do nothing...
    override fun onBackPressed() {}

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun checkPermissions() {
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSIONS)
        }
    }
}
