package com.axinom.drm.sample.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.axinom.axlicense.LicenseManagerErrorCode;
import com.axinom.axlicense.OfflineLicenseManager;
import com.axinom.axlicense.interfaces.IOfflineLicenseManagerListener;
import com.axinom.axlicense.internal.model.DrmMessage;
import com.axinom.axlicense.internal.utils.DrmUtils;
import com.axinom.drm.sample.R;
import com.axinom.drm.sample.offline.AxDownloadTracker;
import com.axinom.drm.sample.offline.AxOfflineManager;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface.
 */
class DemoPlayer implements ExoPlayer.EventListener, VideoListener, TextOutput, IOfflineLicenseManagerListener {

  // Parameters used to initialize the player
  public static class Params {
    Uri contentUri;
    String licenseServer;
    String axDrmMessage;
    long startPosition = 0;
    boolean startOnPrepared = true;
    boolean shouldPlayOffline = false;
  }

  public static class UnsupportedFormatException extends Exception { }

  /**
   * Listener interface for player events.
   */
  public interface Listener {
    void onPlaybackSourceSet(int source);
    void onPlayerError(Exception e);
  }

  private static final String TAG = DemoPlayer.class.getSimpleName();
  private static final String PLAYER_APP_NAME = "ExoPlayerDemo";
  public static final int ONLINE_PLAYBACK = 1;
  public static final int OFFLINE_PLAYBACK = 2;

  private SimpleExoPlayer mPlayer;
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
  private DefaultDrmSessionManager<ExoMediaCrypto> mDrmSessionManager;
  // Estimates bandwidth by listening to data transfers
  private DefaultBandwidthMeter mBandwidthMeter;

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
    mBandwidthMeter = new DefaultBandwidthMeter.Builder(mContext).setResetOnNetworkTypeChange(false).build();
    mMediaDataSourceFactory = buildDataSourceFactory();
    try {
      mPlayerIsCreated = false;
      // Only DASH format is allowed for playback in this application
      if (Util.inferContentType(params.contentUri) != C.TYPE_DASH) {
        throw new UnsupportedFormatException();
      }
      mDrmSessionManager = buildDrmSessionManager(mContext, params.licenseServer, params.axDrmMessage);
      // OfflineLicenseManager should be initialized and license keys received only if offline playback is required
      if (mParams.shouldPlayOffline) {
        mOfflineLicenseManager = new OfflineLicenseManager(mContext);
        mOfflineLicenseManager.setEventListener(this);
        mOfflineLicenseManager.getLicenseKeys(params.contentUri.toString());
      }
    } catch (UnsupportedFormatException | NullPointerException e){
      dispatchPlayerError(e);
      return;
    }
    // Defining DefaultTrackSelector for the player
    TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
    DefaultTrackSelector trackSelector = new DefaultTrackSelector(mContext, trackSelectionFactory);
    trackSelector.setParameters(new DefaultTrackSelector.ParametersBuilder(mContext).setPreferredTextLanguage("eng"));

    // Defining DefaultRenderersFactory for the player
    DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(mContext);
    try {
      mPlayer = new SimpleExoPlayer.Builder(mContext, renderersFactory)
              .setTrackSelector(trackSelector)
              .build();
      mPlayer.addListener(this);
      mPlayerIsCreated = true;
      // Preparing the player can start here nn case content should be played online (offline license
      // has to be acquired first before starting the preparation process).
      if (!mParams.shouldPlayOffline) {
        startPlayerPrepare();
      }
    } catch (NullPointerException e){
      dispatchPlayerError(e);
    }
  }

  // Returns a new DataSource factory
  private DataSource.Factory buildDataSourceFactory() {
    // If offline playback is allowed and media has already been downloaded,
    // use CacheDataSource from AxOfflineManager
    if (mParams.shouldPlayOffline) {
      AxDownloadTracker mAxDownloadTracker = AxOfflineManager.getInstance().getDownloadTracker();
      if (mAxDownloadTracker != null) {
        mDownloadRequest = mAxDownloadTracker.getDownloadRequest(mParams.contentUri);
        if (mDownloadRequest != null) {
          return AxOfflineManager.getInstance().buildDataSourceFactory(mContext);
        }
      }
      dispatchPlayerErrorMessage(mContext.getString(R.string.error_offline_playback));
      return null;
    } else {
      return new DefaultDataSourceFactory(mContext, mBandwidthMeter,
              buildHttpDataSourceFactory(mContext, true));
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
    Log.d(TAG, "buildMediaSource() called with: context = [" + context + "], videoUri = [" + videoUri + "]");

    // If offline playback is allowed and media has already been downloaded,
    // use the existing download request to create the media source
    if (mParams.shouldPlayOffline) {
      if (mDownloadRequest != null) {
        for (Listener listener : mListeners) {
          listener.onPlaybackSourceSet(OFFLINE_PLAYBACK);
        }
        return DownloadHelper.createMediaSource(mDownloadRequest, mMediaDataSourceFactory, mDrmSessionManager);
      }
      dispatchPlayerErrorMessage(mContext.getString(R.string.error_offline_playback));
      return null;
    } else {
      for (Listener listener : mListeners) {
        listener.onPlaybackSourceSet(ONLINE_PLAYBACK);
      }
      String userAgent = Util.getUserAgent(context, PLAYER_APP_NAME);
      DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, userAgent);
      return new DashMediaSource.Factory(
              new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
              .setDrmSessionManager(mDrmSessionManager)
              .createMediaSource(videoUri);
    }
  }

  // A method for building HttpDataSource.Factory
  private HttpDataSource.Factory buildHttpDataSourceFactory(Context context, boolean useBandwidthMeter) {
    return new DefaultHttpDataSourceFactory(Util.getUserAgent(context,
            PLAYER_APP_NAME), useBandwidthMeter ? mBandwidthMeter : null);
  }

  // A method for building DrmSessionManager
  private DefaultDrmSessionManager<ExoMediaCrypto> buildDrmSessionManager(Context context,
                                                                          String licenseUrl, String drmToken) {
    HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
            buildHttpDataSourceFactory(context, false));
    // Here the license token is attached to license request
    if (drmToken != null) {
        drmCallback.setKeyRequestProperty("X-AxDRM-Message", drmToken);
    }

    return new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(drmCallback);

  }

  // A method for preparing the player for creation
  public void prepare(Params params, PlayerView playerView) {
    Log.d(TAG, "prepare() called with: params = [" + params + "]");
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
    Log.d(TAG, "startPlayerPrepare() called");
    MediaSource mediaSource = buildMediaSource(mContext, mParams.contentUri);
    if (mediaSource != null) {
      mPlayer.prepare(mediaSource);
      if (mParams.startPosition != 0){
        mPlayer.seekTo(mParams.startPosition);
      }
    }
  }

  // General method for releasing the player
  private void playerRelease(){
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
        Log.d(TAG, "trackSelection, index = " + i + ", selected format = " + trackSelection.getSelectedFormat());
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
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    Log.d(TAG, "onPlayerStateChanged() called with: playWhenReady = [" + playWhenReady + "], state = [" + state + "]");
    switch (state){
      case Player.STATE_IDLE:
        Log.d(TAG, "idle");
        break;
      case Player.STATE_BUFFERING:
        Log.d(TAG, "buffering");
        break;
      case Player.STATE_READY:
        Log.d(TAG, "ready");
        dumpAvailableTracks();
        dumpSelectedTracks();
        break;
      case Player.STATE_ENDED:
        Log.d(TAG, "ended");
        break;
      default:
        Log.d(TAG, "unknown state");
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    Log.d(TAG, "onPlayerError() called with: exception = [" + exception + "]");
    dispatchPlayerError(exception);
  }

  @Override
  public void onCues(@NonNull List<Cue> cues) {
    Log.d(TAG, "onCues() called with: cues = [" + cues + "]");
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
    Log.e(TAG, "onLicenseDownloadFailed for manifest: " + manifestUrl + " code: " + code);
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
    Log.d(TAG, "onLicenseKeysRestored() called with: manifestUrl = [" + manifestUrl + "]");
    onOfflineLicenseAcquired(manifestUrl, keyIds);
  }

  @Override
  public void onLicenseRestoreFailed(int code, String description, String manifestUrl) {
    Log.d(TAG, "License restore failed for manifest: " + manifestUrl + " code: " + code);
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
    Log.d(TAG, "Offline license acquired.");
    // Offline license acquired. Starting in MODE_QUERY
    mDrmSessionManager.setMode(DefaultDrmSessionManager.MODE_QUERY, keyIds);
    startPlayerPrepare();
  }

  // Called when no offline license is found.
  public void onNoOfflineLicenseFound(String manifestUrl, int licenseErrorCode) {
    Log.d(TAG, "No offline license found.");
    DrmMessage drmMessage = null;
    if (!TextUtils.isEmpty(mParams.axDrmMessage)) {
      drmMessage = DrmUtils.parseDrmString(mParams.axDrmMessage);
      if (drmMessage == null) {
        dispatchPlayerErrorMessage(mContext.getString(R.string.error_drm_token_parse));
        return;
      }
    }
    // Check that if license persistent flag is true, then we can try to download license
    if (drmMessage != null && drmMessage.persistent) {
      Log.d(TAG, "Drm message has persistent flag");
      if (!hasConnection()) {
        if (licenseErrorCode == LicenseManagerErrorCode.ERROR_308.getCode()) {
          dispatchPlayerErrorMessage(mContext.getString(R.string.error_drm_keys_expired));
        } else {
          dispatchPlayerErrorMessage(mContext.getString(R.string.error_no_connection_for_license_download));
        }
        return;
      }

      if (TextUtils.isEmpty(mParams.licenseServer)) {
        dispatchPlayerErrorMessage(mContext.getString(R.string.error_license_server));
        return;
      }
      Log.d(TAG, "Trying to download and save license.");
      mOfflineLicenseManager.downloadLicenseWithResult(
              mParams.licenseServer, manifestUrl, mParams.axDrmMessage, true);
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
    return (cm != null && cm.getActiveNetworkInfo() != null);
  }
}
