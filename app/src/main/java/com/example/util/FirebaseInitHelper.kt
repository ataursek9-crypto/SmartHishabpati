package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Robust Centralized Firebase Initialization Helper.
 * Guarantees that FirebaseApp is safely and idempotently initialized
 * before any Firebase service (Auth, Firestore, Functions) is accessed.
 */
object FirebaseInitHelper {
    private const val TAG = "FirebaseInitHelper"

    const val PROJECT_ID = "smart-business-manager-71879"
    const val APPLICATION_ID = "1:484040397249:android:8a883b7879abd19bd6df40"
    const val API_KEY = "AIzaSyC8mm4ucqjTboLxRRuunqhBT9dSn-qbkEM"
    const val STORAGE_BUCKET = "smart-business-manager-71879.firebasestorage.app"
    const val WEB_CLIENT_ID = "484040397249-h2tfr5q8pgu65ncl9fgaslcbdsvj9kkj.apps.googleusercontent.com"

    @Volatile
    private var initialized = false

    /**
     * Ensures FirebaseApp default instance is initialized.
     * Safe to call multiple times from any thread or component.
     */
    fun ensureFirebaseInitialized(context: Context): Boolean {
        if (initialized && FirebaseApp.getApps(context).isNotEmpty()) {
            return true
        }
        synchronized(this) {
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    initialized = true
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking existing Firebase apps: ${e.message}")
            }

            // 1. Try standard default initialization from google-services.json generated resources
            try {
                val app = FirebaseApp.initializeApp(context.applicationContext)
                if (app != null) {
                    Log.i(TAG, "FirebaseApp successfully initialized from resources: ${app.name}")
                    initialized = true
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Standard FirebaseApp.initializeApp failed: ${e.message}. Proceeding to fallback options.")
            }

            // 2. Fallback initialization using explicit FirebaseOptions
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(APPLICATION_ID)
                    .setApiKey(API_KEY)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build()
                val app = FirebaseApp.initializeApp(context.applicationContext, options)
                Log.i(TAG, "FirebaseApp successfully initialized from fallback FirebaseOptions: ${app.name}")
                initialized = true
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Critical: Fallback FirebaseApp initialization failed: ${e.message}", e)
                return false
            }
        }
    }

    /**
     * Safely retrieves the default FirebaseAuth instance after ensuring initialization.
     */
    fun getAuth(context: Context): FirebaseAuth {
        ensureFirebaseInitialized(context)
        return FirebaseAuth.getInstance()
    }

    /**
     * Safely retrieves the default FirebaseFirestore instance after ensuring initialization.
     */
    fun getFirestore(context: Context): FirebaseFirestore {
        ensureFirebaseInitialized(context)
        return FirebaseFirestore.getInstance()
    }

    /**
     * Checks whether Firebase is currently initialized and ready.
     */
    fun isReady(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
