FROM node:22.23.4-alpine3.24@sha256:ebc216c22db53dbde85719a874978d5aec04e6e405ad26a3804c6f9abc4ca1a9 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.31.8-alpine3.24@sha256:713fc2ebfd92e8217db451477182ee91944b123d2b1ee9fd5a8c6e91ca6c3a11
# Alpine v3.24 CVE-2026-4738: pin the fixed zlib package (1.4-r0).
# OpenSSL CVE-2026-107496, CVE-2026-95691, CVE-2026-95692 (MEDIUM), CVE-2026-88822, CVE-2026-88823 (LOW): pin fixed 3.5.9-r1.
# The static dashboard loads no dynamic nginx modules (no load_module), so the optional
# XSLT/njs/image-filter/GeoIP modules and the XML libraries they pull in are removed
# outright instead of pinned. libxml2/libxslt CVEs (e.g. CVE-2026-86144, CVE-2026-87636)
# then cannot apply, and builds no longer break when Alpine withdraws a pinned revision.
RUN apk add --no-cache 'zlib=1.4-r0' 'libcrypto3=3.5.9-r1' 'libssl3=3.5.9-r1' \
 && for pkg in nginx-module-xslt nginx-module-njs nginx-module-image-filter nginx-module-geoip \
               libxslt libxml2; do \
      if apk info -e "$pkg" >/dev/null 2>&1; then apk del --no-cache "$pkg"; fi; \
    done \
 && ! apk info -e libxml2 >/dev/null 2>&1 \
 && ! apk info -e libxslt >/dev/null 2>&1 \
 && nginx -t
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
