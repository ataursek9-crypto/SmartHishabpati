package com.example

import android.app.Application
import android.util.Log
import com.example.util.AppLanguageManager
import com.example.util.FirebaseInitHelper

/**
 * Custom Application class for Smart Business Manager (SmartHishabpati).
 * Handles centralized initialization of Firebase, Language Manager, and app resources.
 */
class SmartBusinessApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SmartBusinessApplication onCreate started")

        // 1. Initialize Language Manager to load user language preference (Defaults to Bengali)
        AppLanguageManager.init(this)

        // 2. Centrally guarantee Firebase initialization before any activity or background task accesses Firebase
        val firebaseReady = FirebaseInitHelper.ensureFirebaseInitialized(this)
        Log.i(TAG, "Firebase initialization status: $firebaseReady")
    }

    companion object {
        private const val TAG = "SmartBusinessApp"
    }
}
