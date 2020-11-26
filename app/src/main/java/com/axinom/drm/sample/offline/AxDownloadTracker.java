package com.axinom.drm.sample.offline;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A class that manages the downloads: initializes the download requests, enables to select tracks
 * for downloading and listens for events where download status changed.
 */
public class AxDownloadTracker {

    /** Listens for changes in the tracked downloads. */
    public interface Listener {

        /** Called when the tracked downloads changed. */
        void onDownloadsChanged(int state);
    }

    private static final String TAG = AxDownloadTracker.class.getSimpleName();

    private final Context mContext;
    private final DataSource.Factory mDataSourceFactory;
    private final HashMap<Uri, Download> mDownloads;
    private final DownloadIndex mDownloadIndex;
    private final CopyOnWriteArraySet<Listener> mListeners;
    private DownloadHelper mDownloadHelper;

    // Construction of AxDownloadTracker
    AxDownloadTracker(Context context, DataSource.Factory dataSourceFactory, DownloadManager downloadManager) {
        this.mContext = context.getApplicationContext();
        mDataSourceFactory = dataSourceFactory;
        mDownloads = new HashMap<>();
        mListeners = new CopyOnWriteArraySet<>();
        mDownloadIndex = downloadManager.getDownloadIndex();
        downloadManager.addListener(new DownloadManagerListener());
        loadDownloads();
    }

    private void loadDownloads() {
        try {
            DownloadCursor loadedDownloads = mDownloadIndex.getDownloads();
            while (loadedDownloads.moveToNext()) {
                Download download = loadedDownloads.getDownload();
                mDownloads.put(download.request.uri, download);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to query mDownloads", e);
        }
    }

    // get the overrides for renderer
    private List<DefaultTrackSelector.SelectionOverride> getRendererOverrides(int periodIndex, int rendererIndex, int[][] representations) {

        List<DefaultTrackSelector.SelectionOverride> overrides = new ArrayList<>();
        // if representations is null then all tracks are downloaded
        if (representations != null) {
            for (int[] indexes : representations) {
                // for every track specification in the list check if we have the correct period and renderer
                if (periodIndex == indexes[0] && rendererIndex == indexes[1]) {
                    // add a selection override for this renderer by specifying groupindex and trackindex
                    overrides.add(new DefaultTrackSelector.SelectionOverride(indexes[2], indexes[3]));
                }
            }
        }

        return overrides;
    }

    /**
     * Download only selected video tracks
     *
     * @param description     A description for this download, for example a video title
     * @param representations list of representations to download in the format of
     *                        [[periodIndex0, rendererIndex0, groupIndex0, trackIndex0], [...]]
     *                        The structure is based on MappedTrackInfo
     *                        For example: [[0,1,0,0]], [0,1,1,0], [0, 1, 2, 0]]
     */
    public void download(String description, int[][] representations) {

        // search through all the periods
        for (int periodIndex = 0; periodIndex < mDownloadHelper.getPeriodCount(); periodIndex++) {

            // get the mapped track info for this period
            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = mDownloadHelper.getMappedTrackInfo(periodIndex);

            // clear any default selections
            mDownloadHelper.clearTrackSelections(periodIndex);

            // look through all the renderers
            for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                mDownloadHelper.addTrackSelectionForSingleRenderer(
                        periodIndex,
                        rendererIndex,
                        DownloadHelper.getDefaultTrackSelectorParameters(mContext),
                        // get the track selection overrides for this renderer
                        getRendererOverrides(periodIndex, rendererIndex, representations));
            }
        }

        // create a DownloadRequest and send it to service
        DownloadRequest downloadRequest = mDownloadHelper.getDownloadRequest(Util.getUtf8Bytes(description));
        DownloadService.sendAddDownload(
                mContext,
                AxDownloadService.class,
                downloadRequest,
                false);
        mDownloadHelper.release();
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    // boolean for determining whether video is downloaded or not
    public boolean isDownloaded(String url) {
        Download download = mDownloads.get(Uri.parse(url));
        return download != null && download.state == Download.STATE_COMPLETED;
    }

    // Find an existing download request by a URI
    public DownloadRequest getDownloadRequest(Uri uri) {
        Download download = mDownloads.get(uri);
        return download != null && download.state != Download.STATE_FAILED ? download.request : null;
    }

    // Returns DownloadHelper
    public DownloadHelper getDownloadHelper(Uri uri, Context context) {
        if (mDownloadHelper == null) {
            try {
                mDownloadHelper = getDownloadHelper(uri, new DefaultRenderersFactory(context));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mDownloadHelper;
    }

    // Clears DownloadHelper
    public void clearDownloadHelper() {
        if (mDownloadHelper != null) {
            mDownloadHelper.release();
            mDownloadHelper = null;
        }
    }

    // Creates a DownloadHelper according to the video format
    private DownloadHelper getDownloadHelper(
            Uri uri, RenderersFactory renderersFactory) {
        int type = Util.inferContentType(uri);
        switch (type) {
            case C.TYPE_DASH:
                return DownloadHelper.forDash(mContext, uri, mDataSourceFactory, renderersFactory);
            case C.TYPE_SS:
                return DownloadHelper.forSmoothStreaming(mContext, uri, mDataSourceFactory, renderersFactory);
            case C.TYPE_HLS:
                return DownloadHelper.forHls(mContext, uri, mDataSourceFactory, renderersFactory);
            case C.TYPE_OTHER:
                return DownloadHelper.forProgressive(mContext, uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    // For listening download changes and sending callbacks notifying about it
    private class DownloadManagerListener implements DownloadManager.Listener {

        @Override
        public void onDownloadChanged(DownloadManager downloadManager, Download download) {
            mDownloads.put(download.request.uri, download);
            for (Listener listener : mListeners) {
                listener.onDownloadsChanged(download.state);
            }
        }

        @Override
        public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
            mDownloads.remove(download.request.uri);
            for (Listener listener : mListeners) {
                listener.onDownloadsChanged(download.state);
            }
        }
    }
}
