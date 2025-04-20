package cs.ok3vo.five9record.util

import android.content.SharedPreferences

inline fun<E> SharedPreferences.getEnumValue(valueOf: (String) -> E?, key: String): E?
    = getString(key, null)?.let(valueOf)
