const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  // Only proxy /fhir requests to the FHIR server
  app.use(
    '/fhir',
    createProxyMiddleware({
      target: 'http://welldata-fhir:8080',
      changeOrigin: true,
    })
  );
};
