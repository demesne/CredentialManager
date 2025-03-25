package `in`.demesne.classic.credentialmanager

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.OidcConfiguration

class MainApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        AuthFoundation.initializeAndroidContext(context)
        OidcConfiguration.default = OidcConfiguration(
            clientId = BuildConfig.CLIENT_ID,
            defaultScope = "openid email profile offline_access",
            issuer = BuildConfig.ISSUER
        )
    }
}