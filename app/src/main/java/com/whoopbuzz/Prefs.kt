package com.whoopbuzz

import android.content.Context
import com.whoopbuzz.protocol.PatternStoreCodec
import com.whoopbuzz.protocol.SavedPattern

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("whoopbuzz", Context.MODE_PRIVATE)

    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun setToken(c: Context, t: String) = sp(c).edit().putString("token", t.trim()).apply()

    fun isServiceEnabled(c: Context): Boolean = sp(c).getBoolean("service_enabled", false)
    fun setServiceEnabled(c: Context, enabled: Boolean) =
        sp(c).edit().putBoolean("service_enabled", enabled).apply()

    fun patterns(c: Context): List<SavedPattern> =
        PatternStoreCodec.decode(sp(c).getString("patterns_v1", null))

    fun savePatterns(c: Context, list: List<SavedPattern>): List<SavedPattern> {
        val encoded = PatternStoreCodec.encode(list)
        sp(c).edit().putString("patterns_v1", encoded).apply()
        return PatternStoreCodec.decode(encoded)
    }

    fun upsertPattern(c: Context, item: SavedPattern): List<SavedPattern> =
        savePatterns(c, PatternStoreCodec.upsert(patterns(c), item))

    fun deletePattern(c: Context, name: String): List<SavedPattern> =
        savePatterns(c, PatternStoreCodec.delete(patterns(c), name))

    fun resetDefaultPatterns(c: Context): List<SavedPattern> =
        savePatterns(c, PatternStoreCodec.DEFAULT_PATTERNS)
}