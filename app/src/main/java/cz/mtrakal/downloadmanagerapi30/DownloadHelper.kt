package cz.mtrakal.downloadmanagerapi30

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 *
 * @author mtrakal on 13.08.2020.
 */
object DownloadHelper : BroadcastReceiver() {
    private val listeners = HashSet<OnDownloadFinishedListener>()

    /**
     * Get filename from DownloadManager from id which was downloaded
     */
    fun filenameFromDownloadId(context: Context, downloadId: Long): String? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val downloadStatus: Int = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val downloadLocalUri: String = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)).replace("file://", "")
            if (downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                cursor.close()
                return downloadLocalUri
            }
        }
        cursor.close()
        return null
    }

    fun addDownloadListener(context: Context, listener: OnDownloadFinishedListener) {
        if (listeners.isEmpty()) {
            context.registerReceiver(this, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        listeners.add(listener)
    }

    fun removeDownloadListener(context: Context, listener: OnDownloadFinishedListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            try {
                context.unregisterReceiver(this)
            } catch (e: Exception) {
                // already unregistered
            }
        }
    }

    /**
     * Download file with DownloadManager, store it to public DOwnloads directory
     */
    fun downloadFromUrl(
        context: Context,
        url: String,
        fileName: String,
        title: String?,
        description: String?
    ) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(DownloadManager.Request(Uri.parse(url)).apply {
            setDescription(description.orEmpty())
            setTitle(title.orEmpty())
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                allowScanningByMediaScanner()
            }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        })
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == null || downloadId < 0) {
            return
        }
        notifyListeners(downloadId, isSuccessful(downloadId, context))
    }

    private fun notifyListeners(downloadId: Long, success: Boolean) {
        listeners.forEach { it.onDownloadFinished(downloadId, success) }
    }

    private fun isSuccessful(downloadId: Long, context: Context): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        val returnValue = (cursor.moveToFirst()
                && cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL)
        cursor.close()
        return returnValue
    }

    interface OnDownloadFinishedListener {
        fun onDownloadFinished(downloadId: Long, success: Boolean)
    }
}