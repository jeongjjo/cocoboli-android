package com.punkytails.cocoboli

import android.webkit.JavascriptInterface

interface AndroidBridgeInterface {
    fun finishApp()
    @JavascriptInterface
    fun location()
}
