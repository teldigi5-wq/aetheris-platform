FROM node:22.23.4-alpine3.24@sha256:ebc216c22db53dbde85719a874978d5aec04e6e405ad26a3804c6f9abc4ca1a9 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.31.8-alpine3.24@sha256:713fc2ebfd92e8217db451477182ee91944b123d2b1ee9fd5a8c6e91ca6c3a11
# Alpine v3.24 CVE-2026-4738: pin the fixed zlib package (1.4-r0).
# libxslt CVE-2026-87636, CVE-2026-9947 (HIGH), CVE-2025-10911, CVE-2026-125350: pin fixed 1.1.43-r4.
RUN apk add --no-cache 'zlib=1.4-r0' 'libxslt=1.1.43-r4'
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
