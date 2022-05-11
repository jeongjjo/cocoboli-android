package com.punkytails.cocoboli

import android.net.Uri
import android.webkit.JavascriptInterface

interface AndroidBridgeInterface {
    fun finishApp()
    @JavascriptInterface
    fun location()
    @JavascriptInterface
    fun doShare(arg1: String?, arg2: String?, arg3: String?)
}
