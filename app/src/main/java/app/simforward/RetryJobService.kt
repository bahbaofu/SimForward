package app.simforward

import android.app.job.JobParameters
import android.app.job.JobService

class RetryJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                val now = System.currentTimeMillis()
                Outbox.all(this).filter { it.nextAt <= now }.forEach {
                    when {
                        it.attempts >= Dispatcher.MAX_ATTEMPTS -> {
                            Outbox.remove(this, it.id)
                            SmsReceiver.log(Prefs(this), "放棄（已嘗試 ${it.attempts} 次）：${it.label}")
                        }
                        it.type == "mail" -> Dispatcher.deliverMail(this, it)
                        else -> Dispatcher.dispatchSms(this, it)
                    }
                }
            } finally {
                Dispatcher.reschedule(this)
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
