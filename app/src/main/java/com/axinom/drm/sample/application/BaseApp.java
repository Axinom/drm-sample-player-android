package com.axinom.drm.sample.application;

import android.app.Application;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class BaseApp extends Application {

    public static RequestQueue requestQueue;

    @Override
    public void onCreate() {
        super.onCreate();
        requestQueue = Volley.newRequestQueue(this);
    }
}
