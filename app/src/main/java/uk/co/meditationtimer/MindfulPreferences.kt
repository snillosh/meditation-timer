package uk.co.meditationtimer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

fun encodeMindful(p: MindfulConfig): String = JSONObject().apply {
    put("enabled",p.enabled);put("activity",p.activity);put("duration",p.durationMinutes)
    put("schedule",p.schedule);put("days",JSONArray(p.days.sorted()));put("times",JSONArray(p.fixedTimes))
    put("start",p.startMinute);put("end",p.endMinute);put("count",p.nudges);put("seed",p.seed)
}.toString()

fun decodeMindful(text: String?): MindfulConfig = runCatching {
    val j=JSONObject(text ?: return MindfulConfig())
    val days=j.getJSONArray("days");val times=j.getJSONArray("times")
    MindfulConfig(j.getBoolean("enabled"),j.getString("activity"),j.getInt("duration"),j.getString("schedule"),
        (0 until days.length()).map {days.getInt(it)}.toSet(),(0 until times.length()).map {times.getInt(it)},
        j.getInt("start"),j.getInt("end"),j.getInt("count"),j.getLong("seed")).also { require(it.error()==null) }
}.getOrElse { MindfulConfig() }

class MindfulPreferences(private val context: Context) {
    private val key=stringPreferencesKey("mindful_config")
    val flow=context.preferences.data.map { decodeMindful(it[key]) }
    suspend fun save(value: MindfulConfig) {
        require(value.error()==null) { value.error() ?: "Invalid Mindful Minutes settings." }
        context.preferences.edit {
            val seed=decodeMindful(it[key]).seed.takeIf {s -> s!=0L} ?: java.security.SecureRandom().nextLong().let {s -> if(s==0L) 1L else s}
            it[key]=encodeMindful(value.copy(seed=seed))
        }
    }
}
