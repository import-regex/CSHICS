package com.example.cshics

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.random.Random
import java.io.FileInputStream

class MyWebServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {

    private var recordingDir: File? = null
    companion object {

        private const val TAG = "CSHICS_MyWebServer"

        fun getPotentialStoragePaths(context: Context): List<String> {
            //return a list of storage paths. from most reliably working to most
            val potentialSdCardBases = listOf(
                "/storage/sdcard1",      // Common AOSP
                "/mnt/extSdCard",         // Common Samsung
                "/storage/extSdCard",     // Other AOSP
                "/mnt/sdcard2",           // Older devices
                "/storage/external_SD",   // Other AOSP
                "/mnt/external_sd",       // Older devices
                "/storage/removable/sdcard1" // Some Sony devices
            )

            val potentialUsbBases = listOf(
                "/storage/usbdisk",
                "/storage/usbotg",
                "/storage/usb-storage",
                "/mnt/usb_storage",
                "/mnt/usb",
                "/storage/UsbDriveA",       // Samsung
                "/storage/UsbDriveB",       // Samsung
                "/mnt/media_rw/usbdisk",    // Newer but worth checking
                "/removable/usbdisk1",      // Some devices
                "/storage/external_storage/sdcard2", // Some devices map USB as a second SD
                "/storage/external_storage/usb",
                "/mnt/sdcard/usbStorage",
                "/mnt/sdcard/external_sd"   // Sometimes USB is mapped here
            )

            val writePaths = mutableListOf<String>()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                //if api>17 append internal and external path.
                for (dir in context.getExternalFilesDirs(null)) {
                    // The 'file' can be null if a storage path is unavailable.
                    if (dir != null && dir.exists() && dir.canWrite()) {
                        writePaths.add(dir.absolutePath)
                    }
                }
            } else {
                //else append internal and rely on string matching later on.
                val primaryExternalDir = context.getExternalFilesDir(null)
                if (primaryExternalDir != null) {
                    writePaths.add(primaryExternalDir.absolutePath)
                }
            }
            //append matching SD path string(s) and finish with USB path string(s)
            for (base in potentialSdCardBases + potentialUsbBases) {
                val sdCardBase = File(base)
                if (sdCardBase.exists() && sdCardBase.isDirectory && sdCardBase.canWrite()) {
                    val sdPath = File(sdCardBase, "Android/data/${context.packageName}/files")
                    writePaths.add(sdPath.absolutePath)
                }
            }
            // The list starts off with internal storage, then any discovered SD paths and then USB paths.
            return writePaths
        }
    }

    fun setRecordingPathAndStart(path: String): Boolean {
        val dir = File(path)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "FATAL: Could not create or access user-provided directory: $path")
            return false
        }
        this.recordingDir = dir
        Log.i(TAG, "Successfully set recording directory to: ${dir.absolutePath}")
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "MyWebServer started and listening on port $listeningPort")
        } catch (e: IOException) {
            Log.e(TAG, "FATAL: MyWebServer failed to start.", e)
            return false
        }
        return true
    }

    override fun serve(session: IHTTPSession): Response {

        val currentRecordingDir = recordingDir
        if (currentRecordingDir == null) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server Error: Storage not configured.")
        }

        return when (session.method) {

            Method.POST -> when {
                session.uri == "/upload" -> handleFileUpload(session, currentRecordingDir, session.headers["x-filename"])
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Error 404: Not Found")
            }

            Method.GET -> when {
                session.uri.startsWith("/download/") -> serveVideoFile(session.uri, currentRecordingDir)
                session.uri == "/" -> serveAsset("camera11.html")
                session.uri == "/watch" -> serveAsset("watch6.html")
                session.uri == "/files.json" -> serveVideoListPage(currentRecordingDir)
                session.uri.startsWith("/custom/") -> serveCustomFile(session.uri,currentRecordingDir)
                session.uri == "/info" -> serveAsset("info.html")
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Error 404: Not Found")
            }
            //Sometimes the first request a browser sends is OPTIONS.
            Method.OPTIONS -> {
                val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, null, 0)
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-Filename") //Allow the custom X-Filename header
                response
            }
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Error: Method Not Allowed")
        }
    }

    private fun handleFileUpload(session: IHTTPSession, storageDir: File, originalFilename: String?): Response {
        var response: Response
        var outputFile = File(storageDir, "${System.currentTimeMillis()}_${originalFilename}")

        while (outputFile.exists()) {
            outputFile = File(storageDir, "${System.currentTimeMillis()+Random.nextInt(100)}_${originalFilename}")
        }

        try {
            val contentLength = session.headers["content-length"]?.toLongOrNull()
            if (contentLength == null || contentLength <= 0) {
                Log.e(TAG, "Upload failed: Content-Length header is missing or invalid.")
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Content-Length header required.")
            }
            //Write the file to a ram buffer and dump to storage. Necessary for detecting the file end and immediately responding with ok.
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8192) // A standard 8KB buffer
                var bytesRemaining = contentLength
                while (bytesRemaining > 0) {
                    // Read a chunk of data, but no more than what's remaining
                    val bytesRead = session.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), bytesRemaining).toInt())
                    if (bytesRead < 0) {
                        // The stream ended prematurely
                        break
                    }
                    outputStream.write(buffer, 0, bytesRead)
                    bytesRemaining -= bytesRead
                }
            }
            Log.i(TAG, "Successfully saved file: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "File uploaded successfully.")
        } catch (e: SocketTimeoutException) {
            Log.i(TAG, "Socket timed out - saved partial file: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            response = newFixedLengthResponse(Response.Status.REQUEST_TIMEOUT, MIME_PLAINTEXT, "Request timed out.")
        } catch (e: Exception) {
            // For any OTHER error (disk full, etc.), log it and clean up the failed file.
            Log.e(TAG, "Unexpected error during file upload'", e)
            response = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error during upload.")
        }
        //var fos: FileOutputStream? = null  //the most basic copy; doesn't track the file end, thus has to let the socket time out.
        //fos = FileOutputStream(outputFile)
        //session.inputStream.copyTo(fos)
        //fos?.close()
        manageStorageCapacity(storageDir)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun serveAsset(assetName: String): Response {
        return try {
            val inputStream = context.assets.open(assetName)
            newChunkedResponse(Response.Status.OK, "text/html", inputStream)
        } catch (e: IOException) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Error 404: Asset Not Found")
        }
    }

    private fun serveVideoListPage(storageDir: File): Response {
        try {
            // Get all files, filter out subdirectories, and sort by date (newest first).
            val files: List<File> = storageDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            // Create a list of just the file sizes (as numbers).
            val fileSizes = files.map { it.length() }

            // The fileSizes list is automatically converted to a comma-separated string by joinToString.
            val combinedListString = files.joinToString(separator = ",") { "\"${it.name}\"" } + "," + fileSizes.joinToString(separator = ",")

            // Serve the content with the correct JSON MIME type.
            return newFixedLengthResponse(Response.Status.OK, "application/json", "[$combinedListString]")

        } catch (e: Exception) {
            Log.e(TAG, "Error serving file list as JSON", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error generating file list.")
        }
    }

    private fun serveVideoFile(uri: String, storageDir: File): Response {
        val filename = uri.substringAfterLast("/")
        val videoFile = File(storageDir, filename)

        try {
            return newChunkedResponse(Response.Status.OK, "video/webm", FileInputStream(videoFile))
        } catch (e: IOException) {
            Log.e(TAG, "Error serving video file: $filename", e)
            if (!videoFile.exists() || !videoFile.isFile) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Error: File not found.")
            }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: Could not read $filename.")
        }
    }

    private fun serveCustomFile(uri: String, storageDir: File): Response {

        val filename = uri.substringAfterLast("/")
        val customDir = File(storageDir, "custom")
        val customFile = File(customDir, filename)

        try {
            // Use a generic MIME type. The browser is usually smart enough to handle it.
            val mimeType = "application/octet-stream"
            return newChunkedResponse(Response.Status.OK, mimeType, FileInputStream(customFile))
        } catch (e: IOException) {
            Log.e(TAG, "Error serving custom file: $filename", e)
            if (!customDir.exists() || !customDir.isDirectory || !customFile.isFile) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Error: File not found in the 'custom' directory.")
            }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: Could not read the requested file.")
        }
    }

    private fun manageStorageCapacity(storageDir: File) {
        try {
            val totalSpace = storageDir.totalSpace
            val usableSpace = storageDir.usableSpace
            if (usableSpace * 5 > totalSpace || totalSpace == 0L ) return //arbitrary trigger line of 20% || can't manage storage of size 0
            Log.d(TAG, "Storage Stats - Total: ${totalSpace / 1024 / 1024}MB, Usable: ${usableSpace / 1024 / 1024}MB")
            val bytesToDelete = totalSpace / 5 //arbitrary wipe size of 20%
            deleteOldestFiles(storageDir, bytesToDelete)
        } catch (e: Exception) {
            Log.e(TAG, "Error during storage capacity management.", e)
        }
    }

    private fun deleteOldestFiles(storageDir: File, targetBytesToDelete: Long) {
        var bytesDeleted: Long = 0
        // Get all files, filter out any subdirectories, and sort them by modification date (oldest first).
        val files = storageDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?: return // If there are no files, do nothing.

        Log.i(TAG, "Attempting to delete ~${targetBytesToDelete / 1024 / 1024}MB of old files...")

        for (file in files) {
            if (bytesDeleted > targetBytesToDelete) {
                break // Stop deleting once we've hit our target
            }
            val fileSize = file.length()
            if (file.delete()) {
                bytesDeleted += fileSize
                Log.i(TAG, "Deleted old file: ${file.name}")
            } else {
                Log.w(TAG, "Failed to delete old file: ${file.name}")
            }
        }
        Log.i(TAG, "Cleanup finished. Freed ${bytesDeleted / 1024 / 1024}MB of space.")
    }
}