import React from 'react';
import { SOLID_PROVIDER_URL } from '../services/auth';

export const LoginScreen = ({ onLogin, loading, error }) => (
  <div className="app">
    <div className="login-container">
      <div className="login-card">
        <h1>WellData Wellness</h1>
        <p>Your personal health and wellbeing companion</p>
        <button className="login-button" onClick={onLogin} disabled={loading}>
          {loading ? 'Connecting...' : 'Login with Solid'}
        </button>
        <p className="login-hint">
          Connect to your Solid pod at<br/>
          <strong>{SOLID_PROVIDER_URL}</strong>
        </p>
        {error && <div className="error-message">{error}</div>}
      </div>
    </div>
  </div>
);

export default LoginScreen;
