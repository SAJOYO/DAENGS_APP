package com.daengs.app.walk.sync

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.runner.AndroidJUnitRunner

/** Secondary process has no providers; the production Application is never instantiated. */
class MeasurementReadRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(processName == PROCESS) { "Use the dedicated measurementread instrumentation process" }
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        check(providers.none { it.processName == PROCESS }) { "The probe process must not host application providers" }
        return super.newApplication(cl, Application::class.java.name, context)
    }
    companion object { const val PROCESS = "com.daengs.app.devtest:measurementread" }
}
