package app.simforward

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** 0 = 任何 SIM；1 = 僅 SIM1；2 = 僅 SIM2 */
    var sourceSlot: Int
        get() = sp.getInt("source", 1)
        set(v) = sp.edit().putInt("source", v).apply()

    /** 0 = 用接收該短信的同一張 SIM 發出；1/2 = 指定 SIM */
    var sendSlot: Int
        get() = sp.getInt("send", 0)
        set(v) = sp.edit().putInt("send", v).apply()

    /** 短信轉發目標號碼，留空 = 不轉發短信 */
    var dest: String
        get() = sp.getString("dest", "") ?: ""
        set(v) = sp.edit().putString("dest", v.trim()).apply()

    var keywords: String
        get() = sp.getString("kw", "") ?: ""
        set(v) = sp.edit().putString("kw", v).apply()

    var lastLog: String
        get() = sp.getString("log", "") ?: ""
        set(v) = sp.edit().putString("log", v).apply()

    // ---- 郵件 ----
    var mailEnabled: Boolean
        get() = sp.getBoolean("mail_on", false)
        set(v) = sp.edit().putBoolean("mail_on", v).apply()

    var smtpHost: String
        get() = sp.getString("smtp_host", "") ?: ""
        set(v) = sp.edit().putString("smtp_host", v.trim()).apply()

    /** 465 = 隱式 SSL；587 = STARTTLS */
    var smtpPort: Int
        get() = sp.getInt("smtp_port", 465)
        set(v) = sp.edit().putInt("smtp_port", v).apply()

    var smtpUser: String
        get() = sp.getString("smtp_user", "") ?: ""
        set(v) = sp.edit().putString("smtp_user", v.trim()).apply()

    var smtpPass: String
        get() = sp.getString("smtp_pass", "") ?: ""
        set(v) = sp.edit().putString("smtp_pass", v).apply()

    var mailTo: String
        get() = sp.getString("mail_to", "") ?: ""
        set(v) = sp.edit().putString("mail_to", v.trim()).apply()

    fun mailReady() = mailEnabled && smtpHost.isNotBlank() && smtpUser.isNotBlank() &&
            smtpPass.isNotBlank() && mailTo.isNotBlank()
}
