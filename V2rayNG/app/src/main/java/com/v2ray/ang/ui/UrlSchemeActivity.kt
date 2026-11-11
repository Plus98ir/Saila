package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import java.net.URLDecoder

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            var link: String? = null
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        link = intent.getStringExtra(Intent.EXTRA_TEXT)?.let { decodeLink(it, null) }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config", "install-sub" -> {
                            val uri: Uri? = intent.data
                            link = decodeLink(uri?.getQueryParameter("url").orEmpty(), uri?.fragment)
                        }

                        else -> {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }

            // Saila: MainActivity imports the link itself, so the list it shows reloads when the import ends
            // (importing here and finishing at once left an already open MainActivity on its old list).
            val main = Intent(this, MainActivity::class.java)
            link?.let { main.putExtra(MainActivity.EXTRA_IMPORT_LINK, it) }
            startActivity(main)
            finish()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
        }
    }

    @Composable
    override fun ScreenContent() {
    }

    private fun decodeLink(uriString: String?, fragment: String?): String? {
        if (uriString.isNullOrEmpty()) {
            return null
        }
        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null && uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        return decodedUrl
    }
}
