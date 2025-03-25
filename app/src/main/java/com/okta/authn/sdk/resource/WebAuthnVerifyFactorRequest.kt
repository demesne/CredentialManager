package com.okta.authn.sdk.resource


interface WebAuthnVerifyFactorRequest : VerifyU2fFactorRequest{
    fun getAuthenticatorData(): String?
    fun setAuthenticatorData(authenticatorData: String?): WebAuthnVerifyFactorRequest?
}