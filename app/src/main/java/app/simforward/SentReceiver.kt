package app.simforward

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 系統回報「短信是否已交給網絡」。成功就刪除任務，失敗就排重試。 */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val part = intent.getIntExtra("part", 0)
        val parts = intent.getIntExtra("parts", 1)
        if (resultCode == Activity.RESULT_OK) {
            if (part == parts - 1) Dispatcher.success(context, id)
        } else {
            Dispatcher.fail(context, id, "系統錯誤碼 $resultCode")
        }
    }
}
