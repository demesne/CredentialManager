package `in`.demesne.classic.credentialmanager

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authn.sdk.AuthenticationStateHandlerAdapter
import com.okta.authn.sdk.client.AuthenticationClient
import com.okta.authn.sdk.client.AuthenticationClients
import com.okta.authn.sdk.http.RequestContext
import com.okta.authn.sdk.resource.AuthenticationResponse
import com.okta.authn.sdk.resource.WebAuthnVerifyFactorRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class SignInViewModel : ViewModel() {
    private val _state = MutableLiveData<SessionTokenState>(SessionTokenState.Idle)
    val state: LiveData<SessionTokenState> = _state

    private lateinit var authenticationClient: AuthenticationClient

    fun login(activity: Activity, username: String, password: String) {
        val orgUrl = BuildConfig.ISSUER.toHttpUrl().newBuilder().encodedPath("/").build().toString()
        authenticationClient = AuthenticationClients.builder().setOrgUrl(orgUrl).build()
        viewModelScope.launch(Dispatchers.IO) {
            _state.postValue(SessionTokenState.Loading)
            try {
                authenticationClient.authenticate(
                    username,
                    password.toCharArray(),
                    null,
                    object : AuthenticationStateHandlerAdapter() {
                        override fun handleUnknown(unknownResponse: AuthenticationResponse) {
                            initiatePasskeyChallenge(activity, unknownResponse.stateToken)
                        }
                    })
            } catch (e: Exception) {
                _state.postValue(SessionTokenState.Error(e.message.toString()))
            }
        }
    }

    private fun initiatePasskeyChallenge(activity: Activity, stateToken: String) {
        try {
            authenticationClient.verifyFactor(
                "webauthn", stateToken, object : AuthenticationStateHandlerAdapter() {
                    override fun handleUnknown(mfaResponse: AuthenticationResponse) {
                        val embedded = mfaResponse.embedded
                        val challenge = embedded["challenge"] as LinkedHashMap<*, *>
                        validateChallengeUsingSystem(
                            activity, challenge["challenge"] as String, mfaResponse.stateToken
                        )
                    }
                })
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
        }
    }

    private fun validateChallengeUsingSystem(
        activity: Activity, challenge: String, stateToken: String
    ) {
        try {
            val getCredentialRequest = configureGetCredentialRequest(activity, challenge)
            viewModelScope.launch(Dispatchers.IO) {
                val data = getSavedCredentials(activity, getCredentialRequest)
                data?.let {
                    validatePasskeyOnServer(activity, data, stateToken, challenge)
                }
            }
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
        }
    }

    private fun validatePasskeyOnServer(
        activity: Activity,
        data: String,
        stateToken: String,
        challenge: String
    ) {
        try {
            val response: JSONObject = JSONObject(data).getJSONObject("response")

            val authenticatorData = response.getString("authenticatorData")
            val signature = response.getString("signature")
            var clientData = response.getString("clientDataJSON")

            // Modify the clientDataJSON to replace the origin
            val clientDataJSON = JSONObject(String(Base64.decode(clientData, Base64.DEFAULT)))
            clientDataJSON.put("origin", "https://classic.demesne.in")
            clientDataJSON.put("crossOrigin", false)
            clientData = getEncodedChallenge(clientDataJSON.toString().toByteArray(Charsets.UTF_8))

//        val jsonObject = JSONObject().apply {
//            put("stateToken", stateToken)
//            put("clientData", clientData)
//            put("signatureData", signature)
//            put("authenticatorData", authenticatorData)
//        }
//
//        val json = jsonObject.toString()
//
//        val client = OkHttpClient()
//        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
//        val body = json.toRequestBody(mediaType)
//        val request = Request.Builder()
//            .url("https://classic.demesne.in/api/v1/authn/factors/webauthn/verify")
//            .post(body)
//            .addHeader("Content-Type", "application/json")
//            .build()
//
//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) throw IOException("Unexpected code $response")
//
//            // Handle the response
//            val responseBody = response.body?.string()
//            if (responseBody != null) {
//                val jsonResponse = JSONObject(responseBody)
//                // Process the JSON response as needed
//                _state.postValue(SessionTokenState.Token)
//            } else {
//                _state.postValue(SessionTokenState.Error("Empty response body"))
//            }
//        }
            val request = authenticationClient.instantiate(WebAuthnVerifyFactorRequest::class.java)
            request.apply {
                put("stateToken", stateToken)
                put("clientData", clientData)
                put("signatureData", signature)
                put("authenticatorData", authenticatorData)
            }

            authenticationClient.verifyFactor(
                "webauthn",
                request,
                RequestContext().addQuery("rememberDevice", "false"),
                object : AuthenticationStateHandlerAdapter() {
                    override fun handleSuccess(successResponse: AuthenticationResponse) {
                        _state.postValue(SessionTokenState.Token)
                    }

                    override fun handleUnknown(unknownResponse: AuthenticationResponse) {
                        _state.postValue(SessionTokenState.Error("Unknown response"))
                    }
                })
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
        }
    }

    private fun configureGetCredentialRequest(
        activity: Activity, challenge: String
    ): GetCredentialRequest {
        val getPublicKeyCredentialOption =
            GetPublicKeyCredentialOption(fetchAuthJsonFromServer(activity, challenge), null)
        val getCredentialRequest = GetCredentialRequest(
            listOf(
                getPublicKeyCredentialOption
            )
        )
        return getCredentialRequest
    }

    private suspend fun getSavedCredentials(
        activity: Activity, getCredentialRequest: GetCredentialRequest
    ): String? {
        val result = try {
            CredentialManager.create(activity).getCredential(
                activity,
                getCredentialRequest,
            )
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
            return null
        }
        if (result.credential is PublicKeyCredential) {
            val cred = result.credential as PublicKeyCredential
            DataProvider.setSignedInThroughPasskeys(true)
            return cred.authenticationResponseJson
        }
        return null
    }

    private fun fetchAuthJsonToServer(activity: Activity, challenge: String): String {
        return activity.readFromAsset("AuthToServer").replace("<challenge>", challenge)
    }

    private fun fetchAuthJsonFromServer(activity: Activity, challenge: String): String {
        return activity.readFromAsset("AuthFromServer").replace("<challenge>", challenge)
    }

    private fun getEncodedChallenge(challenge: ByteArray): String {
        return Base64.encodeToString(
            challenge, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private fun base64UrlSafeToBase64(str: String): String {
        return str.replace("_", "/").replace("-", "+")
    }

    fun binToStr(bin: ByteArray): String {
        return Base64.encodeToString(bin, Base64.NO_WRAP)
    }

    private fun strToBin(str: String): ByteArray {
        return Base64.decode(base64UrlSafeToBase64(str), Base64.DEFAULT)
    }
}


