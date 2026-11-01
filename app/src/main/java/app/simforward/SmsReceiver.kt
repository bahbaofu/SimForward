package app.simforward

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = Prefs(context)
        if (!prefs.enabled) return
        val smsOn = prefs.dest.isNotBlank()
        val mailOn = prefs.mailReady()
        if (!smsOn && !mailOn) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val sender = msgs[0].originatingAddress ?: "?"
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        val subId = intent.getIntExtra(
            "subscription",
            intent.getIntExtra(
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )
        )
        val slot = slotOf(context, subId)
        val slotLabel = if (slot >= 0) "SIM${slot + 1}" else "SIM?"

        if (prefs.sourceSlot != 0 && slot + 1 != prefs.sourceSlot) {
            log(prefs, "略過：來自 $slotLabel（只轉 SIM${prefs.sourceSlot}）")
            return
        }

        val kws = prefs.keywords.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        if (kws.isNotEmpty() && kws.none { body.contains(it, ignoreCase = true) }) {
            log(prefs, "略過：不含關鍵字")
            return
        }

        val label = "$slotLabel 來自 $sender"

        // ---- 短信轉發（來自目標號碼的不轉，防循環）----
        if (smsOn) {
            if (sameNumber(sender, prefs.dest)) {
                log(prefs, "短信略過：來自目標號碼 $sender")
            } else {
                val sendSubId = if (prefs.sendSlot == 0) subId else subIdOfSlot(context, prefs.sendSlot - 1)
                Dispatcher.enqueueSms(context, sendSubId, prefs.dest, "[$slotLabel] $sender\n$body", label)
            }
        }

        // ---- 郵件轉發（網絡操作，必須在背景執行緒）----
        if (mailOn) {
            val item = Dispatcher.enqueueMail(
                context, prefs.mailTo,
                "[$slotLabel] 短信 來自 $sender",
                "SIM: $slotLabel\n發送者: $sender\n時間: ${Date()}\n\n$body",
                label
            )
            val pending = goAsync()
            Thread {
                try {
                    Dispatcher.deliverMail(context, item)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }

    companion object {
        /** 測試用的直接發送（不經發件匣、不重試） */
        fun send(context: Context, subId: Int, dest: String, text: String) {
            var sm = context.getSystemService(SmsManager::class.java)
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                sm = sm.createForSubscriptionId(subId)
            }
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(dest, null, parts, null, null)
        }

        fun slotOf(context: Context, subId: Int): Int {
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return -1
            return try {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                sm.getActiveSubscriptionInfo(subId)?.simSlotIndex ?: -1
            } catch (e: SecurityException) {
                -1
            }
        }

        fun subIdOfSlot(context: Context, slot: Int): Int {
            return try {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                sm.activeSubscriptionInfoList
                    ?.firstOrNull { it.simSlotIndex == slot }?.subscriptionId
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            } catch (e: SecurityException) {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
        }

        private fun sameNumber(a: String, b: String): Boolean {
            val da = a.filter { it.isDigit() }.takeLast(9)
            val db = b.filter { it.isDigit() }.takeLast(9)
            return da.isNotEmpty() && da == db
        }

        @Synchronized
        fun log(prefs: Prefs, msg: String) {
            val t = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            val old = prefs.lastLog.lines().take(14).joinToString("\n")
            prefs.lastLog = "$t  $msg" + if (old.isNotBlank()) "\n$old" else ""
        }
    }
}
