package com.ahkamboh.callrecorder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.content.ComponentName
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

data class Recording(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folder: String,
    val durationMs: Long,
    val size: Long,
    val date: Long,
    val outgoing: Boolean,
    /** Absolute path on disk, used to share under the real file name. */
    val path: String?,
)

data class Folder(val number: String, val displayName: String?, val recordings: List<Recording>) {
    val title: String
        get() = displayName ?: if (number == Recorder.UNKNOWN) "Unknown number" else number
    val totalDurationMs: Long
        get() = recordings.sumOf { it.durationMs }
    val latest: Long
        get() = recordings.maxOfOrNull { it.date } ?: 0L
}

data class Setup(val service: Boolean, val mic: Boolean, val phone: Boolean, val callLog: Boolean, val notif: Boolean) {
    val ready: Boolean get() = service && mic && phone

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        fun read(ctx: Context): Setup {
            fun g(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            return Setup(
                service = serviceEnabled(ctx),
                mic = g(Manifest.permission.RECORD_AUDIO),
                phone = g(Manifest.permission.READ_PHONE_STATE),
                callLog = g(Manifest.permission.READ_CALL_LOG),
                notif = g(Manifest.permission.POST_NOTIFICATIONS),
            )
        }

        fun serviceEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, CallRecorderService::class.java)
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

/** Everything that touches storage, the call log and other apps. */
object Repo {
    private const val TAG = "CallRecorder"
    private const val FILE_AUTHORITY = "com.ahkamboh.callrecorder.files"
    private val NAME_RE = Regex("""_(in|out)(?:_([+\d]+))?\.m4a$""")

    private fun numberKey(n: String) = n.filter { it.isDigit() }.takeLast(9)

    fun load(ctx: Context): List<Folder> {
        val base = Recorder.basePath()
        val recs = ArrayList<Recording>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("$base%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val rel = c.getString(2) ?: base
                val sub = rel.removePrefix(base).trim('/')
                val m = NAME_RE.find(name)
                // Files from v1 sit directly in CallRecordings/; group them by the number in their name.
                val folder = sub.ifEmpty { m?.groupValues?.getOrNull(2)?.ifEmpty { null } ?: Recorder.UNKNOWN }
                recs += Recording(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    name = name,
                    folder = folder,
                    durationMs = c.getLong(3),
                    size = c.getLong(4),
                    date = c.getLong(5) * 1000,
                    outgoing = m?.groupValues?.getOrNull(1) == "out",
                    path = c.getString(6),
                )
            }
        }
        val names = cachedNames(ctx)
        return recs.groupBy { it.folder }
            .map { (num, list) -> Folder(num, names[numberKey(num)], list.sortedByDescending { it.date }) }
            .sortedByDescending { it.latest }
    }

    /** Contact names as the call log cached them; avoids needing the contacts permission. */
    private fun cachedNames(ctx: Context): Map<String, String> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyMap()
        val out = HashMap<String, String>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows++ < 1000) {
                    val n = c.getString(0) ?: continue
                    val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    out.putIfAbsent(numberKey(n), name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "call log names failed", e)
        }
        return out
    }

    /** Distinct numbers from the call log, newest first, for the rules picker. */
    fun recentNumbers(ctx: Context): List<RuleNumber> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = ArrayList<RuleNumber>()
        val seen = HashSet<String>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows++ < 1000 && out.size < 150) {
                    val n = c.getString(0)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    if (!seen.add(numberKey(n))) continue
                    out += RuleNumber(n, c.getString(1)?.takeIf { it.isNotBlank() })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "recent numbers failed", e)
        }
        return out
    }

    /** Reads the number the system contact picker returned (temporary grant, no permission needed). */
    fun pickedContact(ctx: Context, uri: Uri): RuleNumber? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        return try {
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                    RuleNumber(n, c.getString(1)?.takeIf { it.isNotBlank() })
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "contact read failed", e)
            null
        }
    }

    fun delete(ctx: Context, recs: List<Recording>): Int {
        var n = 0
        for (r in recs) {
            try {
                n += ctx.contentResolver.delete(r.uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "delete failed for ${r.name}", e)
            }
        }
        return n
    }

    /** Copies into the folder the user picked, keeping one sub-folder per number. Returns copied count. */
    fun copyTo(ctx: Context, tree: Uri, recs: List<Recording>): Int {
        val resolver = ctx.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val dirs = HashMap<String, Uri>()
        var n = 0
        for (r in recs) {
            try {
                val dir = dirs.getOrPut(r.folder) { findOrCreateDir(ctx, tree, root, r.folder) ?: root }
                val target = DocumentsContract.createDocument(resolver, dir, "audio/mp4", r.name) ?: continue
                val input = resolver.openInputStream(r.uri) ?: continue
                val output = resolver.openOutputStream(target) ?: continue
                input.use { i -> output.use { o -> i.copyTo(o) } }
                n++
            } catch (e: Exception) {
                Log.w(TAG, "copy failed for ${r.name}", e)
            }
        }
        return n
    }

    private fun findOrCreateDir(ctx: Context, tree: Uri, parent: Uri, name: String): Uri? {
        val resolver = ctx.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        try {
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "listing destination failed", e)
        }
        return try {
            DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
        } catch (e: Exception) {
            Log.w(TAG, "mkdir failed", e)
            null
        }
    }

    /**
     * A FileProvider URI carries the real file name to WhatsApp and friends; the
     * MediaStore URI (which some apps label by its numeric id) is the fallback.
     */
    private fun shareUri(ctx: Context, r: Recording): Uri {
        val f = r.path?.let { File(it) }
        return if (f != null && f.canRead()) {
            try {
                FileProvider.getUriForFile(ctx, FILE_AUTHORITY, f)
            } catch (e: IllegalArgumentException) {
                r.uri
            }
        } else r.uri
    }

    fun shareIntent(ctx: Context, recs: List<Recording>): Intent {
        val uris = ArrayList(recs.map { shareUri(ctx, it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = "audio/mp4"
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newUri(ctx.contentResolver, recs[0].name, uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        send.clipData = clip
        val title = if (uris.size == 1) "Share recording" else "Share ${uris.size} recordings"
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Opens Music/CallRecordings[/number] in the system Files app. */
    fun openFolder(ctx: Context, sub: String?): Boolean {
        val docId = "primary:" + Recorder.basePath().trimEnd('/') + (sub?.let { "/$it" } ?: "")
        val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR),
            Intent("android.provider.action.BROWSE").setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR),
        )
        for (i in attempts) {
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }
}
