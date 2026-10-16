package com.keeltv.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

private const val SET_ASIDE = "set_aside_index"

fun SupportSQLiteDatabase.setCatalogIndexesAside() {
  inTransaction {
    execSQL("CREATE TABLE IF NOT EXISTS $SET_ASIDE (name TEXT PRIMARY KEY NOT NULL, sql TEXT NOT NULL)")
    // Only the indexes Room declared: the primary keys are SQLite's own, carry no `sql`, and are
    // what the load's inserts and the clear before it find rows by.
    execSQL(
      "INSERT OR REPLACE INTO $SET_ASIDE SELECT name, sql FROM sqlite_master " +
        "WHERE type = 'index' AND tbl_name IN ('media_item', 'media_category') AND sql IS NOT NULL",
    )
    for ((name, _) in setAside()) execSQL("DROP INDEX IF EXISTS `$name`")
  }
}

fun SupportSQLiteDatabase.restoreCatalogIndexes() {
  val exists = query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$SET_ASIDE'")
    .use { it.moveToFirst() }
  if (!exists) return
  for ((name, sql) in setAside()) {
    inTransaction {
      execSQL(sql)
      execSQL("DELETE FROM $SET_ASIDE WHERE name = ?", arrayOf(name))
    }
  }
}

private fun SupportSQLiteDatabase.setAside(): List<Pair<String, String>> =
  query("SELECT name, sql FROM $SET_ASIDE").use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
  }

private inline fun SupportSQLiteDatabase.inTransaction(block: () -> Unit) {
  beginTransaction()
  try {
    block()
    setTransactionSuccessful()
  } finally {
    endTransaction()
  }
}
