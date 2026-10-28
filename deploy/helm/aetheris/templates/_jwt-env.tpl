{{/*
JWT mode contract for the review-only Helm candidate. Nothing here creates
signing keys, fetches JWKS, or inserts RSA PEM into Helm values/manifests.
*/}}
{{- define "aetheris.validateJwtMode" -}}
{{- $v := .Values.authJwt -}}
{{- if not (or (eq $v.mode "legacy") (eq $v.mode "rs256")) -}}
{{- fail "authJwt.mode must be exactly legacy or rs256" -}}
{{- end -}}
{{- if eq $v.mode "legacy" -}}
  {{- if or $v.issuer $v.primaryAudience $v.usersAudience $v.auditAudience $v.orchestratorAudience $v.activeKeyId $v.stagedKeyId $v.signerSecretName $v.verifierSecretName -}}
    {{- fail "Legacy mode must not configure RSA issuer, audiences or external signer/verifier Secret refs" -}}
  {{- end -}}
{{- else -}}
  {{- $required := dict "issuer" $v.issuer "primaryAudience" $v.primaryAudience "usersAudience" $v.usersAudience "auditAudience" $v.auditAudience "orchestratorAudience" $v.orchestratorAudience "activeKeyId" $v.activeKeyId "signerSecretName" $v.signerSecretName "verifierSecretName" $v.verifierSecretName -}}
  {{- range $label, $value := $required -}}
    {{- if or (not $value) (ne $value (trim $value)) -}}
      {{- fail (printf "RSA-only authJwt.%s must be set without surrounding whitespace" $label) -}}
    {{- end -}}
  {{- end -}}
  {{- if or (eq $v.usersAudience $v.auditAudience) (eq $v.usersAudience $v.orchestratorAudience) (eq $v.auditAudience $v.orchestratorAudience) -}}
    {{- fail "RSA-only JWT recipient audiences must be distinct" -}}
  {{- end -}}
  {{- if not (or (eq $v.primaryAudience $v.usersAudience) (eq $v.primaryAudience $v.auditAudience) (eq $v.primaryAudience $v.orchestratorAudience)) -}}
    {{- fail "RSA-only Identity primary audience must match exactly one recipient" -}}
  {{- end -}}
  {{- if not (regexMatch "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$" $v.activeKeyId) -}}
    {{- fail "RSA-only activeKeyId must be a valid pinned key ID" -}}
  {{- end -}}
  {{- if and $v.stagedKeyId (or (not (regexMatch "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$" $v.stagedKeyId)) (eq $v.stagedKeyId $v.activeKeyId)) -}}
    {{- fail "RSA-only stagedKeyId must be a distinct valid pinned key ID" -}}
  {{- end -}}
  {{- if eq $v.signerSecretName $v.verifierSecretName -}}
    {{- fail "RSA-only signer/verifier Secret names must be different" -}}
  {{- end -}}
  {{- range $name := list $v.signerSecretName $v.verifierSecretName -}}
    {{- if not (regexMatch "^[a-z0-9]([-a-z0-9.]*[a-z0-9])?$" $name) -}}
      {{- fail "RSA-only Kubernetes Secret names must be valid resource names" -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- end -}}

{{/*
The JWT private key reference is intentionally emitted only inside the
identity-service Deployment. Every resource server sees verifier public keys
only. All RSA-mode names and IDs are values, NOT secret bytes.
*/}}
{{- define "aetheris.jwtAppEnv" -}}
{{- $v := .root.Values.authJwt -}}
{{- $app := .app -}}
{{- if eq $v.mode "legacy" -}}
- name: AETHERIS_JWT_SECRET
  valueFrom: { secretKeyRef: { name: aetheris-secrets, key: AETHERIS_JWT_SECRET } }
{{- else if eq $app "identity-service" -}}
- name: AETHERIS_IDENTITY_JWT_ISSUER
  value: {{ $v.issuer | quote }}
- name: AETHERIS_IDENTITY_JWT_AUDIENCE
  value: {{ $v.primaryAudience | quote }}
- name: AETHERIS_IDENTITY_JWT_RS256_KEY_ID
  value: {{ $v.activeKeyId | quote }}
- name: AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM
  valueFrom: { secretKeyRef: { name: {{ $v.signerSecretName | quote }}, key: AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM } }
{{- else -}}
{{- if eq $app "gateway" }}
- name: AETHERIS_GATEWAY_JWT_ISSUER
  value: {{ $v.issuer | quote }}
- name: AETHERIS_GATEWAY_JWT_USERS_AUDIENCE
  value: {{ $v.usersAudience | quote }}
- name: AETHERIS_GATEWAY_JWT_AUDIT_AUDIENCE
  value: {{ $v.auditAudience | quote }}
- name: AETHERIS_GATEWAY_JWT_ORCHESTRATOR_AUDIENCE
  value: {{ $v.orchestratorAudience | quote }}
- name: AETHERIS_GATEWAY_JWT_RS256_KEY_ID
  value: {{ $v.activeKeyId | quote }}
- name: AETHERIS_GATEWAY_JWT_RS256_PUBLIC_KEY_PEM
  valueFrom: { secretKeyRef: { name: {{ $v.verifierSecretName | quote }}, key: AETHERIS_JWT_RS256_PUBLIC_KEY_PEM } }
{{- if $v.stagedKeyId }}
- name: AETHERIS_GATEWAY_JWT_RS256_STAGED_KEY_ID
  value: {{ $v.stagedKeyId | quote }}
- name: AETHERIS_GATEWAY_JWT_RS256_STAGED_PUBLIC_KEY_PEM
  valueFrom: { secretKeyRef: { name: {{ $v.verifierSecretName | quote }}, key: AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM } }
{{- end }}
{{- else }}
- name: AETHERIS_BACKEND_JWT_ISSUER
  value: {{ $v.issuer | quote }}
- name: AETHERIS_BACKEND_JWT_AUDIENCE
  value: {{ if eq $app "user-service" }}{{ $v.usersAudience | quote }}{{ else }}{{ $v.auditAudience | quote }}{{ end }}
- name: AETHERIS_BACKEND_JWT_RS256_KEY_ID
  value: {{ $v.activeKeyId | quote }}
- name: AETHERIS_BACKEND_JWT_RS256_PUBLIC_KEY_PEM
  valueFrom: { secretKeyRef: { name: {{ $v.verifierSecretName | quote }}, key: AETHERIS_JWT_RS256_PUBLIC_KEY_PEM } }
{{- if $v.stagedKeyId }}
- name: AETHERIS_BACKEND_JWT_RS256_STAGED_KEY_ID
  value: {{ $v.stagedKeyId | quote }}
- name: AETHERIS_BACKEND_JWT_RS256_STAGED_PUBLIC_KEY_PEM
  valueFrom: { secretKeyRef: { name: {{ $v.verifierSecretName | quote }}, key: AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM } }
{{- end }}
{{- end }}
{{- end -}}
{{- end -}}
