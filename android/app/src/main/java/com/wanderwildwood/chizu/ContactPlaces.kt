package com.wanderwildwood.chizu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.core.content.ContextCompat

/**
 * People in the phone's contacts who have a postal address, offered in the search while their
 * name is being typed. Read on the phone, at that moment, and kept nowhere; without the Contacts
 * permission the list is simply empty.
 */
object ContactPlaces {
    class Place(@JvmField val name: String, @JvmField val address: String)

    private const val LONGEST = 3

    @JvmStatic
    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Up to three people one of whose names begins with [typed] (two letters or more). */
    @JvmStatic
    fun find(context: Context, typed: String?): List<Place> {
        val start = typed?.trim().orEmpty()
        if (start.length < 2 || !allowed(context)) return emptyList()
        val like = escape(start)
        val found = ArrayList<Place>()
        try {
            context.contentResolver.query(
                StructuredPostal.CONTENT_URI,
                arrayOf(StructuredPostal.DISPLAY_NAME, StructuredPostal.FORMATTED_ADDRESS),
                "${StructuredPostal.DISPLAY_NAME} LIKE ? ESCAPE '\\' OR ${StructuredPostal.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
                arrayOf("$like%", "% $like%"),
                "${StructuredPostal.DISPLAY_NAME} COLLATE NOCASE",
            )?.use { rows ->
                while (rows.moveToNext() && found.size < LONGEST) {
                    val name = rows.getString(0)?.trim().orEmpty()
                    val address = oneLine(rows.getString(1))
                    if (name.isNotEmpty() && address.isNotEmpty() && found.none { it.name == name && it.address == address })
                        found.add(Place(name, address))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return found
    }

    /** An address as the contacts keep it runs over several lines; the search box wants one. */
    @JvmStatic
    fun oneLine(address: String?): String =
        address.orEmpty().split('\n', '\r').map { it.trim().trimEnd(',') }.filter { it.isNotEmpty() }.joinToString(", ")

    /** [typed] with LIKE's own wildcards made literal. */
    @JvmStatic
    fun escape(typed: String): String = typed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
