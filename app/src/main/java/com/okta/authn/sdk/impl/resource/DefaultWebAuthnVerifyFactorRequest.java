package com.okta.authn.sdk.impl.resource;

import com.okta.authn.sdk.resource.WebAuthnVerifyFactorRequest;
import com.okta.sdk.impl.ds.InternalDataStore;

import java.util.Map;

public class DefaultWebAuthnVerifyFactorRequest extends DefaultVerifyU2fFactorRequest implements WebAuthnVerifyFactorRequest {
    private static final String AUTHENTICATOR_DATA = "authenticatorData";

    public DefaultWebAuthnVerifyFactorRequest(InternalDataStore dataStore) {
        super(dataStore);
    }

    public DefaultWebAuthnVerifyFactorRequest(InternalDataStore dataStore, Map<String, Object> properties) {
        super(dataStore, properties);
    }

    @Override
    public String getAuthenticatorData() {
        return getString(AUTHENTICATOR_DATA);
    }

    @Override
    public WebAuthnVerifyFactorRequest setAuthenticatorData(String signatureData) {
        setProperty(AUTHENTICATOR_DATA, signatureData);
        return this;
    }
}