package com.kithandkeen.qonverse

import androidx.test.core.app.ApplicationProvider
import com.kithandkeen.qonverse.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun backupRestoresOnAFreshPhone() {
        val store = Store(ctx).apply { seedSamples() }
        store.setFamiliar("hotel-front-office-ja", "ja-check-in-01", true)
        store.setFamiliar("hotel-front-office-ja", "ja-check-in-02", true)
        store.themeMode = "amoled"
        store.delete("restaurant-server-th")
        val backup = store.exportBackup()
        File("build/backup-sample.json").writeText(backup)

        // wipe everything, as if on a new phone
        ctx.getSharedPreferences("qonverse", 0).edit().clear().commit()
        File(ctx.filesDir, "packs").deleteRecursively()
        val fresh = Store(ctx).apply { seedSamples() }
        assertEquals(4, fresh.loadAll().size)

        val r = fresh.restoreBackup(backup)
        assertEquals(3, r.packs)
        assertEquals(setOf("ja-check-in-01", "ja-check-in-02"), fresh.familiar("hotel-front-office-ja"))
        assertEquals("amoled", fresh.themeMode)
        assertEquals(3, fresh.loadAll().size)
        assertTrue(fresh.loadAll().none { it.pack.id == "restaurant-server-th" })
    }

    @Test fun packScreenRejectsABackup() {
        val store = Store(ctx).apply { seedSamples() }
        val r = store.parse(store.exportBackup())
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("backup"))
    }
}
