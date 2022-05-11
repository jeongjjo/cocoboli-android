package com.punkytails.cocoboli

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat.startActivity

class AndroidBridge(private val wv: WebView, private val activity: MainActivity): AndroidBridgeInterface {

    private val bridgeTag = "AndroidBridge"

    @JavascriptInterface
    override fun finishApp() {
        activity.finish()
    }

    @JavascriptInterface
    override fun location() {
        Log.i(bridgeTag, "location permission")

        if (activity.checkLocationServicesStatus()) {
            Log.i(bridgeTag, "위치 서비스 상태 확인 true")
            activity.checkRunTimePermission(wv)
        }
        else {
            Log.i(bridgeTag, "위치 서비스 상태 확인 false")
            activity.showDialogForLocationServiceSetting()
        }
    }
    @JavascriptInterface
    override fun doShare(arg1: String?, arg2: String?, arg3: String?) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, "$arg1")
            putExtra(Intent.EXTRA_TEXT, "$arg1 - $arg2\n$arg3")
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        activity.startActivity(shareIntent)
    }
}
