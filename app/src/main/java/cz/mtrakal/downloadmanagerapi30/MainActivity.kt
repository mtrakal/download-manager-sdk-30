package cz.mtrakal.downloadmanagerapi30

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vDownload.setOnClickListener { checkStoragePermission { downloadAgreement(true) } }
        vDownloadOkHttp.setOnClickListener { checkStoragePermission { okHttpDownload() } }
    }

    /**
     * Open downloaded PDF
     */
    fun openPdf(uri: String) {
        val providerUri = FileProvider.getUriForFile(this, "cz.mtrakal.downloadmanagerapi30.provider", File(uri.replace("file://", "content://")))
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(providerUri, "application/pdf")

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    /**
     * Log some info after download file and open it
     */
    private fun afterDownload(uri: String) {
        val file = File(uri)
        updateInfo("Local uri: $uri")
        updateInfo("File exists: ${file.exists()}")
        updateInfo("File can read: ${file.canRead()}")
        updateInfo("File can write: ${file.canWrite()}")
        updateInfo("Opening PDF: $uri")
        openPdf(uri)
    }

    private fun updateInfo(text: String) {
        vText.text = vText.text.toString() + "\r\n$text"
    }

    // Permission
    private var permissionAction: () -> Unit = {}
    protected fun checkStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionAction = action
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE
                )
                return
            }
        }
        action.invoke()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_WRITE_STORAGE -> {
                val failed = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                if (!failed) permissionAction.invoke()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    // End permission

    companion object {
        private const val REQUEST_WRITE_STORAGE = 123
    }

    // Download manager shits
    // FIXME
    private val downloadListener: DownloadHelper.OnDownloadFinishedListener = object : DownloadHelper.OnDownloadFinishedListener {
        override fun onDownloadFinished(downloadId: Long, success: Boolean) {
            updateInfo("Download finished with id: $downloadId")
            DownloadHelper.removeDownloadListener(this@MainActivity, this)

            DownloadHelper.filenameFromDownloadId(this@MainActivity, downloadId)?.let { uri ->
                afterDownload(uri)
            }
        }
    }

    private fun downloadAgreement(openAfterDownload: Boolean = false) {
        if (openAfterDownload) {
            DownloadHelper.addDownloadListener(this, downloadListener)
        }
        updateInfo("Download start")
        DownloadHelper.downloadFromUrl(this, "http://issues.mtrakal.cz/google.pdf", "my_custom_filename.pdf", "Google PDF", "issue with open PDF")
    }
    // End Download manager shits

    // OkHttp download

    private fun okHttpDownload() {
        val client = OkHttpClient()
        val request = Request.Builder().url("http://issues.mtrakal.cz/google.pdf").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                TODO("Not yet implemented")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "my_custom_okhttp_filename.pdf")
                response.body?.let {
                    file.writeBytes(it.bytes())
                }
                runOnUiThread { afterDownload(file.absolutePath.replace("file:/", "")) }
            }
        })
    }

    // End OkHttp download
}