package com.kairos.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a place (address or name) in whatever maps/navigation app the phone
 *  has, via the standard `geo:` scheme. No-op if no maps app is installed. */
object Maps {
    /** A `geo:` view intent for [query]; the maps app resolves and can navigate. */
    fun intent(query: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context, query: String) {
        runCatching { context.startActivity(intent(query)) }
    }
}
