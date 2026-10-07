package com.ahkamboh.callrecorder

import android.provider.CallLog

/** The call-log row written after a call ends; only used to name the file. */
data class CallInfo(val number: String, val type: Int) {
    val directionTag: String
        get() = if (type == CallLog.Calls.OUTGOING_TYPE) "out" else "in"

    val safeNumber: String
        get() = number.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
}
