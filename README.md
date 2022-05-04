# Axinom DRM Sample Player with offline playback support

This is a sample project of an Android video player application. Its purpose is to provide a starting point for developers who want to implement a player application that includes support for Axinom DRM and offline playback.

The application uses [ExoPlayer](https://github.com/google/ExoPlayer) version 2.17.1 to play MPEG-DASH and HLS streams protected using Axinom DRM. Additionally, it supports offline playback and implements functionality for downloading protected streams and persisting DRM licenses for later use.

The application itself can be used for demonstration and testing purposes of the mentioned features. Details about the integration of Axinom DRM can be found in the source code. It contains explanatory comments and can be used as a development guide in addition to this README.


# Important files

Here is a list of more important files in the project that have a key role in Axinom DRM integration and the offline playback support.

[OfflineLicenseManager.java](app/src/main/java/com/axinom/drm/sample/license/OfflineLicenseManager.java)
* A class that helps with downloading the licenses, checking their validity, and restoring the licenses for offline playback. Supporting classes for it can be found in the "license" package.


[SampleChooserActivity.java](app/src/main/java/com/axinom/drm/sample/activity/SampleChooserActivity.java)
* A class that loads sample videos from [samplelist.json](app/src/main/assets/samplelist.json).  
This json file can be modified to add your own sample videos for the application to use.  
More information about it in ["Adding sample videos"](#adding-sample-videos) chapter below.
* Also gives users the possibility to either play videos online or download the videos and play them offline.  
See ["Offline mode"](#offline-mode) chapter below for more details about offline playback support or ["How to use the application"](#how-to-use-the-application) chapter about using the application.


[DemoPlayer.java](app/src/main/java/com/axinom/drm/sample/player/DemoPlayer.java)
* A wrapper class around ExoPlayer which tries to play content offline if required.
* For Axinom DRM integration it is important to see the *buildDrmSessionManager()* method which attaches license token to license requests.


[AxDownloadService.java](app/src/main/java/com/axinom/drm/sample/offline/AxDownloadService.java)
* A class that extends Exoplayer's DownloadService class. Defines a service that enables the downloads to continue even when the app is in the background.


[AxDownloadTracker.java](app/src/main/java/com/axinom/drm/sample/offline/AxDownloadTracker.java)
* A class that manages the downloads: initializes the download requests, enables to select tracks for download, and listens for events where download status changed.


[AxOfflineManager.java](app/src/main/java/com/axinom/drm/sample/offline/AxOfflineManager.java)
* A class that manages the initialization of Exoplayer's DownloadManager and data source factory objects.


# Device compatibility

This project is compatible with devices running Android 4.4 or newer.

# How to run the application

1. Open Android Studio and connect an Android 4.4 (or newer) device to a computer.
2. Clone or download this repository and open the project in Android Studio.
3. Run the application by selecting *Run -> Run 'app'* from the Android Studio menu bar.

The application can also be downloaded from App Center. More information about this in ["Downloading the application"](#downloading-the-application) chapter below.

# How to use the application

* Play video from an online source
1. Select video from sample videos list (selected video title is indicated by gray background).
2. Press the "Play" button.

* Play video from an offline source
1. Observe the offline availability status of the selected video. It is a text placed between the video sample list and control buttons:

![](Images/offline_availability_status.png)

For the video to be available offline, it has to be both downloaded and have a valid license (indicated by the green text, any issues can be recognized by red text).

2. If the video is available offline, press the "Play offline" button to play it from an offline source.  
The "Play offline" button will appear only if the content is available offline.

3. If the video is not already downloaded, it has to be done by pressing the "Download" button.  
Download feature saves the required license also in addition to downloading the video.  
Download progress can be observed from the text below offline availability status. Progress is also visible on the notifications bar.  
After the download is successfully completed, the "Play offline" button appears and it can be pressed for offline playback.

* Delete the downloaded video
1. Press the "Delete" button. Which also removes the license for the downloaded video.

* Remove only the saved licenses (only available when content is downloaded)
1. For removing license only for the selected video press "Remove license".
2. For removing all saved licenses press "Remove all licenses".

* Request license (only available for a downloaded video that does not have a valid license)
1. For requesting a license for the selected video press "Request license".

## Adding sample videos

In order to add your own sample videos to the application, please add entries to [samplelist.json](app/src/main/assets/samplelist.json).

Keys to provide value for are the following:

**title:** Display name for the sample video.

**videoUrl:** DASH manifest URL.

**drmScheme:** DRM scheme for protected video.

**licenseServer:** Axinom DRM License Server URL.

**licenseToken:** License Token for License Request.

## Offline mode

It is possible to download both non-protected and DRM-protected videos and play them offline.

To play DRM videos in offline mode, the persistent license keys have to be saved on the device's internal storage.  
To preserve an offline license, `"allow_persistence": true` flag needs to be present inside the DRM token.

To download licenses, **OfflineLicenseManager** class is used.  
This class contains functionality that is responsible for obtaining licenses and saving licenses' access keys to device internal storage (path returned by the ).

In general, the responsibilities of the OfflineLicenseManager class are:
* Downloading a license
* Checking license validity
* Update expired licenses
* Restoring the license for offline playback
* Dispatch completion events that contain information about completion results (success or failure)

In order to be able to download media the following steps should be followed:

Before initializing the AxOfflineManager, the WRITE_EXTERNAL_STORAGE permission must be granted.

Initialize the AxOfflineManager:
<pre><code class="Java">
AxOfflineManager.getInstance().init(context);
</code></pre>
If no download folder path is specified then the default folder (titled "downloads" and located in external storage directory) is used.  
It is possible to set a custom folder by initializing the AxOfflineManager by providing the folder path:
<pre><code class="Java">
AxOfflineManager.getInstance().init(context, folder);
</code></pre>

Start the download service:
<pre><code class="Java">
DownloadService.start(this, AxDownloadService.class);
</code></pre>

Initialize the OfflineLicenseManager object and start license download:
<pre><code class="Java">
mOfflineLicenseManager = new OfflineLicenseManager(context);
mOfflineLicenseManager.downloadLicenseWithResult(licenseServerUrl, manifestUrl, drmMessage, true)
</code></pre>
License download path is context.getFilesDir().getAbsolutePath() where context is the context used to create new instance of the OfflineLicenseManager.

Retrieve DownloadTracker:
<pre><code class="Java">
mAxDownloadTracker = AxOfflineManager.getInstance().getDownloadTracker();
</code></pre>

Initialize DownloadHelper by providing the media (manifest) url and calling prepare():
<pre><code class="Java">
mDownloadHelper = mAxDownloadTracker.getDownloadHelper(uri,this);
mDownloadHelper.prepare(this);
</code></pre>

Implement DownloadHelper.Callback interface and the required onPrepared() and onPreparedError() methods to listen to the DownloadHelper events.  
After the onPrepared() event occurs, download process can be started by calling the download() method that takes two parameters:  
a description (for example the video title) and a 2-dimensional tracks (representations) array where tracks are defined in the following format: [periodIndex0, rendererIndex0, groupIndex0, trackIndex0, [], ...].  
The format is based on MappedTrackInfo object that contains the mapped track information for each renderer (i.e. video, audio, text, etc.) with the same structure.

If the tracks array is null or empty then all the tracks are downloaded for that media resource.

<pre><code class="Java">
@Override
public void onPrepared(DownloadHelper helper) {
    mAxDownloadTracker.download(title, tracks);
}

@Override
public void onPrepareError(DownloadHelper helper, IOException e) {
    ...
}
</code></pre>

## Downloading the application

This application can be downloaded from App Center by either navigating to the [website](https://install.appcenter.ms/orgs/ax/apps/axinom-drm-sample-player-1/distribution_groups/public) directly or by scanning this QR code with your device:  
![](Images/qr_code.png)

It is also important to keep in mind that the installation of apps from unknown sources has to be allowed on your device. If it is not allowed, then you will be prompted to do that during the installation process.