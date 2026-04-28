package app.clothescast.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Process-wide ring buffer of diagnostic log entries that mirrors every call to
 * [android.util.Log] so a bug-report payload can include the last few hundred lines
 * of context (errors, warnings, info, even verbose / debug) without depending on
 * `READ_LOGS` permission, which user-installed apps don't get.
 *
 * Call sites drop in as `DiagLog.e(TAG, msg, t)` etc. — the signature mirrors
 * [android.util.Log] so the migration is a pure import-and-rename.
 *
 * On an uncaught exception the snapshot of the buffer plus the crash trace is
 * persisted to [crashLogFile]; the next launch reads that on demand from
 * [snapshot] / [readPersistedCrash] and includes it in the bug report.
 */
object DiagLog {
    private const val MAX_ENTRIES = 300

    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val timestampFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile
    private var crashFileProvider: (() -> File)? = null

    fun v(tag: String, msg: String, t: Throwable? = null) = log('V', tag, msg, t).also {
        if (t == null) Log.v(tag, msg) else Log.v(tag, msg, t)
    }

    fun d(tag: String, msg: String, t: Throwable? = null) = log('D', tag, msg, t).also {
        if (t == null) Log.d(tag, msg) else Log.d(tag, msg, t)
    }

    fun i(tag: String, msg: String, t: Throwable? = null) = log('I', tag, msg, t).also {
        if (t == null) Log.i(tag, msg) else Log.i(tag, msg, t)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) = log('W', tag, msg, t).also {
        if (t == null) Log.w(tag, msg) else Log.w(tag, msg, t)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) = log('E', tag, msg, t).also {
        if (t == null) Log.e(tag, msg) else Log.e(tag, msg, t)
    }

    /** Snapshot of the in-memory ring buffer, oldest first. */
    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    /**
     * Wires the crash handler and remembers where to spill the buffer on death.
     * Call once from [android.app.Application.onCreate].
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        crashFileProvider = { File(appContext.cacheDir, "last-crash.txt") }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the persisted crash log from the previous process, or null if absent. */
    fun readPersistedCrash(): String? = crashFileProvider?.invoke()
        ?.takeIf { it.exists() && it.length() > 0L }
        ?.runCatching { readText() }
        ?.getOrNull()

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val file = crashFileProvider?.invoke() ?: return
        val stack = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()
        val header = "Uncaught exception on thread \"${thread.name}\""
        log('E', "DiagLog", "$header: ${throwable.javaClass.name}: ${throwable.message}", throwable)
        val recent = snapshot().joinToString("\n")
        file.parentFile?.mkdirs()
        file.writeText(buildString {
            appendLine("=== ${timestampFormat.get().format(Date())} ===")
            appendLine(header)
            appendLine(stack)
            appendLine("--- recent log ---")
            append(recent)
        })
    }

    private fun log(level: Char, tag: String, msg: String, t: Throwable?) {
        val timestamp = timestampFormat.get().format(Date())
        val formatted = if (t == null) {
            "$timestamp $level $tag: $msg"
        } else {
            val stack = StringWriter().also { sw ->
                PrintWriter(sw).use { t.printStackTrace(it) }
            }.toString().trimEnd()
            "$timestamp $level $tag: $msg\n$stack"
        }
        synchronized(buffer) {
            if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(formatted)
        }
    }
}
