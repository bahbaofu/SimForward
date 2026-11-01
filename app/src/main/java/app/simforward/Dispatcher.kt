package app.simforward

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.util.UUID

object Dispatcher {
    const val MAX_ATTEMPTS = 8
    private const val SMS_WAIT_MS = 5 * 60_000L   // 5 分鐘內沒收到發送結果就當作失敗重送
    private const val JOB_ID = 42

    private fun newId() = UUID.randomUUID().toString()
    private fun log(c: Context, msg: String) = SmsReceiver.log(Prefs(c), msg)

    // ---------- 入列 ----------
    fun enqueueSms(c: Context, subId: Int, dest: String, text: String, label: String) {
        val item = Item(newId(), "sms", subId, dest, "", text, 0, 0L, label)
        Outbox.add(c, item)
        dispatchSms(c, item)
    }

    fun enqueueMail(c: Context, to: String, subject: String, text: String, label: String): Item {
        val item = Item(newId(), "mail", -1, to, subject, text, 0, 0L, label)
        Outbox.add(c, item)
        return item
    }

    // ---------- 發送 ----------
    fun dispatchSms(c: Context, item: Item) {
        val cur = item.copy(
            attempts = item.attempts + 1,
            nextAt = System.currentTimeMillis() + SMS_WAIT_MS
        )
        Outbox.update(c, cur)
        reschedule(c)
        try {
            var sm = c.getSystemService(SmsManager::class.java)
            if (cur.subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                sm = sm.createForSubscriptionId(cur.subId)
            }
            val parts = sm.divideMessage(cur.text)
            val sent = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                val intent = Intent(c, SentReceiver::class.java)
                    .setData(Uri.parse("simforward://sent/${cur.id}/$i"))
                    .putExtra("id", cur.id)
                    .putExtra("part", i)
                    .putExtra("parts", parts.size)
                sent.add(
                    PendingIntent.getBroadcast(
                        c, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            sm.sendMultipartTextMessage(cur.dest, null, parts, sent, null)
        } catch (e: Exception) {
            fail(c, cur.id, "${e.javaClass.simpleName} ${e.message}")
        }
    }

    /** 阻塞式，必須在背景執行緒呼叫。 */
    fun deliverMail(c: Context, item: Item) {
        val cur = item.copy(attempts = item.attempts + 1)
        Outbox.update(c, cur)
        val p = Prefs(c)
        try {
            Mailer.send(p.smtpHost, p.smtpPort, p.smtpUser, p.smtpPass, cur.dest, cur.subject, cur.text)
            Outbox.remove(c, cur.id)
            log(c, "郵件已發送：${cur.label}")
        } catch (e: Exception) {
            fail(c, cur.id, e.message ?: e.javaClass.simpleName)
        }
    }

    // ---------- 失敗 → 排程重試 ----------
    fun fail(c: Context, id: String, reason: String) {
        val item = Outbox.remove(c, id) ?: return
        val kind = if (item.type == "mail") "郵件" else "短信"
        if (item.attempts >= MAX_ATTEMPTS) {
            log(c, "$kind 放棄（已嘗試 ${item.attempts} 次）：$reason")
            return
        }
        val shift = (item.attempts - 1).coerceIn(0, 6)
        val delay = minOf(60_000L shl shift, 3_600_000L)   // 1,2,4,8,...分鐘，最長 1 小時
        Outbox.add(c, item.copy(id = newId(), nextAt = System.currentTimeMillis() + delay))
        log(c, "$kind 失敗（第${item.attempts}次）${delay / 60_000}分鐘後重試：$reason")
        reschedule(c)
    }

    fun success(c: Context, id: String) {
        val item = Outbox.remove(c, id) ?: return
        log(c, "短信已發出：${item.label}")
        reschedule(c)
    }

    /** 依發件匣中最早到期的任務安排系統 Job（重開機後仍保留）。 */
    fun reschedule(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        val items = Outbox.all(c)
        if (items.isEmpty()) { js.cancel(JOB_ID); return }
        val delay = maxOf(0L, items.minOf { it.nextAt } - System.currentTimeMillis())
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(c, RetryJobService::class.java))
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + 60_000L)
                .setPersisted(true)
                .build()
        )
    }
}
