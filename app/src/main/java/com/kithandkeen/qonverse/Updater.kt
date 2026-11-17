package com.kithandkeen.qonverse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class UpdateInfo(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    val notes: String = "",
)

/**
 * Preview builds only: checks docs/version.json in the GitHub repo when the app opens, and if a newer
 * version exists, downloads the APK and hands it to Android's installer. Android always asks the user
 * to confirm an install; apps outside Google Play cannot update silently.
 */
object Updater {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(BuildConfig.UPDATE_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000; useCaches = false
            }
            c.inputStream.bufferedReader().use { r -> json.decodeFromString(UpdateInfo.serializer(), r.readText()) }
                .takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }.getOrNull()
    }

    suspend fun download(context: Context, info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "Qonverse-${info.versionName}.apk")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 10000; readTimeout = 20000; instanceFollowRedirects = true }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); continue }
            check(code == 200) { "Download failed ($code)" }
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    if (total > 0) progress(done / total.toFloat())
                }
            }
        }
        out
    }

    /** True if Android will let Qonverse open the installer (the user may need to allow it once). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
