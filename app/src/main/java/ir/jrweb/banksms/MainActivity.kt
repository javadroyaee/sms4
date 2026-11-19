package ir.jrweb.banksms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSms = findViewById<Button>(R.id.btnSmsPermission)
        val btnOverlay = findViewById<Button>(R.id.btnOverlayPermission)
        val btnTestLastSms = findViewById<Button>(R.id.btnTestLastSms)
        val btnTestDeposit = findViewById<Button>(R.id.btnTestDeposit)
        val btnTestWithdraw = findViewById<Button>(R.id.btnTestWithdraw)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        startKeepAliveService()

        btnSms.setOnClickListener {
            val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSION_CODE)
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "مجوز نمایش روی صفحه فعال است", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestLastSms.setOnClickListener {
            testLastInboxSms()
        }

        btnTestDeposit.setOnClickListener {
            showTestPopup("DEPOSIT", "2,500,000")
        }

        btnTestWithdraw.setOnClickListener {
            showTestPopup("WITHDRAW", "450,000")
        }

        updateStatus(tvStatus)
    }

    private fun testLastInboxSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "ابتدا دسترسی پیامک را فعال کنید", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body"),
                null,
                null,
                "_id DESC LIMIT 15"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(0) ?: ""
                    val body = cursor.getString(1) ?: ""
                    val parsed = BankSmsParser.parse(sender, body)
                    if (parsed != null) {
                        showTestPopup(parsed.type, parsed.formattedAmount)
                        return
                    }
                }
            }
            Toast.makeText(this, "پیامک بانکی در ۱۵ پیام اخیر پیدا نشد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        startKeepAliveService()
        updateStatus(findViewById(R.id.tvStatus))
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, PopupService::class.java).apply {
            putExtra("ACTION_KEEP_ALIVE", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateStatus(tvStatus: TextView) {
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        tvStatus.text = "سرویس پس‌زمینه: فعال 🟢\n" +
                "مجوز پیامک: ${if (smsGranted) "فعال ✅" else "غیرفعال ❌"}\n" +
                "مجوز پاپ‌آپ: ${if (overlayGranted) "فعال ✅" else "غیرفعال ❌"}"
    }

    private fun showTestPopup(type: String, amount: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ابتدا مجوز نمایش روی صفحه را فعال کنید", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, PopupService::class.java).apply {
            putExtra("TYPE", type)
            putExtra("AMOUNT", amount)
            putExtra("DEDUP_KEY", "TEST_${System.currentTimeMillis()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}