package com.axinom.drm.sample.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.axinom.drm.sample.R;
import com.axinom.drm.sample.license.LicenseManagerErrorCode;
import com.axinom.drm.sample.license.OfflineLicenseManager;
import com.axinom.drm.sample.license.interfaces.IOfflineLicenseManagerListener;
import com.axinom.drm.sample.license.internal.model.DrmMessage;
import com.axinom.drm.sample.license.internal.utils.DrmUtils;
import com.axinom.drm.sample.offline.AxDownloadService;
import com.axinom.drm.sample.offline.AxDownloadTracker;
import com.axinom.drm.sample.offline.AxOfflineManager;
import com.axinom.drm.sample.util.Utility;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface.
 */
class DemoPlayer implements TextOutput, IOfflineLicenseManagerListener, Player.Listener {

  // Parameters used to initialize the player
  public static class Params {
    MediaItem mediaItem;
    long startPosition = 0;
    boolean startOnPrepared = true;
    boolean shouldPlayOffline = false;
  }

  public static class UnsupportedFormatException extends Exception { }

  /**
   * Listener interface for player events.
   */
  public interface Listener {
    void onPlayerLog(String message, String tag);
    void onPlayerError(Exception e);
  }

  private static final String TAG = DemoPlayer.class.getSimpleName();

  private ExoPlayer mPlayer;
  private boolean mPlayerIsCreated = false;
  private Context mContext;
  // Manager class for offline licenses
  private OfflineLicenseManager mOfflineLicenseManager;
  // A factory for DataSource instances
  private DataSource.Factory mMediaDataSourceFactory;
  // Defines downloaded content
  private DownloadRequest mDownloadRequest;
  private Params mParams;
  // A DrmSessionManager that supports playbacks using ExoMediaDrm
  private DefaultDrmSessionManager mDrmSessionManager;
  // Media format
  private int mFormat;

  private final CopyOnWriteArrayList<Listener> mListeners;

  public DemoPlayer(Context context) {
    mContext = context;
    mListeners = new CopyOnWriteArrayList<>();
  }

  private boolean isCreated(){
    return mPlayerIsCreated;
  }

  // A method for creating player using predefined parameters
  private void playerCreate(Params params){
    dispatchPlayerLog("Creating the player");
    mMediaDataSourceFactory = buildDataSourceFactory();
    MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(params.mediaItem);
    try {
      mPlayerIsCreated = false;
      // Only DASH and HLS formats are allowed for playback in this application
      mFormat = Util.inferContentType(Utility.getPlaybackProperties(params.mediaItem).uri);
      if (mFormat != C.TYPE_DASH && mFormat != C.TYPE_HLS) {
        throw new UnsupportedFormatException();
      }
      if (drmConfiguration != null) {
        mDrmSessionManager = buildDrmSessionManager(String.valueOf(drmConfiguration.licenseUri),
                drmConfiguration.licenseRequestHeaders.get("X-AxDRM-Message"));
        // OfflineLicenseManager should be initialized and license keys received only if offline playback is required
        if (mParams.shouldPlayOffline) {
          mOfflineLicenseManager = new OfflineLicenseManager(mContext);
          mOfflineLicenseManager.setEventListener(this);
          mOfflineLicenseManager.getLicenseKeys(
                  String.valueOf(Utility.getPlaybackProperties(params.mediaItem).uri));
        }
      }
    } catch (UnsupportedFormatException | NullPointerException e){
      dispatchPlayerError(e);
      return;
    }
    // Defining DefaultTrackSelector for the player
    DefaultTrackSelector trackSelector = new DefaultTrackSelector(mContext);
    trackSelector.setParameters(new DefaultTrackSelector.ParametersBuilder(mContext).setPreferredTextLanguage("eng"));

    // Defining DefaultRenderersFactory for the player
    DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(mContext);
    try {
      mPlayer = new ExoPlayer.Builder(mContext, renderersFactory)
              .setTrackSelector(trackSelector)
              .build();
      mPlayer.addListener(this);
      mPlayerIsCreated = true;
      // Preparing the player can start here in case content should be played online (offline license
      // has to be acquired first before starting the preparation process) or if the content is not
      // protected.
      if (!mParams.shouldPlayOffline || drmConfiguration == null) {
        startPlayerPrepare();
      }
    } catch (NullPointerException e){
      dispatchPlayerError(e);
    }
  }

  // Returns a new DataSource factory
  private DataSource.Factory buildDataSourceFactory() {
    dispatchPlayerLog("Building DataSourceFactory");
    // If offline playback is allowed and media has already been downloaded,
    // use CacheDataSource from AxOfflineManager
    if (mParams.shouldPlayOffline) {
      AxDownloadTracker axDownloadTracker = AxOfflineManager.getInstance().getDownloadTracker();
      if (axDownloadTracker != null) {
        mDownloadRequest = axDownloadTracker.getDownloadRequest(
                Utility.getPlaybackProperties(mParams.mediaItem).uri);
        if (mDownloadRequest != null) {
          return AxOfflineManager.getInstance().buildDataSourceFactory(mContext);
        }
      }
      dispatchPlayerErrorMessage(mContext.getString(R.string.error_offline_playback));
      return null;
    } else {
      return new DefaultDataSource.Factory(mContext, buildHttpDataSourceFactory());
    }
  }

  private void dispatchPlayerLog(String message){
    for (Listener listener : mListeners) {
      listener.onPlayerLog(message, TAG);
    }
  }

  private void dispatchPlayerError(Exception e){
    for (Listener listener : mListeners) {
      listener.onPlayerError(e);
    }
  }

  private void dispatchPlayerErrorMessage(String message){
    dispatchPlayerError(new Exception(message));
  }

  public void addListener(Listener listener) {
    mListeners.add(listener);
  }

  // A method for building MediaSource for playback
  private MediaSource buildMediaSource(Context context, Uri videoUri){
    dispatchPlayerLog("Building MediaSource with videoUri = [" + videoUri + "]");

    // If offline playback is allowed and media has already been downloaded,
    // use the existing download request to create the media source
    if (mParams.shouldPlayOffline) {
      if (mDownloadRequest != null) {
        dispatchPlayerLog(mContext.getString(R.string.player_offline_playback));
        return DownloadHelper.createMediaSource(mDownloadRequest, mMediaDataSourceFactory, mDrmSessionManager);
      }
      dispatchPlayerErrorMessage(mContext.getString(R.string.error_offline_playback));
      return null;
    } else {
      dispatchPlayerLog(mContext.getString(R.string.player_online_playback));
      DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);
      if (mFormat == C.TYPE_DASH) {
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                .setDrmSessionManagerProvider(unusedMediaItem -> mDrmSessionManager)
                .createMediaSource(mParams.mediaItem);
      } else {
        return new HlsMediaSource.Factory(
                new DefaultHlsDataSourceFactory(dataSourceFactory))
                .setDrmSessionManagerProvider(unusedMediaItem -> mDrmSessionManager)
                .createMediaSource(mParams.mediaItem);
      }
    }
  }

  // A method for building HttpDataSource.Factory
  private HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSource.Factory();
  }

  // A method for building DrmSessionManager
  private DefaultDrmSessionManager buildDrmSessionManager(String licenseUrl, String drmToken) {
    dispatchPlayerLog("Building DrmSessionManager with licenseUrl = [" +
            licenseUrl + "] and drmToken = [" + drmToken + "]");
    HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
            buildHttpDataSourceFactory());
    // Here the license token is attached to license request
    if (drmToken != null) {
        drmCallback.setKeyRequestProperty("X-AxDRM-Message", drmToken);
    }

    return new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(drmCallback);

  }

  // A method for preparing the player for creation
  public void prepare(Params params, StyledPlayerView playerView) {
    dispatchPlayerLog("Preparing the player for creation");
    mParams = params;
    playerRelease();
    playerCreate(params);
    if (isCreated()) {
      mPlayer.setPlayWhenReady(params.startOnPrepared);
      playerView.setPlayer(mPlayer);
    }
  }

  // A method for preparing the player
  private void startPlayerPrepare() {
    dispatchPlayerLog("Starting the preparation of the player for video playback");
    MediaSource mediaSource = buildMediaSource(
            mContext, Utility.getPlaybackProperties(mParams.mediaItem).uri);
    if (mediaSource != null) {
      mPlayer.setMediaSource(mediaSource);
      mPlayer.prepare();
      if (mParams.startPosition != 0){
        mPlayer.seekTo(mParams.startPosition);
      }
    }
  }

  // General method for releasing the player
  private void playerRelease(){
    dispatchPlayerLog("Releasing the player");
    if (mPlayer != null) {
      mPlayer.release();
      mPlayer = null;
    }
  }

  // More specific method for releasing the player that also clears the context.
  public void release() {
    playerRelease();
    mContext = null;
  }

  public long getCurrentPosition() {
    if (mPlayerIsCreated) return mPlayer.getCurrentPosition();
    return 0;
  }

  private void dumpSelectedTracks() {
    TrackSelectionArray trackSelectionArray = mPlayer.getCurrentTrackSelections();
    for (int i = 0; i < trackSelectionArray.length; i++){
      TrackSelection trackSelection = trackSelectionArray.get(i);
      if (trackSelection != null) {
        Log.d(TAG, "trackSelection, index = " + i);
      }
    }
  }

  private void dumpAvailableTracks(){
    TrackGroupArray trackGroups = mPlayer.getCurrentTrackGroups();
    for (int i = 0; i < trackGroups.length; i++){
      TrackGroup trackGroup = trackGroups.get(i);
      for (int trackIndex  = 0; trackIndex < trackGroup.length; trackIndex++){
        Format format =  trackGroup.getFormat(trackIndex);
        Log.d(TAG, "group = " + i + ", track = " + trackIndex + ", format = " + format);
      }
    }
  }

  @Override
  public void onPlaybackStateChanged(int state) {
    String stateString;
    switch (state){
      case Player.STATE_IDLE:
        stateString = "idle";
        break;
      case Player.STATE_BUFFERING:
        stateString = "buffering";
        break;
      case Player.STATE_READY:
        stateString = "ready";
        dumpAvailableTracks();
        dumpSelectedTracks();
        break;
      case Player.STATE_ENDED:
        stateString = "ended";
        break;
      default:
        stateString = "unknown state";
    }
    dispatchPlayerLog("Player state changed to = [" + state + " (" + stateString + ")]");
  }

  @Override
  public void onPlayerError(@NonNull PlaybackException exception) {
    dispatchPlayerError(exception);
  }

  // TextOutput implementation
  @Override
  public void onCues(@NonNull List<Cue> cues) {
    dispatchPlayerLog("onCues() called with: cues = [" + cues + "]");
  }

  @Override
  public void onLicenseDownloaded(String manifestUrl) {
    // Not used player implementation
  }

  @Override
  public void onLicenseDownloadedWithResult(String manifestUrl, byte[] keyIds) {
    onOfflineLicenseAcquired(manifestUrl, keyIds);
  }

  @Override
  public void onLicenseDownloadFailed(int code, String description, String manifestUrl) {
    dispatchPlayerLog("onLicenseDownloadFailed for manifest: " + manifestUrl + " code: " + code);
  }

  @Override
  public void onLicenseCheck(boolean isValid, String manifestUrl) {
    // Not used in player implementation
  }

  @Override
  public void onLicenseCheckFailed(int code, String description, String manifestUrl) {
    // Not used in player implementation
  }

  @Override
  public void onLicenseReleased(String manifestUrl) {
    // Not used in player implementation
  }

  @Override
  public void onLicenseReleaseFailed(int code, String description, String manifestUrl) {
    // Not used in player implementation
  }

  @Override
  public void onLicenseKeysRestored(String manifestUrl, byte[] keyIds) {
    dispatchPlayerLog("onLicenseKeysRestored() called with: manifestUrl = [" + manifestUrl + "]");
    onOfflineLicenseAcquired(manifestUrl, keyIds);
  }

  @Override
  public void onLicenseRestoreFailed(int code, String description, String manifestUrl) {
    dispatchPlayerLog("License restore failed for manifest: " + manifestUrl + " code: " + code);
    onNoOfflineLicenseFound(manifestUrl, code);
  }

  @Override
  public void onAllLicensesReleased() {
    // Not used in player implementation
  }

  @Override
  public void onAllLicensesReleaseFailed(int i, String s) {
    // Not used in player implementation
  }

  // Called when offline license is acquired. Player can now be properly prepared
  @SuppressWarnings("unused")
  public void onOfflineLicenseAcquired(String manifestUrl, byte[] keyIds) {
    dispatchPlayerLog("Offline license acquired.");
    // Offline license acquired. Starting in MODE_QUERY
    mDrmSessionManager.setMode(DefaultDrmSessionManager.MODE_QUERY, keyIds);
    startPlayerPrepare();
  }

  // Called when no offline license is found.
  public void onNoOfflineLicenseFound(String manifestUrl, int licenseErrorCode) {
    dispatchPlayerLog("No offline license found.");
    DrmMessage drmMessage = null;
    MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(mParams.mediaItem);
    if (drmConfiguration != null && !TextUtils.isEmpty(
            drmConfiguration.licenseRequestHeaders.get("X-AxDRM-Message"))) {
      drmMessage = DrmUtils.parseDrmString(
              drmConfiguration.licenseRequestHeaders.get("X-AxDRM-Message"));
      if (drmMessage == null) {
        dispatchPlayerErrorMessage(mContext.getString(R.string.error_drm_token_parse));
        return;
      }
    }
    // Check that if license persistent flag is true, then we can try to download license
    if (drmMessage != null && drmMessage.persistent) {
      dispatchPlayerLog("Drm message has persistent flag");
      if (!hasConnection()) {
        if (licenseErrorCode == LicenseManagerErrorCode.ERROR_308.getCode()) {
          dispatchPlayerErrorMessage(mContext.getString(R.string.error_drm_keys_expired));
        } else {
          dispatchPlayerErrorMessage(mContext.getString(R.string.error_no_connection_for_license_download));
        }
        return;
      }

      if (TextUtils.isEmpty((CharSequence) drmConfiguration.licenseUri)) {
        dispatchPlayerErrorMessage(mContext.getString(R.string.error_license_server));
        return;
      }
      dispatchPlayerLog("Trying to download and save license.");
      mOfflineLicenseManager.downloadLicenseWithResult(String.valueOf(drmConfiguration.licenseUri),
              manifestUrl, drmConfiguration.licenseRequestHeaders.get("X-AxDRM-Message"), true);
    } else {
      dispatchPlayerErrorMessage(mContext.getString(R.string.error_drm_message_not_persistent));
    }
  }

  /**
   * Returns true if there is InternetConnection
   *
   * @return boolean
   */
  private boolean hasConnection() {
    final ConnectivityManager cm = (ConnectivityManager) mContext.getApplicationContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE);
    boolean hasConnection = (cm != null && cm.getActiveNetworkInfo() != null);
    dispatchPlayerLog("Device has network connection: " + hasConnection);
    return hasConnection;
  }

  // Method for deleting currently playing video.
  public void onDeletePressed() {
    dispatchPlayerLog("Deleting the currently playing content.");
    AxDownloadTracker axDownloadTracker = AxOfflineManager.getInstance().getDownloadTracker();
    if (axDownloadTracker != null && axDownloadTracker.isDownloaded(
            String.valueOf(Utility.getPlaybackProperties(mParams.mediaItem).uri))) {
      // License is removed for the selected video
      if (mOfflineLicenseManager != null) {
        mOfflineLicenseManager = new OfflineLicenseManager(mContext);
        mOfflineLicenseManager.setEventListener(this);
        mOfflineLicenseManager.releaseLicense(
                String.valueOf(Utility.getPlaybackProperties(mParams.mediaItem).uri));
      }
      // Removes a download
      DownloadService.sendRemoveDownload(mContext, AxDownloadService.class,
              axDownloadTracker.getDownloadRequest(
                      Utility.getPlaybackProperties(mParams.mediaItem).uri).id, false);
    }
  }
}
