package com.punkytails.cocoboli

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

class CustomWebView: WebView {

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    override fun onWindowVisibilityChanged(visibility: Int) {
        if(visibility ==  View.GONE)
            super.onWindowVisibilityChanged(View.VISIBLE)
        else
            super.onWindowVisibilityChanged(visibility)
    }

}