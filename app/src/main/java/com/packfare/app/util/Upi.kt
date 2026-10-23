package com.packfare.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.packfare.app.domain.Money

object Upi {

    /** Loose check for "name@bank" style UPI IDs. */
    fun isValidId(id: String): Boolean =
        Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9.\\-]{1,64}$").matches(id.trim())

    fun payUri(payeeUpiId: String, payeeName: String, amountPaise: Long, note: String): Uri =
        Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", payeeUpiId)
            .appendQueryParameter("pn", payeeName)
            .appendQueryParameter("am", Money.toUpiAmount(amountPaise))
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", note)
            .build()

    /**
     * Opens the user's UPI app (GPay, PhonePe, Paytm, BHIM...) with the payment pre-filled.
     * The app never touches money itself: the user confirms and enters their PIN in the UPI app.
     */
    fun launchPayment(context: Context, payeeUpiId: String, payeeName: String, amountPaise: Long, note: String) {
        val intent = Intent(Intent.ACTION_VIEW, payUri(payeeUpiId, payeeName, amountPaise, note))
        try {
            context.startActivity(Intent.createChooser(intent, "Pay with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI app found on this phone", Toast.LENGTH_LONG).show()
        }
    }
}
