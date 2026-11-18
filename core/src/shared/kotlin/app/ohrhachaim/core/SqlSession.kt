package app.ohrhachaim.core

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY

/**
 * Bind indexes are 1-based, matching sqlite3_bind_*.
 * Column indexes are 0-based, matching sqlite3_column_* and this driver's getters.
 */
interface SqlBinder {
    fun bindLong(index: Int, value: Long)
    fun bindText(index: Int, value: String)
}

interface SqlRow {
    fun isNull(index: Int): Boolean
    fun getLong(index: Int): Long
    fun getInt(index: Int): Int
    fun getText(index: Int): String
}

interface SqlSession : AutoCloseable {
    fun <T> query(sql: String, bind: (SqlBinder) -> Unit = {}, map: (SqlRow) -> T): List<T>
}

/**
 * Opens the bundled commentary database read-only with the AndroidX SQLite that is compiled with FTS5.
 * Platform SQLite on Android is not. The connection is serialized (`SQLITE_OPEN_FULLMUTEX`).
 */
fun openBundledSession(path: String): SqlSession {
    val connection = BundledSQLiteDriver().open(path, SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX)
    return BundledSqlSession(connection)
}

private class BundledSqlSession(private val connection: SQLiteConnection) : SqlSession {
    override fun <T> query(sql: String, bind: (SqlBinder) -> Unit, map: (SqlRow) -> T): List<T> =
        synchronized(connection) {
            connection.prepare(sql).use { statement ->
                bind(StatementBinder(statement))
                val rows = mutableListOf<T>()
                while (statement.step()) {
                    rows += map(StatementRow(statement))
                }
                rows
            }
        }

    override fun close() {
        synchronized(connection) { connection.close() }
    }
}

private class StatementBinder(private val statement: SQLiteStatement) : SqlBinder {
    override fun bindLong(index: Int, value: Long) = statement.bindLong(index, value)
    override fun bindText(index: Int, value: String) = statement.bindText(index, value)
}

private class StatementRow(private val statement: SQLiteStatement) : SqlRow {
    override fun isNull(index: Int): Boolean = statement.isNull(index)
    override fun getLong(index: Int): Long = statement.getLong(index)
    override fun getInt(index: Int): Int = statement.getLong(index).toInt()
    override fun getText(index: Int): String = if (statement.isNull(index)) "" else statement.getText(index)
}
