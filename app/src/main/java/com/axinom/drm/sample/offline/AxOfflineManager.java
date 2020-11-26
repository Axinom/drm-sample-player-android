package com.axinom.drm.sample.offline;

import android.content.Context;
import android.os.Environment;

import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.ActionFileUpgradeUtil;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * A class that manages the initialization of DownloadManager and data source factory objects.
 *
 */
public class AxOfflineManager {

    private static final String TAG = AxOfflineManager.class.getSimpleName();
    private static final String USER_AGENT = "axSdk";
    private static final String DOWNLOAD_ACTION_FILE = "actions";
    private static final String DOWNLOAD_TRACKER_ACTION_FILE = "tracked_actions";
    private static AxOfflineManager sAxOfflineManager;
    private DatabaseProvider databaseProvider;
    private File mDownloadDirectory;
    private DownloadManager mDownloadManager;
    private AxDownloadTracker mDownloadTracker;
    private Cache mDownloadCache;

    private static String DEFAULT_DOWNLOADS_FOLDER;

    static {
        DEFAULT_DOWNLOADS_FOLDER = Environment.getExternalStorageDirectory().getAbsolutePath();
        DEFAULT_DOWNLOADS_FOLDER = DEFAULT_DOWNLOADS_FOLDER.endsWith("/") ? DEFAULT_DOWNLOADS_FOLDER : (DEFAULT_DOWNLOADS_FOLDER + "/");
        DEFAULT_DOWNLOADS_FOLDER += "downloads";
    }

    // Return and create if necessary the AxOfflineManager instance
    public static AxOfflineManager getInstance() {
        if (sAxOfflineManager == null) {
            sAxOfflineManager = new AxOfflineManager();
        }
        return sAxOfflineManager;
    }

    public AxDownloadTracker getDownloadTracker() {
        return mDownloadTracker;
    }

    public DownloadManager getDownloadManager() {
        return mDownloadManager;
    }

    public void init(Context context) {
        init (context, new File(DEFAULT_DOWNLOADS_FOLDER));
    }

    // Initializing of AxOfflineManager
    public synchronized void init(Context context, File folder) {
        if (mDownloadManager == null) {
            mDownloadDirectory = folder;
            DefaultDownloadIndex downloadIndex = new DefaultDownloadIndex(getDatabaseProvider(context));
            upgradeActionFile(DOWNLOAD_ACTION_FILE, downloadIndex, false);
            upgradeActionFile(DOWNLOAD_TRACKER_ACTION_FILE, downloadIndex, true);
            DownloaderConstructorHelper downloaderConstructorHelper =
                    new DownloaderConstructorHelper(getDownloadCache(context), buildHttpDataSourceFactory());
            mDownloadManager = new DownloadManager(context.getApplicationContext(), downloadIndex,
                    new DefaultDownloaderFactory(downloaderConstructorHelper));
            mDownloadTracker = new AxDownloadTracker(context, buildDataSourceFactory(context),
                    mDownloadManager);
        }
    }

    private void upgradeActionFile(String fileName, DefaultDownloadIndex downloadIndex,
                                   boolean addNewDownloadsAsCompleted) {
        try {
            ActionFileUpgradeUtil.upgradeAndDelete(
                    new File(getDownloadDirectory(), fileName),
                    null,
                    downloadIndex,
                    true,
                    addNewDownloadsAsCompleted);
        } catch (IOException e) {
            Log.e(TAG, "Failed to upgrade action file: " + fileName, e);
        }
    }

    private File getDownloadDirectory() {
        if (mDownloadDirectory == null) {
            mDownloadDirectory = new File(DEFAULT_DOWNLOADS_FOLDER);
        }
        return mDownloadDirectory;
    }

    private synchronized Cache getDownloadCache(Context context) {
        if (mDownloadCache == null) {
            mDownloadCache = new SimpleCache(getDownloadDirectory(), new NoOpCacheEvictor(),
                    getDatabaseProvider(context));
        }
        return mDownloadCache;
    }

    /** Returns a {@link DataSource.Factory}. */
    public DataSource.Factory buildDataSourceFactory(Context context) {
        DefaultDataSourceFactory upstreamFactory =
                new DefaultDataSourceFactory(context, buildHttpDataSourceFactory());
        return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
    }

    /** Returns a {@link HttpDataSource.Factory}. */
    private HttpDataSource.Factory buildHttpDataSourceFactory() {
        return new DefaultHttpDataSourceFactory(USER_AGENT);
    }

    private static CacheDataSourceFactory buildReadOnlyCacheDataSource(
            DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSourceFactory(
                cache,
                upstreamFactory,
                new FileDataSource.Factory(),
                null,
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                null);
    }

    private DatabaseProvider getDatabaseProvider(Context context) {
        if (databaseProvider == null) {
            databaseProvider = new ExoDatabaseProvider(context.getApplicationContext());
        }
        return databaseProvider;
    }

}
