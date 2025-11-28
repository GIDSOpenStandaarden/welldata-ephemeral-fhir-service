const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  // Serve dynamic Client ID Document for Solid OIDC
  // This must be registered before the static file middleware
  app.use('/clientid.jsonld', (req, res) => {
    // Determine the origin from the request
    const protocol = req.headers['x-forwarded-proto'] || req.protocol || 'http';
    const host = req.headers['x-forwarded-host'] || req.headers.host;
    const origin = `${protocol}://${host}`;

    const clientIdDocument = {
      '@context': ['https://www.w3.org/ns/solid/oidc-context.jsonld'],
      'client_id': `${origin}/clientid.jsonld`,
      'client_name': 'WellData Wellness App',
      'redirect_uris': [`${origin}/`],
      'scope': 'openid profile webid offline_access',
      'grant_types': ['authorization_code', 'refresh_token'],
      'response_types': ['code'],
      'token_endpoint_auth_method': 'none'
    };

    res.set('Content-Type', 'application/ld+json');
    res.send(JSON.stringify(clientIdDocument));
  });

  // Proxy /fhir requests to the FHIR server
  app.use(
    '/fhir',
    createProxyMiddleware({
      target: 'http://welldata-fhir:8080',
      changeOrigin: true,
    })
  );
};
