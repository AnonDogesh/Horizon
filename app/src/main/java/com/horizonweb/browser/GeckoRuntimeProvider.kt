package com.horizonweb.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.mozilla.geckoview.GeckoRuntime

@Singleton
class GeckoRuntimeProvider @Inject constructor(
    @ApplicationContext appContext: Context
) {
    val runtime: GeckoRuntime = GeckoRuntime.create(appContext)
}
