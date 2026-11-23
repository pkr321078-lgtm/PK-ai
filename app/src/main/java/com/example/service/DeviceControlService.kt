package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.network.AiVisionAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class DeviceActionResult(
    val success: Boolean,
    val message: String
)

data class LoadedDocumentPayload(
    val title: String,
    val mimeType: String,
    val contentText: String
)

class DeviceControlService(private val context: Context) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pk_jarvis_reminders"
    }

    private var torchEnabled: Boolean = false

    fun isNetworkOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun hasFlashlightHardware(): Boolean {
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        } catch (_: Throwable) {
            false
        }
    }

    fun toggleFlashlight(): DeviceActionResult {
        if (!hasFlashlightHardware()) {
            return DeviceActionResult(false, "No flashlight hardware detected on this device.")
        }
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return DeviceActionResult(false, "No camera with flash unit found.")

            val nextState = !torchEnabled
            cameraManager.setTorchMode(flashCameraId, nextState)
            torchEnabled = nextState
            DeviceActionResult(true, if (nextState) "Flashlight turned ON." else "Flashlight turned OFF.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "Could not toggle flashlight: ${t.message ?: "hardware busy"}")
        }
    }

    fun isTorchEnabled(): Boolean = torchEnabled

    fun copyToClipboard(label: String, text: String): DeviceActionResult {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            DeviceActionResult(true, "Copied to clipboard.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "Clipboard copy failed: ${t.message}")
        }
    }

    fun shareText(title: String, text: String): DeviceActionResult {
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            DeviceActionResult(true, "Opened Android Share sheet.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "Share failed: ${t.message}")
        }
    }

    fun openHttpsUrl(rawUrl: String): DeviceActionResult {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return DeviceActionResult(false, "Blocked non-HTTPS URL for safety.")
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Opened link in browser.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "No browser available: ${t.message}")
        }
    }

    fun openInstalledApp(packageName: String, displayName: String, fallbackUrl: String? = null): DeviceActionResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                DeviceActionResult(true, "Launched $displayName.")
            } else if (fallbackUrl != null) {
                openHttpsUrl(fallbackUrl)
            } else {
                DeviceActionResult(false, "$displayName is not installed on this device.")
            }
        } catch (t: Throwable) {
            DeviceActionResult(false, "Could not launch $displayName: ${t.message}")
        }
    }

    fun openCameraApp(): DeviceActionResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Opened device Camera app.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "Camera app unavailable: ${t.message}")
        }
    }

    /**
     * Opens the system Phone Dialer pre-populated with [phoneNumber] — never dials silently
     * without user confirmation.
     */
    fun openDialerWithNumber(phoneNumber: String): DeviceActionResult {
        val sanitized = phoneNumber.filter { it.isDigit() || it == '+' }
        if (sanitized.length < 3) {
            return DeviceActionResult(false, "Enter a valid phone number first.")
        }
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Opened Phone Dialer for $sanitized. Confirm call in dialer.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "Phone dialer unavailable: ${t.message}")
        }
    }

    fun setSystemAlarm(hour: Int, minute: Int, label: String): DeviceActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "PK JARVIS Reminder" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Opened Android Clock to save alarm for %02d:%02d.".format(hour, minute))
        } catch (t: Throwable) {
            DeviceActionResult(false, "Android Clock app unavailable: ${t.message}")
        }
    }

    fun postLocalNotification(title: String, body: String): DeviceActionResult {
        if (!hasNotificationPermission()) {
            return DeviceActionResult(false, "Notification permission not granted.")
        }
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "PK JARVIS Reminders & Automations",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
            DeviceActionResult(true, "Local notification delivered.")
        } catch (t: Throwable) {
            DeviceActionResult(false, "Could not show notification: ${t.message}")
        }
    }

    suspend fun encodeImageUriToVisionAttachment(uri: Uri): AiVisionAttachment? =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val original = stream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
                val maxDim = 1024
                val scale = minOf(
                    maxDim.toFloat() / original.width.coerceAtLeast(1),
                    maxDim.toFloat() / original.height.coerceAtLeast(1),
                    1.0f
                )
                val resized = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    original
                }

                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 82, out)
                if (resized !== original) resized.recycle()
                original.recycle()

                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                AiVisionAttachment(mimeType = "image/jpeg", base64Data = b64)
            } catch (_: Throwable) {
                null
            }
        }

    suspend fun readDocumentTextFromUri(uri: Uri): LoadedDocumentPayload? =
        withContext(Dispatchers.IO) {
            try {
                var displayName = "Uploaded Document"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }
                val mimeType = context.contentResolver.getType(uri) ?: "text/plain"
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0
                    val maxBytes = 300_000 // Safe cap for low-RAM devices
                    while (total < maxBytes) {
                        val read = input.read(chunk, 0, minOf(chunk.size, maxBytes - total))
                        if (read <= 0) break
                        buf.write(chunk, 0, read)
                        total += read
                    }
                    buf.toByteArray()
                } ?: return@withContext null

                val extractedText = if (mimeType.contains("pdf", ignoreCase = true) ||
                    displayName.endsWith(".pdf", ignoreCase = true)
                ) {
                    // Best-effort readable ASCII/UTF-8 text extraction for simple text PDFs
                    extractReadableTextFromPdfBytes(rawBytes)
                } else {
                    String(rawBytes, StandardCharsets.UTF_8)
                }.trim().take(24_000)

                if (extractedText.isBlank()) {
                    return@withContext LoadedDocumentPayload(
                        title = displayName,
                        mimeType = mimeType,
                        contentText = "[Binary or scanned PDF detected — use the Vision tab to upload a screenshot/photo of pages for OCR.]"
                    )
                }

                LoadedDocumentPayload(
                    title = displayName,
                    mimeType = mimeType,
                    contentText = extractedText
                )
            } catch (_: Throwable) {
                null
            }
        }

    private fun extractReadableTextFromPdfBytes(bytes: ByteArray): String {
        val raw = String(bytes, StandardCharsets.ISO_8859_1)
        val regex = Regex("\\(([^()]{2,200})\\)")
        val chunks = regex.findAll(raw)
            .map { it.groupValues[1].replace("\\n", "\n").replace("\\r", " ") }
            .filter { segment -> segment.count { it.isLetterOrDigit() } >= 2 }
            .take(400)
            .toList()
        return chunks.joinToString(" ").trim()
    }
}
