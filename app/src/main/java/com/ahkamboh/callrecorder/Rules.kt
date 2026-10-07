package com.ahkamboh.callrecorder

import android.content.Context
import android.telephony.PhoneNumberUtils
import org.json.JSONArray
import org.json.JSONObject

data class RuleNumber(val number: String, val name: String?)

enum class RuleMode { ALL, ONLY, EXCEPT }

/** Which calls get recorded: everything, only a list of numbers, or everything except a list. */
data class Rules(val mode: RuleMode, val numbers: List<RuleNumber>) {

    fun contains(number: String): Boolean = numbers.any { same(it.number, number) }

    /** `null` = number unknown. Unknown numbers are recorded unless mode is ONLY. */
    fun shouldRecord(number: String?): Boolean = when (mode) {
        RuleMode.ALL -> true
        RuleMode.ONLY -> number != null && contains(number)
        RuleMode.EXCEPT -> !(number != null && contains(number))
    }

    fun plusAll(list: List<RuleNumber>): Rules {
        val out = numbers.toMutableList()
        for (n in list) if (n.number.isNotBlank() && out.none { same(it.number, n.number) }) out += n
        return copy(numbers = out)
    }

    fun plus(n: RuleNumber) = plusAll(listOf(n))

    fun minus(n: RuleNumber) = copy(numbers = numbers.filterNot { it.number == n.number })

    fun summary(): String = when (mode) {
        RuleMode.ALL -> "all numbers"
        RuleMode.ONLY -> "only ${numbers.size} number${if (numbers.size == 1) "" else "s"}"
        RuleMode.EXCEPT -> "all except ${numbers.size} number${if (numbers.size == 1) "" else "s"}"
    }

    companion object {
        /** Tolerates +92 300 vs 0300 style differences. */
        fun same(a: String, b: String): Boolean = PhoneNumberUtils.compare(a, b)

        fun load(ctx: Context): Rules {
            val mode = RuleMode.entries.getOrElse(Prefs.getInt(ctx, Prefs.KEY_RULE_MODE, 0)) { RuleMode.ALL }
            val list = ArrayList<RuleNumber>()
            try {
                val arr = JSONArray(Prefs.getString(ctx, Prefs.KEY_RULE_NUMBERS) ?: "[]")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list += RuleNumber(o.getString("n"), o.optString("name").takeIf { it.isNotBlank() })
                }
            } catch (_: Exception) {
            }
            return Rules(mode, list)
        }

        fun save(ctx: Context, rules: Rules) {
            val arr = JSONArray()
            rules.numbers.forEach { arr.put(JSONObject().put("n", it.number).put("name", it.name ?: "")) }
            Prefs.put(ctx, Prefs.KEY_RULE_MODE, rules.mode.ordinal)
            Prefs.put(ctx, Prefs.KEY_RULE_NUMBERS, arr.toString())
        }
    }
}
