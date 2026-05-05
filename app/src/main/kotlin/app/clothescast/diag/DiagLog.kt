package app.clothescast.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
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

    @Volatile
    private var ackFileProvider: (() -> File)? = null

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
        ackFileProvider = { File(appContext.cacheDir, "last-crash.ack") }
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

    /**
     * True iff a crash from the previous process is on disk and the user hasn't
     * acknowledged it yet. The Today screen polls this on launch / resume to
     * surface a "share crash report" banner; tapping either button on the
     * banner calls [acknowledgePersistedCrash].
     *
     * Identity is a content hash of the crash file. Filesystem mtime tick can
     * be coarser than ms (1s on ext4) and would collide in a fast crash loop;
     * hashing the bytes — which include a ms-precision timestamp header and
     * the recent log buffer — gives a fresh identity for each new crash.
     */
    fun hasUnacknowledgedCrash(): Boolean {
        val crash = crashFileProvider?.invoke() ?: return false
        val ack = ackFileProvider?.invoke() ?: return false
        return isCrashUnacknowledged(crash, ack)
    }

    /** Records the currently persisted crash as seen — see [hasUnacknowledgedCrash]. */
    fun acknowledgePersistedCrash() {
        val crash = crashFileProvider?.invoke() ?: return
        val ack = ackFileProvider?.invoke() ?: return
        writeCrashAcknowledgement(crash, ack)
    }

    internal fun isCrashUnacknowledged(crashFile: File, ackFile: File): Boolean {
        if (!crashFile.exists() || crashFile.length() == 0L) return false
        if (!ackFile.exists()) return true
        val ackedHash = runCatching { ackFile.readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return true
        val currentHash = runCatching { crashIdentity(crashFile) }.getOrNull()
            ?: return true
        return ackedHash != currentHash
    }

    internal fun writeCrashAcknowledgement(crashFile: File, ackFile: File) {
        if (!crashFile.exists()) return
        runCatching {
            val hash = crashIdentity(crashFile)
            ackFile.parentFile?.mkdirs()
            ackFile.writeText(hash)
        }
    }

    private fun crashIdentity(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

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
