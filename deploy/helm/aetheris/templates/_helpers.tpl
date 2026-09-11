{{- define "aetheris.labels" -}}
app.kubernetes.io/part-of: aetheris
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "aetheris.selectorLabels" -}}
app.kubernetes.io/part-of: aetheris
{{- end -}}
