package com.axinom.drm.sample.player;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.axinom.drm.sample.R;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Util;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class PlayerActivity extends Activity implements DemoPlayer.Listener {

  private static final String TAG = "PlayerActivity";

  public static final String WIDEVINE_LICENSE_SERVER = "widevine_license_server";
  public static final String LICENSE_TOKEN = "license_token";
  public static final String SHOULD_PLAY_OFFLINE = "should_play_offline";

  // Content URI
  private Uri mContentUri;
  // License server URL
  private String mWidevineLicenseServer;
  // License token is sent together with license request to get a license
  private String mLicenseToken;
  // boolean to determine if online or offline source should be used for playback
  private boolean mShouldPlayOffline;
  // save last playback position on suspend
  private long mPlayerPosition;
  // boolean to determine whether playback should automatically start when player is prepared
  private boolean mPlayerStartOnPrepared;

  private static final CookieManager sDefaultCookieManager;
  static {
    sDefaultCookieManager = new CookieManager();
    sDefaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  // View for displaying video playback
  private PlayerView mPlayerView;
  // TextView on the top right corner of the screen indicating whether online or offline source is used for playback
  private TextView mPlayerPlaybackSource;
  // Player
  private DemoPlayer mPlayer;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.player_activity);
    mPlayerView = findViewById(R.id.player_view);
    mPlayerPlaybackSource = findViewById(R.id.player_playback_source);
    CookieHandler currentHandler = CookieHandler.getDefault();
    if (currentHandler != sDefaultCookieManager) {
      CookieHandler.setDefault(sDefaultCookieManager);
    }
    handleIntent(getIntent());
  }

  // method to obtain data (content URI, license token and server URL) from intent used to start this Activity
  private void handleIntent(Intent intent){
    Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
    mContentUri = intent.getData();
    mLicenseToken = intent.getStringExtra(LICENSE_TOKEN);
    mWidevineLicenseServer = intent.getStringExtra(WIDEVINE_LICENSE_SERVER);
    if (intent.getExtras() != null) {
      mShouldPlayOffline = intent.getExtras().getBoolean(SHOULD_PLAY_OFFLINE);
    }
    mPlayerPosition = 0;
    mPlayerStartOnPrepared = true;
  }

  @Override
  public void onNewIntent(Intent intent) {
    Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
    releasePlayer();
    handleIntent(intent);
  }

  @Override
  public void onResume() {
    super.onResume();
    restorePlayer();
  }

  // method for restoring the player (should be called when application is resumed from minimized state for example)
  private void restorePlayer() {
    Log.d(TAG, "restorePlayer() called");
    if (mPlayer == null) {
      if (!requestPermissionsIfNeeded()) {
        preparePlayer();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.d(TAG, "onPause() called");
    releasePlayer();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    releasePlayer();
  }


  // Permission request listener method
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      // prepare player if permissions are granted
      preparePlayer();
    } else {
      Toast.makeText(getApplicationContext(), R.string.storage_permission_denied,
              Toast.LENGTH_LONG).show();
      finish();
    }
  }

  // Permission management methods

  /**
   * Checks whether it is necessary to ask for permission to read storage. If necessary, it also
   * requests permission.
   *
   * @return true if a permission request is made. False if it is not necessary.
   */
  @TargetApi(23)
  private boolean requestPermissionsIfNeeded() {
    Log.d(TAG, "requestPermissionsIfNeeded() called");
    if (requiresPermission(mContentUri)) {
      requestPermissions(new String[] {permission.READ_EXTERNAL_STORAGE}, 0);
      return true;
    } else {
      return false;
    }
  }

  @TargetApi(23)
  private boolean requiresPermission(Uri uri) {
    return Util.SDK_INT >= 23
            && Util.isLocalFileUri(uri)
            && checkSelfPermission(permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED;
  }

  // method for preparing the player
  private void preparePlayer() {
    Log.d(TAG, "preparePlayer() called");
    // add parameters for player
    DemoPlayer.Params params = new DemoPlayer.Params();
    params.contentUri = mContentUri;
    params.axDrmMessage = mLicenseToken;
    params.licenseServer = mWidevineLicenseServer;
    params.startPosition = mPlayerPosition;
    params.startOnPrepared = mPlayerStartOnPrepared;
    params.shouldPlayOffline = mShouldPlayOffline;
    // if player is not existing, create a new instance of it and prepare
    if (mPlayer == null) {
      mPlayer = new DemoPlayer(this);
      mPlayer.addListener(this);
      mPlayer.prepare(params, mPlayerView);
    }
  }

  // method for releasing the player (should be called when application is closed or minimized for example)
  private void releasePlayer() {
    Log.d(TAG, "releasePlayer() called");
    if (mPlayer != null) {
      mPlayerPosition = mPlayer.getCurrentPosition();
      mPlayer.release();
      mPlayer = null;
      mPlayerStartOnPrepared = false;
    } else {
      mPlayerPosition = 0;
    }
  }

  // Callback method for displaying what source is used for playback (online or offline source).
  // Even though the preferred source is determined by the user (by clicking "Play" for online playback
  // and "Play offline" for offline playback), the final confirmation callback about the source used
  // comes from player.
  @Override
  public void onPlaybackSourceSet(int source) {
    if (source == DemoPlayer.ONLINE_PLAYBACK) {
      mPlayerPlaybackSource.setText(getString(R.string.player_online_playback));
    } else {
      mPlayerPlaybackSource.setText(getString(R.string.player_offline_playback));
    }
  }

  // DemoPlayer.Listener implementation
  @Override
  public void onPlayerError(Exception e) {
    Log.d(TAG, "onPlayerError() called with: e = [" + e + "]");
    String errorString;
    if (e instanceof UnsupportedDrmException) {
      // Special case DRM failures.
      UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
      errorString = getString(unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
              ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
    } else if (e instanceof ExoPlaybackException
            && e.getCause() instanceof MediaCodecRenderer.DecoderInitializationException) {
      // Special case for decoder initialization failures.
      MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
              (MediaCodecRenderer.DecoderInitializationException) e.getCause();
      if (decoderInitializationException.codecInfo == null) {
        if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
          errorString = getString(R.string.error_querying_decoders);
        } else if (decoderInitializationException.secureDecoderRequired) {
          errorString = getString(R.string.error_no_secure_decoder,
                  decoderInitializationException.mimeType);
        } else {
          errorString = getString(R.string.error_no_decoder,
                  decoderInitializationException.mimeType);
        }
      } else {
        errorString = getString(R.string.error_instantiating_decoder,
                decoderInitializationException.codecInfo.name);
      }
    } else if (e instanceof DemoPlayer.UnsupportedFormatException){
      errorString = getString(R.string.error_unsupported_file_format);
    } else {
      errorString = getString(R.string.error_player_unknown, e.getMessage());
    }
    showAlertDialog(errorString);
  }

  private void showAlertDialog(String message){
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    alertDialogBuilder.setTitle("Error");
    alertDialogBuilder
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK",new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog,int id) {
                finish();
              }
            });
    AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.show();
  }

}
