package com.sysadmindoc.nimbus.wear.testing

import android.content.SharedPreferences

/**
 * In-memory implementation of [SharedPreferences] for unit tests.
 *
 * Backed by a plain [MutableMap]. Matches the semantics that
 * [com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore] relies on:
 *  - `apply()` and `commit()` both flush synchronously into the map.
 *  - `remove(key)` deletes the entry on commit.
 *  - reads after a commit reflect the latest writes.
 *
 * Listener registration and the full `getAll` shape are stubbed out
 * because nothing the store uses needs them.
 */
class FakeSharedPreferences : SharedPreferences {
    private val store = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = store.toMap()

    override fun getString(key: String, defValue: String?): String? =
        store[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        store[key] as? Set<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        (store[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (store[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (store[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (store[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(store)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private class FakeEditor(
        private val store: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() {
            flush()
        }

        private fun flush() {
            if (clearAll) store.clear()
            for (k in removals) store.remove(k)
            store.putAll(pending)
            pending.clear()
            removals.clear()
            clearAll = false
        }
    }
}
