# My Society frontend

Angular 21 PWA with standalone routed components, reactive forms, SCSS and a runtime-configured Spring Boot API.

See the [root README](../README.md) for setup, generated local credentials, society roles and deployment.

```text
npm ci
npm start
npm test -- --watch=false
npm run build
npx playwright install chromium
npx playwright test
```

`public/runtime-config.json` is the only local API address. GitHub Actions generates its hosted counterpart from the repository variable `API_BASE_URL`.

All rights reserved by Sarvesh Barve.
