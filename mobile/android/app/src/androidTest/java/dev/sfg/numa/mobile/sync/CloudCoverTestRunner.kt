package dev.sfg.numa.mobile.sync

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Bitmap conversion tests do not initialize the playback graph or any account session. */
class CloudCoverTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
