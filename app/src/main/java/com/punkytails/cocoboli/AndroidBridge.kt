package com.punkytails.cocoboli

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView

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
}
