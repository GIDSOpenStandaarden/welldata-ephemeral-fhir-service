import React, { useState } from 'react';
import { getSolidProvider, setSolidProvider, DEFAULT_PROVIDER_URL } from '../services/auth';

export const LoginScreen = ({ onLogin, loading, error }) => {
  const [providerUrl, setProviderUrl] = useState(getSolidProvider());
  const [showAdvanced, setShowAdvanced] = useState(false);

  const handleProviderChange = (e) => {
    setProviderUrl(e.target.value);
  };

  const handleLogin = () => {
    setSolidProvider(providerUrl);
    onLogin();
  };

  const handleReset = () => {
    setProviderUrl(DEFAULT_PROVIDER_URL);
  };

  const isCustomProvider = providerUrl !== DEFAULT_PROVIDER_URL;

  return (
    <div className="app">
      <div className="login-container">
        <div className="login-card">
          <h1>WellData Wellness</h1>
          <p>Your personal health and wellbeing companion</p>

          <div className="login-form">
            <button className="login-button" onClick={handleLogin} disabled={loading}>
              {loading ? 'Connecting...' : 'Login with Solid'}
            </button>
          </div>

          <p className="login-hint">
            Connect to your Solid pod at<br/>
            <strong>{providerUrl}</strong>
          </p>

          <button
            className="toggle-advanced"
            onClick={() => setShowAdvanced(!showAdvanced)}
          >
            {showAdvanced ? 'Hide options' : 'Use different Solid provider'}
          </button>

          {showAdvanced && (
            <div className="advanced-options">
              <label htmlFor="provider-url">Solid Provider URL:</label>
              <input
                id="provider-url"
                type="url"
                className="login-input"
                value={providerUrl}
                onChange={handleProviderChange}
                placeholder="https://solidcommunity.net"
              />
              {isCustomProvider && (
                <button className="reset-button" onClick={handleReset}>
                  Reset to default
                </button>
              )}
              <p className="provider-examples">
                Examples: solidcommunity.net, login.inrupt.com
              </p>
              {isCustomProvider && (
                <p className="provider-note">
                  Note: External providers require this app to be publicly deployed
                  so they can verify the client identity.
                </p>
              )}
            </div>
          )}

          {error && <div className="error-message">{error}</div>}
        </div>
      </div>
    </div>
  );
};

export default LoginScreen;
