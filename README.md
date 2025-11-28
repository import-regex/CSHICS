# CSHICS - Android Camera Surveillance Server

This is a simple, lightweight Android application that turns an old smartphone or a tablet (running Android 4.1 / API 16 "JellyBean" +) into a web-based camera surveillance recording server.

The entire system is self-contained. The app hosts a web server that you can access from any device on the same Wi-Fi/hotspot/Bluetooth tether network to store and review recordings.

## Key Features

-   **Web-Based Recording:** Manage recording parameters directly within the web interface.
-   **File Management:**  Filter by time and source, view and download recordings from a simple web page. Old recordings are auto-deleted as storage fills up.
-   **Portable Mode:** Works completely offline and can even run on a phone's own Wi-Fi hotspot or Bluetooth tether, creating a portable, private surveillance network.
-   **Flexible Storage:** Automatically detects and prioritizes saving recordings to a USB drive, SD card, or internal storage.
-   **No Cloud, No Accounts:** Completely private. Nothing is ever uploaded to the internet.
-   **Resource effiency** The whole system is optimized for peak storage, network and CPU load efficiency.
-   **Customization** Users have in depth control to set, change and optimize detailed running parameters of the whole system.

## How to Use

1.  **Install the App:**
    -   Download an .apk from the [Releases page](https://github.com/import-regex/CSHICS/releases) (or build it yourself using Android Studio).
    -   Install the APK on your target Android device.

2.  **Prepare Storage:**
    -   Preferably use an empty SD card or even connect a USB OTG drive (flash drive, SSD, HDD). The app will automatically try to use it. Or set the path manualy if path discover fails/you have different priorities.
    -   **Important:** The app works best with a completely empty storage device. It assumes the whole storage capacity as dedicated for recordings.

3.  **Start the Server:**
    -   Open the app. It will suggest a storage path.
    -   Press **"Apply and Start a Server"**.

4.  **Connect and View:**
    -   The app will display an IP address on the screen (e.g., `http://192.168.1.10:8080`).
    -   On another device (a laptop or another phone) connected to the **same Wi-Fi network**, open a web browser and go to that address. 
    -   You will see the main camera page. To view recordings, navigate to `http://<IP_ADDRESS>:8080/watch`.
    -   You are 95% likely to encounter permissions issues. Install and run the app locally on every camera device to initialize the page as localhost:8080 and update the upload URL to the correct one. On PC just open the page as a file:///
    -   Some Androids shut down the screen after a few hours of no user input while in the browser. Run CSHICS in the background with "no screen sleep" enabled, regardless if the server has been started or in use.
    -   Use the page /info to learn more.

## Technical Details

-   **Minimum API Level:** 16 (Android 4; Can be built for API 14 or even patched for older versions)
-   **Web Server:** Implemented using the lightweight `NanoHTTPD` library.
-   **Motion detection** All motion detection parameters can be set.
-   **Recording:** Uses the `MediaRecorder` and `getUserMedia` JavaScript APIs of a web browser. All major options can be set.
-   **Compatibility:** The web interface has been tested on Chrome and Firefox. Modern Chromium has the least bugs.
-   **Pages and use**
<br>/ - the main page is the camera mode page.
<br>/upload - POST the recordings (or any file) to that server/CSHICS instance.
<br>/watch - the watch page is where you filter, review and download recordings.
<br>/info - the info page is where you learn more about the project.
<br>/custom/abc.xyz - GET your custom abc.xyz file saved under the /custom/ directory in the recordings storage path of the server.
