package `in`.demesne.classic.credentialmanager

import android.app.Activity
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
import org.json.JSONArray
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
                        validateChallengeUsingSystem(
                            activity,
                            constructChallenge(mfaResponse),
                            mfaResponse.stateToken,
                        )
                    }
                })
        } catch (e: Exception) {
            _state.postValue(SessionTokenState.Error(e.message.toString()))
        }
    }

    private fun constructChallenge(mfaResponse: AuthenticationResponse): String {
        val embedded = JSONObject(mfaResponse.embedded)
        val factorsArray = embedded.getJSONArray("factors")
        val credentialIds = mutableListOf<String>()

        for (i in 0 until factorsArray.length()) {
            credentialIds.add(
                factorsArray.getJSONObject(i).getJSONObject("profile").getString("credentialId")
            )
        }
        var challenge = embedded.getJSONObject("challenge").getString("challenge")

        val allowCredentialsJson = JSONArray().apply {
            credentialIds.forEach { id ->
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("id", id)
                })
            }
        }

        val response = JSONObject().apply {
            put("challenge", challenge)
            put("allowCredentials", allowCredentialsJson)
            put("timeout", 60000)
            put("userVerification", "preferred")
            put("rpId", "classic.demesne.in")
        }.toString()

        Log.i("SignInViewModel", "Challenge request : $response")

        return response
    }

    private fun validateChallengeUsingSystem(
        activity: Activity, challenge: String, stateToken: String
    ) {
        Log.i("SignInViewModel", "Validating challenge using system. Request: $challenge")
        try {
            val getCredentialRequest = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(challenge))
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
            Log.e("SignInViewModel", "Error validating challenge: ${e.message}")
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
}