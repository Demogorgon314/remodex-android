# Relay Worker

This package is an experimental Cloudflare Workers + Durable Objects implementation of the Remodex relay transport.

It exists alongside [`../relay/`](../relay/) and does not replace the existing Node relay. The goal is to provide a clean Worker-native implementation surface that keeps the public relay interface compatible:

- `GET /health`
- `GET /relay/{sessionId}` with `Upgrade: websocket`
- `POST /v1/trusted/session/resolve`

## Current Scope

- WebSocket relay room semantics for one live Mac and one live iPhone per session
- Trusted-session resolve via a per-Mac Durable Object registry
- Mac absence grace window using Durable Object alarms
- Pure JavaScript state modules with Node unit tests

## Explicitly Out Of Scope

- APNs / FCM push service routes
- Migrating or deleting the existing Node relay
- Wiring this package into the current production/self-host scripts

## Layout

- `src/index.js`: Worker entrypoint and request routing
- `src/session-relay-do.js`: per-session Durable Object
- `src/trusted-registry-do.js`: per-Mac trusted-session registry Durable Object
- `src/session/state.js`: pure session-room state machine
- `src/registry/state.js`: pure trusted-session registry state
- `src/protocol/trusted-session.js`: shared transcript and signature helpers

## Commands

```sh
cd relay-worker
npm install
npm test
npm run dev
```

## Deploy On Cloudflare

1. Install dependencies.

```sh
cd relay-worker
npm install
```

2. Authenticate Wrangler with your Cloudflare account.

```sh
npx wrangler login
npx wrangler whoami
```

3. Deploy the Worker and Durable Objects.

```sh
npx wrangler deploy
```

Wrangler will bundle `src/index.js`, create or migrate the Durable Objects declared in [`wrangler.toml`](wrangler.toml), and print the final Worker URL.

4. Verify the deployed Worker.

```sh
curl https://<your-worker>.<your-subdomain>.workers.dev/health
```

Expected response:

```json
{"ok":true}
```

5. Point the Remodex bridge at the deployed Worker.

```sh
REMODEX_RELAY="wss://<your-worker>.<your-subdomain>.workers.dev/relay" remodex up
```

If you want to use the repo checkout directly instead of a globally installed CLI:

```sh
cd ../phodex-bridge
REMODEX_RELAY="wss://<your-worker>.<your-subdomain>.workers.dev/relay" node ./bin/remodex.js up
```

## Local Simulation

Run the Worker locally with Durable Objects enabled:

```sh
cd relay-worker
npm install
npm run dev
```

The local health check should respond at:

```sh
curl http://127.0.0.1:8787/health
```

Use Wrangler tail to inspect the deployed Worker in real time:

```sh
npx wrangler tail
```

## Notes

- This package currently implements `/health`, `/relay/{sessionId}`, and `/v1/trusted/session/resolve`.
- The optional push-service endpoints are not implemented in this Worker package yet.
- If Cloudflare rejects a deploy because the compatibility date is in the future, update [`wrangler.toml`](wrangler.toml) to a currently accepted date and redeploy.

## References

- Current Node relay implementation: [`../relay/`](../relay/)
- Cloudflare Workers WebSockets: <https://developers.cloudflare.com/workers/examples/websockets/>
- Durable Objects WebSocket hibernation: <https://developers.cloudflare.com/durable-objects/best-practices/websockets/>
- Durable Objects alarms: <https://developers.cloudflare.com/durable-objects/api/alarms/>
