package com.axinom.drm.sample.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.axinom.drm.sample.R;
import com.axinom.drm.sample.license.OfflineLicenseManager;
import com.axinom.drm.sample.license.interfaces.IOfflineLicenseManagerListener;
import com.axinom.drm.sample.offline.AxDownloadService;
import com.axinom.drm.sample.offline.AxDownloadTracker;
import com.axinom.drm.sample.offline.AxOfflineManager;
import com.axinom.drm.sample.player.PlayerActivity;
import com.axinom.drm.sample.util.Utility;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *	An activity for selecting samples.
 */
public class SampleChooserActivity extends Activity implements View.OnClickListener,
		IOfflineLicenseManagerListener, DownloadHelper.Callback, AxDownloadTracker.Listener {

	private static final String TAG = SampleChooserActivity.class.getSimpleName();

	// RecyclerView for displaying sample videos
	private RecyclerView mRecyclerView;
	// Adapter for binding sample videos to RecyclerView
	private SampleAdapter mListAdapter;
	// List of sample videos
	private final ArrayList<MediaItem> mMediaItems = new ArrayList<>();

	// Index of currently selected video
	private int mSelectedVideo = -1;
	// Manager class for offline licenses
	private OfflineLicenseManager mLicenseManager;
	// TextViews for displaying offline availability and download progress
	private TextView mOfflineAvailability, mDownloadProgress;
	private Button mButtonDownload, mButtonDelete, mButtonPlay, mButtonSave, mButtonPlayOffline,
			mButtonRemoveLicense, mButtonRemoveAll;
	// A class that manages the downloads: initializes the download requests, enables track selection
	// for downloading and listens to download status change events
	private AxDownloadTracker mAxDownloadTracker;
	// A helper for initializing and removing downloads
	private DownloadHelper mDownloadHelper;

	// For receiving broadcasts about download progress
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null && intent.getExtras() != null) {
				Bundle bundle = intent.getExtras();
				int progress = bundle.getInt(AxDownloadService.PROGRESS);
				mDownloadProgress.setText(String.format(getResources().getString(
						R.string.download_progress), progress));
				if (progress != 0) {
					mDownloadProgress.setVisibility(View.VISIBLE);
				}
			}
		}
	};

	// Permission request listener method
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			// Can be initiated here, after permissions have been granted
			AxOfflineManager.getInstance().init(this);

			if (mAxDownloadTracker == null) {
				mAxDownloadTracker = AxOfflineManager.getInstance().getDownloadTracker();
			}
			mAxDownloadTracker.addListener(this);

			// Start the download service if it should be running but it's not currently.
			// Starting the service in the foreground causes notification flicker if there is no scheduled
			// action. Starting it in the background throws an exception if the app is in the background too
			// (e.g. if device screen is locked).
			if (isNetworkAvailable()) {
				try {
					DownloadService.start(this, AxDownloadService.class);
				} catch (IllegalStateException e) {
					DownloadService.startForeground(this, AxDownloadService.class);
				}
			}

			checkCurrentDownloadStatus();
		} else {
			Toast.makeText(getApplicationContext(), R.string.storage_permission_denied,
					Toast.LENGTH_LONG).show();
		}
	}

	// A method for checking whether network is available
	private boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager
				= (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = null;
		if (connectivityManager != null) {
			activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		}
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	/**
	 * Request permission to write storage
	 */
	@TargetApi(23)
	private void requestPermissions() {
		Log.d(TAG, "requestPermissionsIfNeeded() called");
		requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sample_chooser_activity);
		mRecyclerView = findViewById(R.id.sample_list);
		mRecyclerView.setLayoutManager(new LinearLayoutManager(
				this, LinearLayoutManager.VERTICAL, false));
		mListAdapter = new SampleAdapter();
		mRecyclerView.setAdapter(mListAdapter);

		mOfflineAvailability = findViewById(R.id.main_offline_availability);
		mDownloadProgress = findViewById(R.id.main_download_progress);

		mButtonDownload = findViewById(R.id.main_button_download);
		mButtonDelete = findViewById(R.id.main_button_delete);
		mButtonPlay = findViewById(R.id.main_button_play);
		mButtonSave = findViewById(R.id.main_button_save);
		mButtonPlayOffline = findViewById(R.id.main_button_play_offline);
		mButtonRemoveLicense = findViewById(R.id.main_button_remove_license);
		mButtonRemoveAll = findViewById(R.id.main_button_remove_all);

		// Initializing the OfflineLicenseManager
		mLicenseManager = new OfflineLicenseManager(this);

		requestPermissions();
	}

	class SampleAdapter extends RecyclerView.Adapter<SampleAdapter.ViewHolder> implements View.OnClickListener {

		@Override
		public void onClick(View view) {
			mSelectedVideo = mRecyclerView.indexOfChild(view);
			for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
				if (i == mSelectedVideo) {
					mRecyclerView.getChildAt(i).setBackgroundColor(Color.LTGRAY);
					checkCurrentDownloadStatus();
				} else {
					mRecyclerView.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
				}
			}
		}

		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}

		@NonNull
		@Override
		public SampleAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			TextView v = (TextView) LayoutInflater.from(parent.getContext())
					.inflate(android.R.layout.simple_list_item_1, parent, false);
			return new SampleAdapter.ViewHolder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull SampleAdapter.ViewHolder holder, int position) {
			holder.itemView.setOnClickListener(this);
			if (getItemCount() > 0) {
				((TextView)holder.itemView).setText(getMediaItem(position).mediaMetadata.title);
				if (position == 0 &&  mSelectedVideo == -1) {
					mSelectedVideo = 0;
					holder.itemView.setBackgroundColor(Color.LTGRAY);
					checkCurrentDownloadStatus();
				}
			}
		}

		@Override
		public int getItemCount() {
			return mMediaItems.size();
		}
	}

	// Returns MediaItem from the MediaItems list at the specified position
	private MediaItem getMediaItem(int position) {
		return mMediaItems.get(position);
	}

	// Returns currently selected MediaItem from the MediaItems list
	private MediaItem getSelectedMediaItem() {
		return mMediaItems.get(mSelectedVideo);
	}

	// A method for checking current download status and updating UI accordingly
	private void checkCurrentDownloadStatus() {
		if (mAxDownloadTracker != null && mSelectedVideo >= 0) {
			// If currently selected video is downloaded:
			if (mAxDownloadTracker.isDownloaded(
					String.valueOf(Utility.getPlaybackProperties(getSelectedMediaItem()).uri))) {
				// Hide the "Download" button
				mButtonDownload.setVisibility(View.GONE);
				// Show the "Delete" button
				mButtonDelete.setVisibility(View.VISIBLE);
				// If the selected video is protected, check if the license is also valid
				if (Utility.getDrmConfiguration(getSelectedMediaItem()) != null) {
					mLicenseManager.checkLicenseValid(String.valueOf(
							Utility.getPlaybackProperties(getSelectedMediaItem()).uri));
				} else {
					mButtonSave.setVisibility(View.GONE);
					mButtonPlayOffline.setVisibility(View.VISIBLE);
					mButtonRemoveLicense.setVisibility(View.GONE);
					mButtonRemoveAll.setVisibility(View.GONE);
					mOfflineAvailability.setTextColor(Color.GREEN);
					mOfflineAvailability.setText(
							getResources().getString(R.string.available_offline_not_protected));
				}
			} else {
				// If currently selected video is not downloaded show the "Download" button
				mButtonDownload.setVisibility(View.VISIBLE);
				// Hide the "Delete" button
				mButtonDelete.setVisibility(View.GONE);
				// Hide the "Request license" button
				mButtonSave.setVisibility(View.GONE);
				// Hide the "Play offline" button
				mButtonPlayOffline.setVisibility(View.GONE);
				// Hide the "Remove license" button
				mButtonRemoveLicense.setVisibility(View.GONE);
				// Hide the "Remove all licenses" button
				mButtonRemoveAll.setVisibility(View.GONE);
				// Change offline availability text color to red to indicate an issue
				mOfflineAvailability.setTextColor(Color.RED);
				// Set text to offline availability TextView to show that selected video is not downloaded
				mOfflineAvailability.setText(getResources().getString(R.string.not_available_offline_not_downloaded));
			}
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (mAxDownloadTracker != null) {
			mAxDownloadTracker.addListener(this);
		}
		// If MediaList is empty then fill it with sample videos
		if (mMediaItems.size() == 0) {
			fillListWithSamples(getLocalSampleList());
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		if (mAxDownloadTracker != null) {
			mAxDownloadTracker.removeListener(this);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mButtonDownload.setOnClickListener(this);
		mButtonDelete.setOnClickListener(this);
		mButtonPlay.setOnClickListener(this);
		mButtonSave.setOnClickListener(this);
		mButtonPlayOffline.setOnClickListener(this);
		mButtonRemoveLicense.setOnClickListener(this);
		mButtonRemoveAll.setOnClickListener(this);
		mLicenseManager.setEventListener(this);

		// Registering receiver for download progress
		registerReceiver(mBroadcastReceiver, new IntentFilter(
				AxDownloadService.NOTIFICATION));
		checkCurrentDownloadStatus();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mButtonDownload.setOnClickListener(null);
		mButtonDelete.setOnClickListener(null);
		mButtonPlay.setOnClickListener(null);
		mButtonSave.setOnClickListener(null);
		mButtonPlayOffline.setOnClickListener(null);
		mButtonRemoveLicense.setOnClickListener(null);
		mButtonRemoveAll.setOnClickListener(null);
		if (mLicenseManager != null) {
			mLicenseManager.setEventListener(null);
			mLicenseManager.release();
		}

		// Unregistering receiver for download progress
		unregisterReceiver(mBroadcastReceiver);
		mDownloadProgress.setVisibility(View.GONE);
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.main_button_download) {
			onDownloadPressed();
		} else if (view.getId() == R.id.main_button_delete) {
			onDeletePressed();
		}else if (view.getId() == R.id.main_button_play) {
			onPlayPressed();
		} else if (view.getId() == R.id.main_button_save) {
			onSavePressed();
		} else if (view.getId() == R.id.main_button_play_offline) {
			onPlayOfflinePressed();
		} else if (view.getId() == R.id.main_button_remove_license) {
			onRemoveLicense();
		} else if (view.getId() == R.id.main_button_remove_all) {
			onRemoveAllLicenses();
		}
	}

	// Called when "Delete" button is pressed
	private void onDeletePressed() {
		// License is removed for the selected video
		onRemoveLicense();
		Uri uri = Utility.getPlaybackProperties(getSelectedMediaItem()).uri;
		// Removes a download
		DownloadService.sendRemoveDownload(
				this, AxDownloadService.class, mAxDownloadTracker.getDownloadRequest(uri).id, false);
	}

	private void onRemoveAllLicenses() {
		// All saved licenses are removed
		mLicenseManager.releaseAllLicenses();
	}

	// Called when "Remove license" button is pressed or as a first step of deleting a downloaded video
	private void onRemoveLicense() {
		// License is removed for the selected video
		mLicenseManager.releaseLicense(
				String.valueOf(Utility.getPlaybackProperties(getSelectedMediaItem()).uri));
	}

	// Called when "Download" button is pressed
	private void onDownloadPressed() {
		// First, network availability is determined to proceed
		if (!isNetworkAvailable()) {
			Toast.makeText(getApplicationContext(),
					R.string.error_no_connection_for_downloading, Toast.LENGTH_LONG).show();
			return;
		}
		// Download a license if the content is protected
		downloadLicenseWithResult(getSelectedMediaItem());

		// Prepare DownloadHelper
		if (mDownloadHelper != null) {
			mDownloadHelper.release();
			mDownloadHelper = null;
		}
		mAxDownloadTracker.clearDownloadHelper();
		mDownloadHelper = mAxDownloadTracker.getDownloadHelper(getSelectedMediaItem(),this);
		try {
			mDownloadHelper.prepare(this);
		} catch (Exception e) {
			showToast("Download failed, exception: " + e.getMessage());
		}
	}

	// A method for downloading a license
	private void downloadLicenseWithResult(MediaItem mediaItem) {
		// Result is handled in the OfflineLicenseManager class
		MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(mediaItem);
		if (drmConfiguration != null) {
			mLicenseManager.downloadLicenseWithResult(
					String.valueOf(drmConfiguration.licenseUri),
					String.valueOf(Utility.getPlaybackProperties(mediaItem).uri),
					drmConfiguration.requestHeaders.get("X-AxDRM-Message"),
					true
			);
		}
	}

	// Called when "Play" button is pressed. This method is for starting online playback
	private void onPlayPressed() {
		startVideoActivity(mSelectedVideo, false);
	}

	// Called when "Play offline" button is pressed. This method is for starting offline playback
	private void onPlayOfflinePressed() {
		startVideoActivity(mSelectedVideo, true);
	}

	// Called when "Request license" button is pressed
	private void onSavePressed() {
		// Download license for the selected video
		downloadLicense(getSelectedMediaItem());
	}

	// A method for downloading license for currently selected video
	private void downloadLicense(MediaItem mediaItem) {
		MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(mediaItem);
		if (drmConfiguration != null) {
			mLicenseManager.downloadLicense(
					String.valueOf(drmConfiguration.licenseUri),
					String.valueOf(Utility.getPlaybackProperties(mediaItem).uri),
					drmConfiguration.requestHeaders.get("X-AxDRM-Message"));
		}
	}

	private void showToast(String errorMessage){
		Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
	}

	// A method for converting local sample videos list to JSONArray
	private JSONArray getLocalSampleList() {
		JSONArray jsonArray;
		try {
			InputStream inputStream = getAssets().open("samplelist.json");
			int size = inputStream.available();
			byte[] buffer = new byte[size];
			//noinspection ResultOfMethodCallIgnored
			inputStream.read(buffer);
			inputStream.close();
			String jsonString = new String(buffer, StandardCharsets.UTF_8);
			jsonArray = new JSONArray(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return jsonArray;
	}

	// A method for filling video URL-s and video names lists with sample data
	private void fillListWithSamples(JSONArray jsonArray) {
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.length(); i++) {
				try {
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					MediaItem.Builder mediaItemBuilder = new MediaItem.Builder();
					if (jsonObject.has("title")) {
//						mediaItemBuilder.setMediaId(jsonObject.getString("title"));
						mediaItemBuilder.setMediaMetadata(new MediaMetadata.Builder().setTitle(
								jsonObject.getString("title")).build());
					}
					if (jsonObject.has("videoUrl")) {
						mediaItemBuilder.setUri(jsonObject.getString("videoUrl"));
					}
					if (jsonObject.has("drmScheme")) {
						mediaItemBuilder.setDrmUuid(Util.getDrmUuid(jsonObject.getString("drmScheme")));
						if (jsonObject.has("licenseServer")) {
							mediaItemBuilder.setDrmLicenseUri(jsonObject.getString("licenseServer"));
						}
						if (jsonObject.has("licenseToken")) {
							Map<String, String> requestHeaders = new HashMap<>();
							requestHeaders.put("X-AxDRM-Message", jsonObject.getString("licenseToken"));
							mediaItemBuilder.setDrmLicenseRequestHeaders(requestHeaders);
						}
					}
					MediaItem mediaItem = mediaItemBuilder.build();
					mMediaItems.add(mediaItem);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			// If MediaList is empty then hide the "Play" button and alert the user about it
			if (mMediaItems.size() == 0) {
				mButtonPlay.setVisibility(View.GONE);
				Toast.makeText(getApplicationContext(),
						R.string.error_sample_videos_list_empty, Toast.LENGTH_LONG).show();
			} else {
				mListAdapter.notifyDataSetChanged();
			}
		}
	}

	// A method for starting activity for the playback of currently selected video
	private void startVideoActivity(int position, boolean shouldPlayOffline) {
		Intent intent = new Intent(this, PlayerActivity.class);
		intent.setData(Utility.getPlaybackProperties(getMediaItem(position)).uri);
		MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(getMediaItem(position));
		if (drmConfiguration != null) {
			intent.putExtra(PlayerActivity.DRM_SCHEME, Utility.getDrmSchemeFromUuid(drmConfiguration.uuid));
			intent.putExtra(PlayerActivity.LICENSE_TOKEN, drmConfiguration.requestHeaders.get("X-AxDRM-Message"));
			intent.putExtra(PlayerActivity.WIDEVINE_LICENSE_SERVER, String.valueOf(drmConfiguration.licenseUri));
		}
		intent.putExtra(PlayerActivity.SHOULD_PLAY_OFFLINE, shouldPlayOffline);
		startActivity(intent);
	}

	// A method for generating list of tracks to download
	private int[][] getTracks() {
		ArrayList<int[]> tracks = new ArrayList<>();

		// For demo we currently want to download the max bitrate video track and all audio and text tracks
		// Search through all periods
		for (int period = 0; period < mDownloadHelper.getPeriodCount(); period++) {
			MappingTrackSelector.MappedTrackInfo mappedTrackInfo = mDownloadHelper.getMappedTrackInfo(period);
			// Search through all the renderers
			for (int renderer = 0; renderer < mappedTrackInfo.getRendererCount(); renderer++) {
				boolean isVideoRenderer = false;
				int maxBitrate = Integer.MIN_VALUE;
				int videoTrackIndex = 0;
				TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(renderer);
				if (mappedTrackInfo.getRendererType(renderer) == 2) {
					isVideoRenderer = true;
				}
				// Search through groups inside the renderer
				for (int group = 0; group < trackGroupArray.length; group++) {
					TrackGroup trackGroup = trackGroupArray.get(group);
					// Finally search through tracks (representations)
					for (int track = 0; track < trackGroup.length; track++) {
						// For videos we only care about the max bitrate track that is available
						if (isVideoRenderer && trackGroup.getFormat(track).bitrate > maxBitrate) {
							maxBitrate = trackGroup.getFormat(track).bitrate;
							videoTrackIndex = track;
						} else if (!isVideoRenderer) {
							int [] indexes = new int[] { period, renderer, group, track };
							tracks.add(indexes);
						}
					}
					if (isVideoRenderer) {
						int [] indexes = new int[] { period, renderer, group, videoTrackIndex };
						tracks.add(indexes);
						break; // Found a video, currently not interested in other video groups
					}
				}
			}
		}

		int [][] tracksToDownload = new int[tracks.size()][1];
		for (int i = 0; i < tracks.size(); i++) {
			tracksToDownload[i] = tracks.get(i);
		}
		for (int[] row : tracksToDownload){
			Log.d(TAG, "Tracks to download: " + Arrays.toString(row));
		}
		return tracksToDownload;
	}

	// Called when DownloadHelper is prepared for download
	@Override
	public void onPrepared(@NonNull DownloadHelper helper) {
		// Generate the list of tracks to download
		int [][] tracks = getTracks();
		// Download the currently selected video
		mAxDownloadTracker.download(getSelectedMediaItem().mediaId, tracks);
	}

	// Called when preparation of DownloadHelper failed
	@Override
	public void onPrepareError(@NonNull DownloadHelper helper, @NonNull IOException e) {
		Toast.makeText(this, "Error when preparing download", Toast.LENGTH_SHORT).show();
		Toast.makeText(this, "Failed to start download", Toast.LENGTH_LONG)
				.show();
		Log.e(TAG, "Failed to start download", e);
	}

	// Called when license is downloaded
	@Override
	public void onLicenseDownloaded(String manifestUrl) {
		Toast.makeText(SampleChooserActivity.this, "License saved", Toast.LENGTH_SHORT).show();
		checkCurrentDownloadStatus();
	}

	// Called when license is downloaded with a result containing offline license keyIds
	@Override
	public void onLicenseDownloadedWithResult(String manifestUrl, byte[] keyIds) {
		Toast.makeText(SampleChooserActivity.this, "License saved", Toast.LENGTH_SHORT).show();
		checkCurrentDownloadStatus();
	}

	// Called when license download failed
	@Override
	public void onLicenseDownloadFailed(int code, String description, String manifestUrl) {
		Toast.makeText(SampleChooserActivity.this, "License download error: " + code + " : " + description,
				Toast.LENGTH_LONG).show();
		checkCurrentDownloadStatus();
	}

	// Called when the validity of license is checked
	@Override
	public void onLicenseCheck(boolean isValid, String manifestUrl) {
		if (isValid) {
			mButtonSave.setVisibility(View.GONE);
			mButtonPlayOffline.setVisibility(View.VISIBLE);
			mButtonRemoveLicense.setVisibility(View.VISIBLE);
			mButtonRemoveAll.setVisibility(View.VISIBLE);
			mOfflineAvailability.setTextColor(Color.GREEN);
			mOfflineAvailability.setText(getResources().getString(R.string.available_offline_protected));
		} else {
			mButtonSave.setVisibility(View.VISIBLE);
			mButtonPlayOffline.setVisibility(View.GONE);
			mButtonRemoveLicense.setVisibility(View.GONE);
			mButtonRemoveAll.setVisibility(View.GONE);
			mOfflineAvailability.setTextColor(Color.RED);
			mOfflineAvailability.setText(getResources().getString(R.string.not_available_offline_license_not_valid));
		}
	}

	// Called when the validity of license check fails
	@Override
	public void onLicenseCheckFailed(int code, String description, String manifestUrl) {
		mButtonSave.setVisibility(View.VISIBLE);
		mButtonPlayOffline.setVisibility(View.GONE);
		mButtonRemoveLicense.setVisibility(View.GONE);
		mButtonRemoveAll.setVisibility(View.GONE);
		mOfflineAvailability.setTextColor(Color.RED);
		mOfflineAvailability.setText(getResources().getString(R.string.not_available_offline_license_check_error));
	}

	// Called when license of a video is released
	@Override
	public void onLicenseReleased(String manifestUrl) {
		Toast.makeText(SampleChooserActivity.this, "License released", Toast.LENGTH_SHORT).show();
		checkCurrentDownloadStatus();
	}

	// Called when license release fails
	@Override
	public void onLicenseReleaseFailed(int code, String description, String manifestUrl) {
		Toast.makeText(SampleChooserActivity.this, "License remove error: " + code + " : " + description,
				Toast.LENGTH_LONG).show();
		checkCurrentDownloadStatus();
	}

	// Called when license keys are restored
	@Override
	public void onLicenseKeysRestored(String manifestUrl, byte[] keyIds) {
		Toast.makeText(SampleChooserActivity.this, "License restored", Toast.LENGTH_SHORT).show();
		checkCurrentDownloadStatus();
	}

	// Called when license keys of a video are restored
	@Override
	public void onLicenseRestoreFailed(int code, String description, String manifestUrl) {
		Toast.makeText(SampleChooserActivity.this, "License restore error: " + code + " : " + description,
				Toast.LENGTH_LONG).show();
		checkCurrentDownloadStatus();
	}

	// Called when all saved licenses are released
	@Override
	public void onAllLicensesReleased() {
		Toast.makeText(SampleChooserActivity.this, "All Licenses released", Toast.LENGTH_SHORT).show();
		checkCurrentDownloadStatus();
	}

	// Called when releasing of all saved licenses fails
	@Override
	public void onAllLicensesReleaseFailed(int code, String description) {
		Toast.makeText(SampleChooserActivity.this, "All Licenses release error: " + code + " : " + description,
				Toast.LENGTH_LONG).show();
		checkCurrentDownloadStatus();
	}

	// Called when download state changes
	@Override
	public void onDownloadsChanged(int state) {
		if (state == Download.STATE_COMPLETED || state == Download.STATE_FAILED
				|| state == Download.STATE_REMOVING) {
			// If download state is in "STATE_DOWNLOADING" then TextView for showing the progress is made visible.
			// If download has finished by it being completed "STATE_COMPLETED", failed "STATE_FAILED"
			// or removed "STATE_REMOVING" then hide download progress TextView and update current download status
			mDownloadProgress.setVisibility(View.GONE);
			checkCurrentDownloadStatus();
		} else if (state == Download.STATE_DOWNLOADING) {
			// If download state is in "STATE_DOWNLOADING" then TextView for showing the progress is made visible
			mDownloadProgress.setVisibility(View.VISIBLE);
		}
	}
}
