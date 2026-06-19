package com.nextgen.nxplayer.utils

import android.content.Context
import android.widget.Toast

object DonationHelper {

    const val DONATION_ENABLED: Boolean = false

    fun showDonationDisabledMessage(context: Context) {
        Toast.makeText(
            context,
            "Donations are disabled until Google Play Billing is ready.",
            Toast.LENGTH_LONG
        ).show()
    }
}