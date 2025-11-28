{{/*
Expand the name of the chart.
*/}}
{{- define "welldata.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "welldata.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "welldata.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "welldata.labels" -}}
helm.sh/chart: {{ include "welldata.chart" . }}
{{ include "welldata.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "welldata.selectorLabels" -}}
app.kubernetes.io/name: {{ include "welldata.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
FHIR Server name
*/}}
{{- define "welldata.fhirServer.name" -}}
{{- printf "%s-fhir" (include "welldata.fullname" .) }}
{{- end }}

{{/*
FHIR Server labels
*/}}
{{- define "welldata.fhirServer.labels" -}}
{{ include "welldata.labels" . }}
app.kubernetes.io/component: fhir-server
{{- end }}

{{/*
FHIR Server selector labels
*/}}
{{- define "welldata.fhirServer.selectorLabels" -}}
{{ include "welldata.selectorLabels" . }}
app.kubernetes.io/component: fhir-server
{{- end }}

{{/*
Demo Client name
*/}}
{{- define "welldata.demoClient.name" -}}
{{- printf "%s-demo" (include "welldata.fullname" .) }}
{{- end }}

{{/*
Demo Client labels
*/}}
{{- define "welldata.demoClient.labels" -}}
{{ include "welldata.labels" . }}
app.kubernetes.io/component: demo-client
{{- end }}

{{/*
Demo Client selector labels
*/}}
{{- define "welldata.demoClient.selectorLabels" -}}
{{ include "welldata.selectorLabels" . }}
app.kubernetes.io/component: demo-client
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "welldata.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "welldata.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
FHIR Server internal URL
*/}}
{{- define "welldata.fhirServer.url" -}}
{{- printf "http://%s:%d" (include "welldata.fhirServer.name" .) (.Values.fhirServer.service.port | int) }}
{{- end }}
