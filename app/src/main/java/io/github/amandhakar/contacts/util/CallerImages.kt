package io.github.amandhakar.contacts.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import io.github.amandhakar.contacts.data.Prefs
import java.io.File

/**
 * Images picked for the caller screen (a wallpaper and a caller photo, for everyone or for one contact).
 *
 * Picked images are copied into app storage, scaled down and turned upright, under a new file name each time.
 * The new name matters: the avatar cache and the screens key on the address, so reusing one name kept showing
 * the old picture after a new one was picked. Scaling matters too: a full-size camera photo decoded as is can
 * take hundreds of MB and crash the call screen.
 */
object CallerImages {
    private const val DIR = "caller-images"

    /** Wallpaper size limit (long side, px). Big enough for a phone screen, small enough to decode quickly during a ring. */
    const val BACKGROUND_MAX = 1920
    /** Caller photo size limit. It is shown at most 112 dp wide. */
    const val PHOTO_MAX = 720

    /** The wallpaper for a call: the contact's own, else the one for everyone. */
    fun backgroundFor(prefs: Prefs, contactId: Long?): String? =
        contactId?.let { prefs.contactCallerBackgroundUri(it) } ?: prefs.callerBackgroundUri

    /** The photo for a call: the contact's custom one, else their photo in Contacts, else the default caller image. */
    fun photoFor(prefs: Prefs, contactId: Long?, contactsPhoto: String?): String? =
        contactId?.let { prefs.contactCallerPhotoUri(it) } ?: contactsPhoto ?: prefs.callerFallbackPhotoUri

    /** The file name prefix for one slot, such as "contact-12-background" or "global-photo". */
    fun slot(contactId: Long?, kind: String): String =
        if (contactId == null) "global-$kind" else "contact-$contactId-$kind"

    /**
     * Copies [source] into app storage as a scaled, upright JPEG and returns its file:// address, or null if it
     * could not be read. Earlier images for the same slot are deleted. Call off the main thread.
     */
    fun import(ctx: Context, source: Uri, slot: String, maxSide: Int): String? {
        return try {
            val bitmap = decode(ctx, source, maxSide) ?: return null
            val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
            val target = File(dir, "$slot-${System.currentTimeMillis()}.jpg")
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
            dir.listFiles()?.forEach { f -> if (f != target && (f.name.startsWith("$slot-") || f.name.startsWith("$slot."))) f.delete() }
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Deletes every stored image for [slot]. */
    fun delete(ctx: Context, slot: String) {
        File(ctx.filesDir, DIR).listFiles()?.forEach { f -> if (f.name.startsWith("$slot-") || f.name.startsWith("$slot.")) f.delete() }
    }

    /**
     * Decodes [uri] so that its long side is at most about [maxSide] px, turned upright from the camera's EXIF
     * orientation. Returns null if it cannot be read. Call off the main thread.
     */
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= maxSide) sample *= 2
            val decoded = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val rotation = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) { 0f }
            val longSide = maxOf(decoded.width, decoded.height)
            val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
            if (rotation == 0f && scale == 1f) return decoded
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                .also { if (it !== decoded) decoded.recycle() }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun decode(ctx: Context, uri: String?, maxSide: Int): Bitmap? =
        if (uri.isNullOrBlank()) null else decode(ctx, Uri.parse(uri), maxSide)
}
