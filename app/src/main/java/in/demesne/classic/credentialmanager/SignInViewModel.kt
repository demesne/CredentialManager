package `in`.demesne.classic.credentialmanager

import android.app.Activity
import android.content.ComponentName
import android.util.Base64
import android.util.Log
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
        Log.i("SignInViewModel", "Trying login")

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
        Log.i("SignInViewModel", "Trying initiatePasskeyChallenge")

        try {
            authenticationClient.verifyFactor(
                "webauthn", stateToken, object : AuthenticationStateHandlerAdapter() {
                    override fun handleUnknown(mfaResponse: AuthenticationResponse) {
                        val embedded = mfaResponse.embedded
                        val challenge = embedded["challenge"] as LinkedHashMap<*, *>
                        val factors = embedded["factors"] as List<LinkedHashMap<*, *>>
                        val credentialIds =
                            factors.map { (it["profile"] as LinkedHashMap<*, *>)["credentialId"] as String }
                        Log.i("SignInViewModel", "Collected credentialIds: $credentialIds")
                        validateChallengeUsingSystem(
                            activity,
                            challenge["challenge"] as String,
                            mfaResponse.stateToken,
                            credentialIds
                        )
                    }
                })
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
        }
    }

    private fun validateChallengeUsingSystem(
        activity: Activity, challenge: String, stateToken: String, credentialIds: List<String>
    ) {
        Log.i("SignInViewModel", "Trying validateChallengeUsingSystem")

        try {
            val getCredentialRequest =
                configureGetCredentialRequest(activity, challenge, credentialIds)

            Log.i(
                "SignInViewModel",
                "GetCredentialRequest: ${getCredentialRequest.credentialOptions}"
            )
            viewModelScope.launch(Dispatchers.IO) {
                val data = getSavedCredentials(activity, getCredentialRequest)
                data?.let {
                    Log.i("SignInViewModel", "Received data: $data")
                    validatePasskeyOnServer(data, stateToken)
                }
            }
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
        }
    }

    private fun validatePasskeyOnServer(
        data: String,
        stateToken: String,
    ) {
        Log.i("SignInViewModel", "Trying validatePasskeyOnServer")

        try {
            val response: JSONObject = JSONObject(data).getJSONObject("response")

            val authenticatorData = response.getString("authenticatorData")
            val signature = response.getString("signature")
            var clientData = response.getString("clientDataJSON")

            val request = authenticationClient.instantiate(WebAuthnVerifyFactorRequest::class.java)
            request.apply {
                put("stateToken", stateToken)
                put("clientData", clientData)
                put("signatureData", signature)
                put("authenticatorData", authenticatorData)
            }

            Log.i("SignInViewModel", "Sending WebAuthn verify factor request: $request")

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
        activity: Activity, challenge: String, credentialIds: List<String>
    ): GetCredentialRequest {
        val allowedCredentials = credentialIds.map { credentialId ->
            ComponentName(activity.packageName, credentialId)
        }.toSet()
        val getPublicKeyCredentialOption =
            GetPublicKeyCredentialOption(
                fetchAuthJsonFromServer(activity, challenge),
                activity.packageName.toByteArray()
//                allowedCredentials
            )

        Log.i(
            "SignInViewModel",
            "GetPublicKeyCredentialOption: ${getPublicKeyCredentialOption.requestJson}"
        )
        return GetCredentialRequest(
            listOf(getPublicKeyCredentialOption)
        )
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