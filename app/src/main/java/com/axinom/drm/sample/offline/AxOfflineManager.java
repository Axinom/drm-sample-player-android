package com.axinom.drm.sample.offline;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * A class that manages the initialization of DownloadManager and data source factory objects.
 */
public class AxOfflineManager {

    private static final String TAG = AxOfflineManager.class.getSimpleName();
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

    // Return and create the AxOfflineManager instance if necessary
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
        Log.d(TAG, "init() called with: context = [" + context + "], folder = [" + folder + "]");
        if (mDownloadManager == null) {
            mDownloadDirectory = folder;
            mDownloadManager = new DownloadManager(
                    context.getApplicationContext(),
                    getDatabaseProvider(context),
                    getDownloadCache(context),
                    buildHttpDataSourceFactory(),
                    Executors.newFixedThreadPool(6));
            mDownloadTracker = new AxDownloadTracker(context, buildDataSourceFactory(context),
                    mDownloadManager);
        }
    }

    private File getDownloadDirectory() {
        if (mDownloadDirectory == null) {
            mDownloadDirectory = new File(DEFAULT_DOWNLOADS_FOLDER);
            Log.d(TAG, "Setting value to mDownloadDirectory: " + mDownloadDirectory);
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

    // Returns a {@link DataSource.Factory}
    public DataSource.Factory buildDataSourceFactory(Context context) {
        DefaultDataSource.Factory upstreamFactory =
                new DefaultDataSource.Factory(context, buildHttpDataSourceFactory());
        return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
    }

    // Returns a {@link HttpDataSource.Factory}
    private HttpDataSource.Factory buildHttpDataSourceFactory() {
        return new DefaultHttpDataSource.Factory();
    }

    private static CacheDataSource.Factory buildReadOnlyCacheDataSource(
            DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory);
    }

    private DatabaseProvider getDatabaseProvider(Context context) {
        if (databaseProvider == null) {
            databaseProvider = new StandaloneDatabaseProvider(context.getApplicationContext());
        }
        return databaseProvider;
    }
}
