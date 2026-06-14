package com.nextgen.nxplayer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object DonationHelper {
    private const val DONATION_URL = "https://www.buymeacoffee.com/nxplayer"

    fun openDonationPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL))
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}