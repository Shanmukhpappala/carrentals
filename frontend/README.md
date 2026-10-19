# React + Vite

## Local development

Run `npm install` and `npm run dev` from this directory. The Vite server binds to the local network and forwards `/api` requests to the backend configured by `VITE_API_BASE_URL` in `.env.development`. Phone and laptop browsers using the same Vite server therefore reach the same backend instead of resolving `localhost` on each device.

For a different backend, update `VITE_API_BASE_URL` before starting Vite. Production builds use same-origin `/api` requests by default; set `VITE_API_BASE_URL` at build time only when the API is hosted on a separate origin. Vite exposes `VITE_*` values in the browser bundle, so never put server credentials or secrets in them. Production backend settings and secret handling are documented in [`backend/README.md`](../backend/README.md).

## Production routing

The frontend uses browser-history routes such as `/bookings` and `/admin/bookings`. Configure the production web server to serve the built `index.html` for frontend paths that are not static files. Keep `/api/**` routed to the Spring Boot backend before applying the frontend fallback. For example, with Nginx:

```nginx
location ^~ /api/ {
	proxy_pass http://backend;
}

location / {
	try_files $uri $uri/ /index.html;
}
```

Vite's development and preview servers provide the SPA fallback automatically; the production host must provide the equivalent rewrite.

## Deployment prerequisites

- Build and run the backend with Java 21 and Maven. The backend uses PostgreSQL. Flyway is disabled by default in production; enable it explicitly only after reviewing the migration history and rehearsing against a disposable database, as described in [`backend/README.md`](../backend/README.md).
- Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the production PostgreSQL service. Do not use the localhost datasource fallback.
- Activate the backend `prod` profile and provide required production variables as documented in [`backend/README.md`](../backend/README.md). Do not use local profile fallbacks in production.
- Generate `JWT_SECRET` from at least 32 random bytes and store it in deployment secret management. Never pass it through a `VITE_*` variable.
- Use the documented opt-in administrator bootstrap/rotation procedure; do not leave bootstrap enabled after the one-time operation.
- Set `CORS_ALLOWED_ORIGINS` to the actual trusted HTTPS frontend origin(s). No production domain is assumed here; do not include localhost or use a wildcard.
- Set `VITE_API_BASE_URL` at frontend build time when the API is not same-origin. Leave it empty only when the production web server proxies `/api/**` to Spring Boot.
- Serve `frontend/dist` and configure the SPA fallback above. Route `/api/**` to Spring Boot first; never rewrite API requests to `index.html`.
- Production uses Hibernate schema validation. Flyway is opt-in in that profile; review and rehearse migration history on a disposable PostgreSQL database before enabling migrations for a real deployment.

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and Oxlint's TypeScript related rules in your project.
