package app.clothescast.diag

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Validates the file-based ack tracking that backs the post-crash banner on
 * the Today screen — see [DiagLog.hasUnacknowledgedCrash] and
 * [DiagLog.acknowledgePersistedCrash]. Identity of "the current crash" is
 * a content hash of the crash file, so a fresh crash hashes differently and
 * the banner re-surfaces even when filesystem mtime resolution (1s on ext4)
 * would have collided with the previously-acked crash.
 */
class CrashAckTest {

    @Test
    fun `no crash file means nothing to acknowledge`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe false
    }

    @Test
    fun `empty crash file is treated as no crash`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        crash.writeText("")
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe false
    }

    @Test
    fun `unacked crash surfaces`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        crash.writeText("boom")
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe true
    }

    @Test
    fun `acknowledging silences the banner`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        crash.writeText("boom")
        DiagLog.writeCrashAcknowledgement(crash, ack)
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe false
    }

    @Test
    fun `a fresh crash after an ack re-surfaces`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        crash.writeText("first")
        DiagLog.writeCrashAcknowledgement(crash, ack)
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe false

        crash.writeText("second")
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe true
    }

    @Test
    fun `mtime collision with new content still re-surfaces`(@TempDir dir: Path) {
        // Filesystem mtime can tick at 1s granularity (ext4 default) so a
        // fast crash / restart loop can produce two different crash logs that
        // share an mtime. Identity must be content, not mtime, or the second
        // crash would be silently swallowed as already-acked.
        val (crash, ack) = files(dir)
        crash.writeText("first")
        val sharedMtime = crash.lastModified()
        DiagLog.writeCrashAcknowledgement(crash, ack)
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe false

        crash.writeText("second")
        crash.setLastModified(sharedMtime)
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe true
    }

    @Test
    fun `corrupt ack file falls back to surfacing`(@TempDir dir: Path) {
        // If the ack file ever ends up with junk that doesn't match the
        // current crash hash (manual tamper, truncated write), prefer to
        // show the banner once more rather than swallow a real crash report.
        val (crash, ack) = files(dir)
        crash.writeText("boom")
        ack.writeText("not-a-hash")
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe true
    }

    @Test
    fun `empty ack file falls back to surfacing`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        crash.writeText("boom")
        ack.writeText("")
        DiagLog.isCrashUnacknowledged(crash, ack) shouldBe true
    }

    @Test
    fun `acknowledgement is a no-op when there's no crash to ack`(@TempDir dir: Path) {
        val (crash, ack) = files(dir)
        DiagLog.writeCrashAcknowledgement(crash, ack)
        ack.exists() shouldBe false
    }

    private fun files(dir: Path): Pair<File, File> =
        dir.resolve("last-crash.txt").toFile() to dir.resolve("last-crash.ack").toFile()
}
