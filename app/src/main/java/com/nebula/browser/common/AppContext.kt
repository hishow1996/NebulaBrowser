package com.nebula.browser.common

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit

object AppContext {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val prefs: SharedPreferences
        get() = appContext.getSharedPreferences("nebula_prefs", Context.MODE_PRIVATE)
}

fun toast(message: String, long: Boolean = false) {
    Toast.makeText(AppContext.appContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun putPref(key: String, value: Any?) {
    AppContext.prefs.edit {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
            null -> remove(key)
        }
    }
}

fun getPrefString(key: String, default: String = ""): String =
    AppContext.prefs.getString(key, default) ?: default

fun getPrefBoolean(key: String, default: Boolean = false): Boolean =
    AppContext.prefs.getBoolean(key, default)

fun getPrefInt(key: String, default: Int = 0): Int =
    AppContext.prefs.getInt(key, default)
