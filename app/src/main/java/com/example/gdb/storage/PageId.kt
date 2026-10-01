package com.example.gdb.storage

/**
 * Unique identifier for a page in GDB-SPEC storage.
 */
data class PageId(
    val fileId: Int,
    val pageNumber: Long
) {
    override fun toString(): String = "PageId($fileId:$pageNumber)"
}
