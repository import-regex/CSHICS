package com.example.cshics

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity //limits minsdk to 14
import android.os.PowerManager
import android.view.View
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections


class MainActivity : AppCompatActivity() {

    private var webServer: MyWebServer? = null
    private val serverPort = 8080
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    // --- UI Elements ---
    private lateinit var statusTextView: TextView
    private lateinit var ipAddressTextView: TextView
    private lateinit var infoTextView: TextView
    private lateinit var pathEditText: EditText
    private lateinit var applyPathButton: Button
    private lateinit var wakeLockButton: Button

    companion object {
        private const val TAG = "CSHICS_MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Initialize all UI elements from the layout ---
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        infoTextView = findViewById(R.id.infoTextView)
        pathEditText = findViewById(R.id.pathEditText)
        applyPathButton = findViewById(R.id.applyPathButton)
        wakeLockButton=findViewById(R.id.wakeLockButton)

        infoTextView.visibility = View.VISIBLE

        // Get potential paths from the MyWebServer class.
        val potentialPaths = MyWebServer.getPotentialStoragePaths(this)
        if (potentialPaths.isNotEmpty()) {
            // Pre-fill the EditText with the last suggestion (most likely the SD card).
            pathEditText.setText(potentialPaths.last())
        }

        // Create the server instance, but DO NOT start it yet.
        webServer = MyWebServer(applicationContext, serverPort)

        // Set up the button's click listener to start the server.
        applyPathButton.setOnClickListener {
            val userPath = pathEditText.text.toString()
            if (userPath.isNotBlank()) {
                // Attempt to set the path and start the server using the new method.
                val success = webServer?.setRecordingPathAndStart(userPath)

                if (success == true) {
                    // Server started successfully! Update the UI.
                    val ipAddress = getDeviceIpAddress()
                    if (ipAddress != null) {
                        ipAddressTextView.text = "Remote devices: $ipAddress:$serverPort\nThis device: localhost:8080"
                        infoTextView.text="Recordings: $ipAddress:$serverPort/watch\nDetails:$ipAddress:$serverPort/info\nScreen locking is free."
                        if (screenWakeLock?.isHeld != true) {
                            acquireCpuWakeLock()
                        }

                    } else {
                        ipAddressTextView.text = "Wi-Fi not connected or IP not found."
                        Log.w(TAG, "Could not get device IP address.")
                    }

                    // Disable the button and field to prevent changes while running.
                    applyPathButton.isEnabled = false
                    pathEditText.isEnabled = false
                } else {
                    // Server failed to start with the given path.
                    statusTextView.text = "Error: Failed to start server."
                    ipAddressTextView.text = "Check path and permissions."
                    Log.e(TAG, "Failed to start server with path: $userPath")
                }
            }
        }

        wakeLockButton.setOnClickListener{
            acquireScreenWakeLock() //Powerfull anti-screen sleep lock.
            wakeLockButton.isEnabled = false
            wakeLockButton.text="screen is less sleepy"
        }
    }

    private fun acquireScreenWakeLock() {
        // Release the other lock first, if it's held.
        if (cpuWakeLock?.isHeld == true) {
            cpuWakeLock?.release()
            Log.i(TAG, "CPU Wake Lock released.")
        }
        // Acquire this lock if it's not already held.
        if (screenWakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            //SCREEN_DIM_WAKE_LOCK is a powerful lock that prevents locking even outside the app.
            screenWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "CSHICS::ScreenLockTag")
            screenWakeLock?.acquire()
            Log.i(TAG, "SCREEN Wake Lock acquired.")
        }
    }

    private fun acquireCpuWakeLock() {
        // Release the other lock first, if it's held.
        if (screenWakeLock?.isHeld == true) {
            screenWakeLock?.release()
            Log.i(TAG, "SCREEN Wake Lock released.")
        }
        // Acquire this lock if it's not already held.
        if (cpuWakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CSHICS::CpuLockTag")
            cpuWakeLock?.acquire()
            Log.i(TAG, "CPU (Partial) Wake Lock acquired.")
        }
    }

    private fun getDeviceIpAddress(): String? {
        try {
            // Get all network interfaces.
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                // 1. Filter out interfaces that are down, loopback, or virtual.
                if (!intf.isUp || intf.isLoopback || intf.isVirtual) continue
                // Get all addresses for this interface.
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    // 2. Find the first address that is an instance of Inet4Address.
                    if (addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP address", ex)
        }
        return null // No suitable IP address found.
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer?.stop()
        Log.i(TAG, "CSH WebServer stopped.")
        if (screenWakeLock?.isHeld == true) {
            screenWakeLock?.release()
            Log.w(TAG, "Nuking screen wake lock on destroy.")
        }
        if (cpuWakeLock?.isHeld == true) {
            cpuWakeLock?.release()
            Log.w(TAG, "Nuking CPU wake lock on destroy.")
        }

    }
}