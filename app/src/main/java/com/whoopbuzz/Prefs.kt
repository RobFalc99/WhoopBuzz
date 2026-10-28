package com.whoopbuzz

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("whoopbuzz", Context.MODE_PRIVATE)
    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun setToken(c: Context, t: String) = sp(c).edit().putString("token", t.trim()).apply()
}