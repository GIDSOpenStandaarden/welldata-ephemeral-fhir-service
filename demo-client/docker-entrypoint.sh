#!/bin/sh
# Substitute environment variables in nginx config

set -e

# Create nginx config from template
envsubst '${FHIR_BACKEND_URL} ${DEFAULT_SOLID_PROVIDER}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf

echo "FHIR backend URL configured: $FHIR_BACKEND_URL"
echo "Default Solid provider configured: $DEFAULT_SOLID_PROVIDER"
