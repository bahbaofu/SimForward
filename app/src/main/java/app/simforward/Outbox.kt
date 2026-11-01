package app.simforward

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 待發送／待確認的轉發任務。type = "sms" 或 "mail"。 */
data class Item(
    val id: String,
    val type: String,
    val subId: Int,
    val dest: String,
    val subject: String,
    val text: String,
    val attempts: Int,
    val nextAt: Long,
    val label: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type).put("subId", subId)
        .put("dest", dest).put("subject", subject).put("text", text)
        .put("attempts", attempts).put("nextAt", nextAt).put("label", label)

    companion object {
        fun from(o: JSONObject) = Item(
            o.getString("id"), o.getString("type"), o.getInt("subId"),
            o.getString("dest"), o.optString("subject"), o.getString("text"),
            o.getInt("attempts"), o.getLong("nextAt"), o.optString("label")
        )
    }
}

/** 以 SharedPreferences 持久化的發件匣；成功送出後會立即刪除內容。 */
object Outbox {
    private fun sp(c: Context) = c.getSharedPreferences("outbox", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<Item> {
        val raw = sp(c).getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Item.from(arr.getJSONObject(it)) }
    }

    @Synchronized
    private fun save(c: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp(c).edit().putString("items", arr.toString()).apply()
    }

    @Synchronized
    fun add(c: Context, item: Item) = save(c, all(c) + item)

    @Synchronized
    fun update(c: Context, item: Item) =
        save(c, all(c).map { if (it.id == item.id) item else it })

    @Synchronized
    fun remove(c: Context, id: String): Item? {
        val list = all(c)
        val found = list.firstOrNull { it.id == id } ?: return null
        save(c, list.filter { it.id != id })
        return found
    }
}
