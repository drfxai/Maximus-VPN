package com.drfxai.maximusvpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted per-app split-tunneling lists.
 *
 * Kept separate from AppSettings because the lists can hold hundreds of package
 * names; a dedicated prefs file avoids rewriting the whole settings blob on each
 * toggle. Stored as JSON arrays of package names.
 */
class SplitTunnelRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("maximusvpn_split_tunnel", Context.MODE_PRIVATE)

    fun getAllowList(): Set<String> = readSet("allow_list")

    fun getExcludeList(): Set<String> = readSet("exclude_list")

    fun setAllowList(packages: Set<String>) = writeSet("allow_list", packages)

    fun setExcludeList(packages: Set<String>) = writeSet("exclude_list", packages)

    /** Toggle a package in the list matching the current [mode]; removes it from the other. */
    fun togglePackage(packageName: String, toAllowList: Boolean) {
        val allow = getAllowList().toMutableSet()
        val exclude = getExcludeList().toMutableSet()
        if (toAllowList) {
            exclude.remove(packageName)
            if (!allow.add(packageName)) allow.remove(packageName) // second tap untoggles
        } else {
            allow.remove(packageName)
            if (!exclude.add(packageName)) exclude.remove(packageName)
        }
        setAllowList(allow)
        setExcludeList(exclude)
    }

    private fun readSet(key: String): Set<String> {
        val raw = prefs.getString(key, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun writeSet(key: String, values: Set<String>) {
        val arr = JSONArray()
        values.sorted().forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
