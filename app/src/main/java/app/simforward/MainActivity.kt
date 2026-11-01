package app.simforward

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var simInfo: TextView
    private lateinit var logView: TextView
    private lateinit var swEnabled: Switch
    private lateinit var etDest: EditText
    private lateinit var etKeywords: EditText
    private lateinit var spSource: Spinner
    private lateinit var spSend: Spinner
    private lateinit var swMail: Switch
    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etMailTo: EditText

    private val perms = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad, 0, 4) }
        fun edit(h: String, type: Int = InputType.TYPE_CLASS_TEXT) =
            EditText(this).apply { hint = h; inputType = type }
        fun spinner(items: List<String>) = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items
            )
        }

        simInfo = TextView(this)
        swEnabled = Switch(this).apply { text = "總開關：啟用轉發" }
        etDest = edit("+886912345678（留空 = 不轉發短信）", InputType.TYPE_CLASS_PHONE)
        spSource = spinner(listOf("任何 SIM", "僅 SIM 1", "僅 SIM 2"))
        spSend = spinner(listOf("同接收的那張 SIM", "SIM 1", "SIM 2"))
        etKeywords = edit("例如：驗證碼,code（留空 = 全部轉發）")

        swMail = Switch(this).apply { text = "同時轉發到郵箱" }
        etHost = edit("SMTP 伺服器，例如 smtp.gmail.com", InputType.TYPE_TEXT_VARIATION_URI)
        etPort = edit("埠：465（SSL）或 587（STARTTLS）", InputType.TYPE_CLASS_NUMBER)
        etUser = edit("SMTP 帳號（通常是你的郵箱）", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        etPass = edit(
            "Gmail 應用程式密碼（16 碼，空格會自動移除）",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        etMailTo = edit("收件郵箱", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)

        val btnGmail = Button(this).apply {
            text = "套用 Gmail 預設（smtp.gmail.com:465）"
            setOnClickListener {
                etHost.setText("smtp.gmail.com")
                etPort.setText("465")
                toast("帳號填 Gmail 地址，密碼填「應用程式密碼」")
            }
        }
        val btnPerm = Button(this).apply {
            text = "授予權限"
            setOnClickListener { requestPermissions(perms, 1) }
        }
        val btnSave = Button(this).apply { text = "儲存設定"; setOnClickListener { save(); toast("已儲存") } }
        val btnTestSms = Button(this).apply { text = "測試短信轉發"; setOnClickListener { testSms() } }
        val btnTestMail = Button(this).apply { text = "測試郵件轉發"; setOnClickListener { testMail() } }
        logView = TextView(this).apply { textSize = 12f }

        listOf(
            simInfo, btnPerm, swEnabled,
            label("【短信接口】轉發到的號碼（含國碼）"), etDest,
            label("從哪張 SIM 收到的才轉發"), spSource,
            label("用哪張 SIM 發出"), spSend,
            label("關鍵字過濾（短信與郵件共用）"), etKeywords,
            label("【郵件接口】"), swMail, btnGmail,
            etHost, etPort, etUser, etPass, etMailTo,
            btnSave, btnTestSms, btnTestMail,
            label("最近記錄"), logView
        ).forEach {
            root.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        swEnabled.isChecked = prefs.enabled
        etDest.setText(prefs.dest)
        spSource.setSelection(prefs.sourceSlot)
        spSend.setSelection(prefs.sendSlot)
        etKeywords.setText(prefs.keywords)
        swMail.isChecked = prefs.mailEnabled
        etHost.setText(prefs.smtpHost)
        etPort.setText(prefs.smtpPort.toString())
        etUser.setText(prefs.smtpUser)
        etPass.setText(prefs.smtpPass)
        etMailTo.setText(prefs.mailTo)
    }

    override fun onResume() { super.onResume(); refreshInfo() }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        refreshInfo()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun hasAll() = perms.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshInfo() {
        val sb = StringBuilder()
        sb.append(if (hasAll()) "權限：已齊全\n" else "權限：尚未齊全，請點「授予權限」\n")
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val list = getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                if (list.isNullOrEmpty()) sb.append("偵測不到 SIM\n")
                list?.sortedBy { it.simSlotIndex }?.forEach {
                    sb.append("SIM ${it.simSlotIndex + 1}：${it.carrierName}  ${it.number ?: ""}\n")
                }
            } catch (e: SecurityException) {
                sb.append("無法讀取 SIM 資訊\n")
            }
        }
        simInfo.text = sb.toString()
        logView.text = prefs.lastLog.ifBlank { "（尚無）" }
    }

    private fun save() {
        prefs.enabled = swEnabled.isChecked
        prefs.dest = etDest.text.toString()
        prefs.sourceSlot = spSource.selectedItemPosition
        prefs.sendSlot = spSend.selectedItemPosition
        prefs.keywords = etKeywords.text.toString()
        prefs.mailEnabled = swMail.isChecked
        prefs.smtpHost = etHost.text.toString()
        prefs.smtpPort = etPort.text.toString().toIntOrNull() ?: 465
        prefs.smtpUser = etUser.text.toString()
        prefs.smtpPass = etPass.text.toString().let {
            if (etHost.text.toString().contains("gmail.com", true)) it.replace(" ", "") else it
        }
        prefs.mailTo = etMailTo.text.toString()
    }

    private fun testSms() {
        save()
        if (!hasAll()) { toast("請先授予權限"); return }
        if (prefs.dest.isBlank()) { toast("請先填寫號碼"); return }
        try {
            val subId = if (prefs.sendSlot == 0)
                SubscriptionManager.getDefaultSmsSubscriptionId()
            else SmsReceiver.subIdOfSlot(this, prefs.sendSlot - 1)
            SmsReceiver.send(this, subId, prefs.dest, "SimForward 測試短信")
            SmsReceiver.log(prefs, "已發送測試短信到 ${prefs.dest}")
        } catch (e: Exception) {
            SmsReceiver.log(prefs, "測試失敗：${e.message}")
        }
        refreshInfo()
    }

    private fun testMail() {
        save()
        if (!prefs.mailReady()) { toast("請先開啟郵件並填齊所有欄位"); return }
        toast("發送中…")
        Thread {
            try {
                Mailer.send(
                    prefs.smtpHost, prefs.smtpPort, prefs.smtpUser, prefs.smtpPass,
                    prefs.mailTo, "SimForward 測試郵件", "這是一封測試郵件。"
                )
                SmsReceiver.log(prefs, "測試郵件已發送到 ${prefs.mailTo}")
            } catch (e: Exception) {
                SmsReceiver.log(prefs, "測試郵件失敗：${e.message}")
            }
            runOnUiThread { refreshInfo() }
        }.start()
    }
}
