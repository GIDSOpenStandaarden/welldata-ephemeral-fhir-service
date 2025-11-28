// Solid OIDC Authentication Service

// Get default provider from runtime config (set in /config.js) or fallback
const DEFAULT_SOLID_PROVIDER = window.WELLDATA_CONFIG?.DEFAULT_SOLID_PROVIDER || 'http://localhost:3000';
const REDIRECT_URI = window.location.origin + '/';
const CLIENT_NAME = 'WellData Wellness App';

// Client ID Document URL - used for external Solid providers that don't support open dynamic registration
const CLIENT_ID_DOCUMENT = window.location.origin + '/clientid.jsonld';

// Storage keys
const STORAGE_KEY_CLIENT = 'welldata_oidc_client';
const STORAGE_KEY_STATE = 'welldata_oidc_state';
const STORAGE_KEY_VERIFIER = 'welldata_oidc_verifier';
const STORAGE_KEY_TOKEN = 'welldata_oidc_token';
const STORAGE_KEY_PROCESSED_CODE = 'welldata_processed_code';
const STORAGE_KEY_PROVIDER = 'welldata_solid_provider';

// Get current Solid provider URL
export const getSolidProvider = () => {
  return localStorage.getItem(STORAGE_KEY_PROVIDER) || DEFAULT_SOLID_PROVIDER;
};

// Check if provider supports dynamic registration
// We assume same-origin providers (e.g., bundled CSS) support it, external providers don't
const supportsDynamicRegistration = () => {
  try {
    const providerUrl = new URL(getSolidProvider());
    const appUrl = new URL(window.location.origin);
    // Same hostname means bundled provider (e.g., docker-compose setup)
    return providerUrl.hostname === appUrl.hostname;
  } catch {
    return false;
  }
};

// Cache for OIDC configuration
let oidcConfigCache = null;
let oidcConfigProvider = null;

// Discover OIDC configuration from provider
const getOidcConfig = async () => {
  const provider = getSolidProvider();

  // Return cached config if same provider
  if (oidcConfigCache && oidcConfigProvider === provider) {
    return oidcConfigCache;
  }

  console.log('Fetching OIDC configuration from:', provider);
  const response = await fetch(`${provider}/.well-known/openid-configuration`);
  if (!response.ok) {
    throw new Error(`Failed to fetch OIDC configuration: ${response.status}`);
  }

  oidcConfigCache = await response.json();
  oidcConfigProvider = provider;
  return oidcConfigCache;
};

// Set Solid provider URL
export const setSolidProvider = (url) => {
  // Normalize URL (remove trailing slash)
  const normalized = url.replace(/\/+$/, '');
  localStorage.setItem(STORAGE_KEY_PROVIDER, normalized);
  // Clear existing client registration and OIDC config cache when provider changes
  localStorage.removeItem(STORAGE_KEY_CLIENT);
  oidcConfigCache = null;
  oidcConfigProvider = null;
};

// Reset to default provider
export const resetSolidProvider = () => {
  localStorage.removeItem(STORAGE_KEY_PROVIDER);
  localStorage.removeItem(STORAGE_KEY_CLIENT);
  oidcConfigCache = null;
  oidcConfigProvider = null;
};

export const DEFAULT_PROVIDER_URL = DEFAULT_SOLID_PROVIDER;

// Generate random string for PKCE and state
const generateRandomString = (length) => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, (byte) => chars[byte % chars.length]).join('');
};

// Generate PKCE code challenge from verifier
const generateCodeChallenge = async (verifier) => {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return btoa(String.fromCharCode(...new Uint8Array(hash)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
};

// Parse JWT to get claims (without validation - server will validate)
export const parseJwt = (token) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

// Get or register client
// - For same-origin providers (bundled CSS): use dynamic registration
// - For external providers: use Client ID Document
const getClient = async (forceNew = false) => {
  // For external providers, always use the Client ID Document
  if (!supportsDynamicRegistration()) {
    console.log('Using Client ID Document for external provider:', CLIENT_ID_DOCUMENT);
    const client = { client_id: CLIENT_ID_DOCUMENT };
    localStorage.setItem(STORAGE_KEY_CLIENT, JSON.stringify(client));
    return client;
  }

  // For local provider, use dynamic registration
  if (!forceNew) {
    const stored = localStorage.getItem(STORAGE_KEY_CLIENT);
    if (stored) {
      try {
        const client = JSON.parse(stored);
        if (client.registered_at && Date.now() - client.registered_at < 5 * 60 * 1000) {
          console.log('Using cached client:', client.client_id);
          return client;
        }
      } catch (e) {
        console.log('Invalid stored client, re-registering');
      }
    }
  }

  localStorage.removeItem(STORAGE_KEY_CLIENT);

  const provider = getSolidProvider();
  console.log('Registering new OIDC client with provider:', provider);
  const response = await fetch(`${provider}/.oidc/reg`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      application_type: 'web',
      client_name: CLIENT_NAME,
      redirect_uris: [REDIRECT_URI],
      grant_types: ['authorization_code', 'refresh_token'],
      response_types: ['code'],
      token_endpoint_auth_method: 'none',
      id_token_signed_response_alg: 'ES256'
    })
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Client registration failed: ${response.status} ${text}`);
  }

  const client = await response.json();
  client.registered_at = Date.now();
  localStorage.setItem(STORAGE_KEY_CLIENT, JSON.stringify(client));
  console.log('Client registered:', client.client_id);
  return client;
};

// Exchange authorization code for tokens
const exchangeCodeForTokens = async (code) => {
  const clientData = localStorage.getItem(STORAGE_KEY_CLIENT);
  const verifier = localStorage.getItem(STORAGE_KEY_VERIFIER);

  if (!clientData || !verifier) {
    throw new Error('Missing client or verifier data');
  }

  const client = JSON.parse(clientData);

  const params = new URLSearchParams({
    grant_type: 'authorization_code',
    code: code,
    redirect_uri: REDIRECT_URI,
    client_id: client.client_id,
    code_verifier: verifier
  });

  const oidcConfig = await getOidcConfig();
  const response = await fetch(oidcConfig.token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params.toString()
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Token exchange failed: ${response.status} ${text}`);
  }

  const tokens = await response.json();

  localStorage.setItem(STORAGE_KEY_TOKEN, JSON.stringify({
    access_token: tokens.access_token,
    id_token: tokens.id_token,
    expires_at: Date.now() + (tokens.expires_in * 1000)
  }));

  localStorage.removeItem(STORAGE_KEY_STATE);
  localStorage.removeItem(STORAGE_KEY_VERIFIER);

  return tokens;
};

// Start login flow - redirect to Solid IDP
export const login = async () => {
  const client = await getClient(true);

  const verifier = generateRandomString(64);
  const challenge = await generateCodeChallenge(verifier);
  const state = generateRandomString(32);

  localStorage.setItem(STORAGE_KEY_STATE, state);
  localStorage.setItem(STORAGE_KEY_VERIFIER, verifier);

  const oidcConfig = await getOidcConfig();
  const authUrl = new URL(oidcConfig.authorization_endpoint);
  authUrl.searchParams.set('response_type', 'code');
  authUrl.searchParams.set('client_id', client.client_id);
  authUrl.searchParams.set('redirect_uri', REDIRECT_URI);
  authUrl.searchParams.set('scope', 'openid profile webid offline_access');
  authUrl.searchParams.set('state', state);
  authUrl.searchParams.set('code_challenge', challenge);
  authUrl.searchParams.set('code_challenge_method', 'S256');

  console.log('Redirecting to:', authUrl.toString());
  window.location.href = authUrl.toString();
};

// Handle OAuth callback
export const handleCallback = async () => {
  const urlParams = new URLSearchParams(window.location.search);
  const code = urlParams.get('code');
  const state = urlParams.get('state');
  const storedState = localStorage.getItem(STORAGE_KEY_STATE);

  if (!code || !state) {
    return null;
  }

  // Check if we already processed this code
  const processedCode = sessionStorage.getItem(STORAGE_KEY_PROCESSED_CODE);
  if (processedCode === code) {
    console.log('Code already processed, skipping...');
    return null;
  }

  // Verify state
  if (state !== storedState) {
    // Check for existing session
    const session = getStoredSession();
    if (session) {
      window.history.replaceState({}, document.title, window.location.pathname);
      return session;
    }
    throw new Error('State mismatch - possible CSRF attack');
  }

  sessionStorage.setItem(STORAGE_KEY_PROCESSED_CODE, code);

  const tokens = await exchangeCodeForTokens(code);
  const idTokenClaims = parseJwt(tokens.id_token);

  window.history.replaceState({}, document.title, window.location.pathname);

  return {
    accessToken: tokens.access_token,
    webId: idTokenClaims?.webid || idTokenClaims?.sub
  };
};

// Get stored session if valid
export const getStoredSession = () => {
  const tokenData = localStorage.getItem(STORAGE_KEY_TOKEN);
  if (!tokenData) return null;

  try {
    const tokens = JSON.parse(tokenData);
    if (tokens.expires_at > Date.now()) {
      const idTokenClaims = parseJwt(tokens.id_token);
      return {
        accessToken: tokens.access_token,
        webId: idTokenClaims?.webid || idTokenClaims?.sub
      };
    }
  } catch (e) {
    console.error('Failed to parse stored session:', e);
  }

  localStorage.removeItem(STORAGE_KEY_TOKEN);
  return null;
};

// Logout - clear all auth state
export const logout = () => {
  localStorage.removeItem(STORAGE_KEY_TOKEN);
  localStorage.removeItem(STORAGE_KEY_CLIENT);
  localStorage.removeItem(STORAGE_KEY_STATE);
  localStorage.removeItem(STORAGE_KEY_VERIFIER);
  sessionStorage.removeItem(STORAGE_KEY_PROCESSED_CODE);
};

// Clear auth error state
export const clearAuthState = () => {
  localStorage.removeItem(STORAGE_KEY_STATE);
  localStorage.removeItem(STORAGE_KEY_VERIFIER);
  sessionStorage.removeItem(STORAGE_KEY_PROCESSED_CODE);
};

