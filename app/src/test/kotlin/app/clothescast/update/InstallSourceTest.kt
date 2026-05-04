package app.clothescast.update

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pure-JVM test for the install-source decision. The Android-side wrapper
 * ([InstallSource.isFromPlayStore]) is a thin one-liner over PackageManager;
 * the meaningful logic — which installer strings count as "Play" — lives in
 * [InstallSource.isPlayStoreInstaller], so that's what we gate here.
 *
 * If we ever broaden the set (e.g. accept Play Beta as Play), one new line in
 * `PLAY_INSTALLERS` plus a matching case here is the whole change.
 */
class InstallSourceTest {

    @Test
    fun `modern Play Store installer is recognised`() {
        InstallSource.isPlayStoreInstaller("com.android.vending") shouldBe true
    }

    @Test
    fun `legacy Play Store installer is recognised`() {
        // Older system images recorded `com.google.android.feedback` as the
        // installer for Play installs. Devices that updated to a newer Play
        // Store keep the original installer string on records made under it.
        InstallSource.isPlayStoreInstaller("com.google.android.feedback") shouldBe true
    }

    @Test
    fun `F-Droid installer is not recognised`() {
        InstallSource.isPlayStoreInstaller("org.fdroid.fdroid") shouldBe false
    }

    @Test
    fun `Amazon Appstore installer is not recognised`() {
        InstallSource.isPlayStoreInstaller("com.amazon.venezia") shouldBe false
    }

    @Test
    fun `package installer (system sideload UI) is not recognised`() {
        // adb install / "Install unknown apps" via the system installer.
        InstallSource.isPlayStoreInstaller("com.google.android.packageinstaller") shouldBe false
    }

    @Test
    fun `null installer is not recognised`() {
        // Some sideload paths leave the installer field unset.
        InstallSource.isPlayStoreInstaller(null) shouldBe false
    }

    @Test
    fun `empty string is not recognised`() {
        InstallSource.isPlayStoreInstaller("") shouldBe false
    }
}
