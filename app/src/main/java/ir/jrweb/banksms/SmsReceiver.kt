package ir.jrweb.banksms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: ""
        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody)
        }

        val bodyStr = fullBody.toString()
        val parsed = BankSmsParser.parse(sender, bodyStr) ?: return

        val serviceIntent = Intent(context, PopupService::class.java).apply {
            putExtra("TYPE", parsed.type)
            putExtra("AMOUNT", parsed.formattedAmount)
            putExtra("DEDUP_KEY", "${sender}_${bodyStr.hashCode()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}