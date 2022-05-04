package com.axinom.drm.sample.player;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.axinom.drm.sample.R;
import com.axinom.drm.sample.util.Utility;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.Util;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class PlayerActivity extends Activity implements DemoPlayer.Listener, View.OnClickListener {

  private static final String TAG = "PlayerActivity";

  public static final String WIDEVINE_LICENSE_SERVER = "widevine_license_server";
  public static final String LICENSE_TOKEN = "license_token";
  public static final String DRM_SCHEME = "drm_scheme";
  public static final String SHOULD_PLAY_OFFLINE = "should_play_offline";

  // Media item that contains relevant information for the player
  private MediaItem mMediaItem;
  // Boolean to determine if online or offline source should be used for playback
  private boolean mShouldPlayOffline;
  // Save last playback position on suspend
  private long mPlayerPosition;
  // Boolean to determine whether playback should automatically start when player is prepared
  private boolean mPlayerStartOnPrepared;

  private static final CookieManager sDefaultCookieManager;
  static {
    sDefaultCookieManager = new CookieManager();
    sDefaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  // View for displaying video playback
  private StyledPlayerView mPlayerView;
  // Console output layout frame
  private FrameLayout mConsoleOutputFrame;
  // ScrollView for console output
  private ScrollView mConsoleOutputScrollView;
  // TextView for console output
  private TextView mConsoleOutputText;
  // Button for showing and hiding console output view
  private Button mConsoleButton;
  // Button for deleting the currently playing content.
  private Button mDeleteButton;
  // Button for copying the console output text to clipboard
  private Button mConsoleCopyButton;
  // Button for clearing the text in console output view
  private Button mConsoleClearButton;
  // Player
  private DemoPlayer mPlayer;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.player_activity);
    mPlayerView = findViewById(R.id.player_view);
    mConsoleOutputFrame = findViewById(R.id.player_console_output_frame);
    mConsoleOutputScrollView = findViewById(R.id.player_console_output_scrollview);
    mConsoleOutputText = findViewById(R.id.player_console_output_text);
    mConsoleOutputText.setMovementMethod(new ScrollingMovementMethod());
    writeToConsole("Activity onCreate() called");
    mConsoleButton = findViewById(R.id.player_console_button);
    mDeleteButton = findViewById(R.id.player_delete_button);
    mConsoleCopyButton = findViewById(R.id.player_console_copy_button);
    mConsoleClearButton = findViewById(R.id.player_console_clear_button);
    CookieHandler currentHandler = CookieHandler.getDefault();
    if (currentHandler != sDefaultCookieManager) {
      CookieHandler.setDefault(sDefaultCookieManager);
    }
    handleIntent(getIntent());
  }

  // A method to obtain data (content URI, license token and server URL) from intent used to start this Activity
  private void handleIntent(Intent intent){
    writeToConsole("Handling intent = [" + intent + "]");
    MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(intent.getData());
    if (intent.hasExtra(DRM_SCHEME)) {
      Map<String, String> requestHeaders = new HashMap<>();
      requestHeaders.put("X-AxDRM-Message", intent.getStringExtra(LICENSE_TOKEN));
      UUID drmUuid = Util.getDrmUuid(intent.getStringExtra(DRM_SCHEME));
      MediaItem.DrmConfiguration.Builder drmConfigurationBuilder
              = new MediaItem.DrmConfiguration.Builder(drmUuid);
      drmConfigurationBuilder.setLicenseUri(
              intent.getStringExtra(WIDEVINE_LICENSE_SERVER));
      drmConfigurationBuilder.setLicenseRequestHeaders(requestHeaders);
      mediaItemBuilder.setDrmConfiguration(drmConfigurationBuilder.build());
    }
    mMediaItem = mediaItemBuilder.build();
    if (intent.getExtras() != null) {
      mShouldPlayOffline = intent.getExtras().getBoolean(SHOULD_PLAY_OFFLINE);
    }
    mPlayerPosition = 0;
    mPlayerStartOnPrepared = true;
    String log = "Parameters from intent: \nContent URI: " + Utility.getPlaybackProperties(mMediaItem).uri
            + "\nShould play offline: " + mShouldPlayOffline;
    MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(mMediaItem);
    if (drmConfiguration != null) {
      log += "\nDRM scheme: " + intent.hasExtra(DRM_SCHEME)
              + "\nLicense Token: " + drmConfiguration.licenseRequestHeaders.get("X-AxDRM-Message")
              + "\nWidevine License Server: " + drmConfiguration.licenseUri;
    }
    writeToConsole(log);
  }

  @Override
  public void onNewIntent(Intent intent) {
    writeToConsole("Activity onNewIntent() called with: intent = [" + intent + "]");
    releasePlayer();
    handleIntent(intent);
  }

  @Override
  public void onResume() {
    super.onResume();
    writeToConsole("Activity onResume() called");
    mConsoleButton.setOnClickListener(this);
    mDeleteButton.setOnClickListener(this);
    mConsoleCopyButton.setOnClickListener(this);
    mConsoleClearButton.setOnClickListener(this);
    restorePlayer();
  }

  // A method for restoring the player (should be called when application is resumed from minimized state for example)
  private void restorePlayer() {
    writeToConsole("Restoring the player");
    if (mPlayer == null) {
      if (!requestPermissionsIfNeeded()) {
        preparePlayer();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    writeToConsole("Activity onPause() called");
    mConsoleButton.setOnClickListener(null);
    mDeleteButton.setOnClickListener(null);
    mConsoleCopyButton.setOnClickListener(null);
    mConsoleClearButton.setOnClickListener(null);
    releasePlayer();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    writeToConsole("Activity onDestroy() called");
    releasePlayer();
  }

  // Permission request listener method
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      writeToConsole("Permissions granted, can start preparing the player.");
      // Prepare player if permissions are granted
      preparePlayer();
    } else {
      Toast.makeText(getApplicationContext(), R.string.storage_permission_denied,
              Toast.LENGTH_LONG).show();
      writeToConsole("Permission to access storage was denied, player will be closed.");
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
    if (requiresPermission(Utility.getPlaybackProperties(mMediaItem).uri)) {
      writeToConsole("Permissions needed, will request.");
      requestPermissions(new String[] {permission.READ_EXTERNAL_STORAGE}, 0);
      return true;
    } else {
      writeToConsole("Permissions not needed.");
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

  // A method for preparing the player
  private void preparePlayer() {
    // Add parameters for player
    DemoPlayer.Params params = new DemoPlayer.Params();
    params.mediaItem = mMediaItem;
    params.startPosition = mPlayerPosition;
    params.startOnPrepared = mPlayerStartOnPrepared;
    params.shouldPlayOffline = mShouldPlayOffline;
    
    // If player is not existing, create a new instance of it and prepare
    if (mPlayer == null) {
      mPlayer = new DemoPlayer(this);
      mPlayer.addListener(this);
      mPlayer.prepare(params, mPlayerView);
    }
  }

  // A method for releasing the player (should be called when application is closed or minimized for example)
  private void releasePlayer() {
    if (mPlayer != null) {
      mPlayerPosition = mPlayer.getCurrentPosition();
      mPlayer.release();
      mPlayer = null;
      mPlayerStartOnPrepared = false;
    } else {
      mPlayerPosition = 0;
    }
  }

  @Override
  public void onPlayerLog(String message, String tag) {
    writeToConsole(message, tag);
  }

  // DemoPlayer.Listener implementation
  @Override
  public void onPlayerError(Exception e) {
    writeToConsole("Player error occurred: [" + e + "]");
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
    writeToConsole("Showing alert dialog with message = [" + message + "]");
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    alertDialogBuilder.setTitle("Error");
    alertDialogBuilder
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK", (dialog, id) -> finish());
    AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.show();
  }

  private void writeToConsole(String log) {
    writeToConsole(log, null);
  }

  private void writeToConsole(String log, String tag) {
    if (tag == null) {
      Log.d(TAG, log);
    } else {
      Log.d(tag, log);
    }
    mConsoleOutputText.append(Utility.getCurrentTime());
    mConsoleOutputText.append(log);
    mConsoleOutputText.append("\n\n");
    mConsoleOutputScrollView.fullScroll(ScrollView.FOCUS_DOWN);
  }

  // Method for deleting currently playing video in the player class.
  // Called when delete button is pressed on the player view.
  private void onDeletePressed() {
    mPlayer.onDeletePressed();
  }

  @Override
  public void onClick(View view) {
    if (view.getId() == mConsoleButton.getId()) {
      if (mConsoleOutputFrame.getVisibility() == View.VISIBLE) {
        mConsoleOutputFrame.setVisibility(View.GONE);
      } else {
        mConsoleOutputFrame.setVisibility(View.VISIBLE);
      }
    } else if (view.getId() == mDeleteButton.getId()) {
      onDeletePressed();
    } else if (view.getId() == mConsoleCopyButton.getId()) {
      ClipboardManager clipboardManager
              = (ClipboardManager)getApplicationContext().getSystemService(CLIPBOARD_SERVICE);
      if (clipboardManager != null) {
        ClipData clipData = ClipData.newPlainText(
                "DRM Sample Player console", mConsoleOutputText.getText());
        clipboardManager.setPrimaryClip(clipData);
      }
    } else if (view.getId() == mConsoleClearButton.getId()) {
      mConsoleOutputText.setText("");
    }
  }

}
