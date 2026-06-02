# Server Companion, Official Mod Trust, and Third-Party API Auth Reference

This document captures the discussion around the current Server Companion implementation, the unintended fork/other-mod access path, what can and cannot be secured in a client-side Minecraft mod, and possible designs for official and third-party APIs.

The scope is intentionally broader than the current mod. It covers the current Fabric client and Shredded server behavior, player communication, official-mod hardening, public API design, third-party mod access, backend-brokered auth, and client identifier limits.

## Executive Summary

The current Server Companion system is server-authoritative for gameplay data, but its official-client gate depends on a static build attestation string sent by the client. That attestation proves that the signed metadata describes an official released jar, but it does not prove that the connected JVM is actually running that exact official jar.

A fork or another mod can register/send on the same custom payload channel, copy a valid official attestation string, send a `ClientHelloC2S` that looks official, and receive companion-only data if the server accepts the handshake.

Open sourcing the mod is not the root problem. Closed source would only obscure the protocol. The actual issue is replayable client proof.

The correct security direction is:

- Keep real secrets server-side.
- Treat client-side mods as untrusted public clients.
- Do not ship reusable API secrets inside mods.
- Keep production Server Companion in `ENFORCE` mode.
- Replace static-only attestation with short-lived, session-bound proof.
- For third-party APIs, use approved app registration, scopes, player grants, rate limits, audit logs, and server-side brokerage.
- For sensitive live gameplay features, keep the Minecraft server authoritative and session-bound.

Current implementation direction:

- Replace `servercompanion:main` with a new `cosmicapi:main` broker channel.
- Client-side Minecraft mods receive approved hook events over the Minecraft server-client protocol, not HTTPS webhooks.
- Backend-owned apps receive signed HTTPS webhooks from Cosmic API delivery workers.
- Both delivery paths use the same event registry, app approval, scope approval, player grant, server-scope, relationship, rate-limit, and audit checks.
- The Minecraft server emits compact internal events into an event bus/outbox and never synchronously calls third-party webhook URLs from the game thread.

Canonical Shredded-side specs:

```text
/Users/landon/IdeaProjects/CosmicPrisons-Shredded/cosmic-admin/docs/cosmic-api-endpoint-scaffold.md
/Users/landon/IdeaProjects/CosmicPrisons-Shredded/cosmic-admin/docs/cosmic-api-hooks-spec.md
/Users/landon/IdeaProjects/CosmicPrisons-Shredded/cosmic-admin/docs/cosmic-api-launch-plan.md
```

## Current Server Companion Design

### Client Channel

The Fabric client registers a normal custom payload channel:

```text
servercompanion:main
```

This is defined client-side as `Identifier.of("servercompanion", "main")` and registered for both S2C and C2S payload types.

This channel name is not private. Any client-side Fabric mod can know it, register it, and send bytes on it.

### Client Hello

On join, the official client attempts to send `ClientHelloC2S` over `servercompanion:main`.

The payload includes:

```text
mod=<mod version>;
build=<build id>;
sha256=<official jar sha256>;
iat=<issued at instant>;
sig=<attestation signature>;
signature=<same attestation signature>;
proof=<optional launcher proof>
```

The signed attestation payload is effectively:

```text
build=<build id>;sha256=<jar sha256>;iat=<issued at instant>
```

The client verifies its embedded `official-build.properties` before using it:

- Metadata signature must verify against the pinned Ed25519 public key.
- Runtime jar hash must match the attested `sha256`.
- If verification fails, the client falls back to unsigned local mode.

This protects honest official builds from accidentally presenting invalid metadata, but it does not stop another mod from copying an official attestation string.

### Server Handshake

The server receives `ClientHelloC2S`, unwraps the frame, decodes the protocol message, checks protocol version, and handles the client hello.

In production-intended `ENFORCE` mode, the server:

- Parses the attestation fields.
- Checks required fields are present.
- Checks `sha256` format.
- Checks `iat` is within the accepted time window.
- Verifies the Ed25519 signature against the pinned public key.
- Checks `(buildId, sha256)` is present in the allowlist loaded from the official launcher manifest/cache.
- Sets `moddedConfirmed = true` only when verification succeeds.

The server considers a companion session enabled only when:

```text
serverHelloSent && moddedConfirmed
```

### Passive Data Feed

Passive data such as event HUD lines is not sent to every player automatically. It is gated by the same enabled-session state.

For HUD/event sync, the server loops online players and skips anyone where:

```text
!api.isCompanionEnabled(playerId)
```

The sender also refuses to flush HUD widgets, markers, inventory overlays, and questions unless:

```text
state.isReadyForFeatures()
```

This means that, in `ENFORCE` mode, failing attestation should block both:

- Interaction features, such as ping intents and question answers.
- Passive feed features, such as event timers, HUD widgets, markers, leaderboards, cooldowns, gang panels, satchels, and item overlays.

### Interaction Features

C2S messages like ping intent are also gated:

```text
if (sessionManager.isCompanionEnabled(playerId)) {
    handle action
}
```

So the same companion-enabled state controls both read-like and action-like companion behavior.

## What Other Mods/Forks Are Doing

The likely technical behavior is:

1. The fork or separate mod registers or sends on `servercompanion:main`.
2. It constructs the same binary protocol frame.
3. It sends a `ClientHelloC2S`.
4. It includes official-looking build attestation fields.
5. If accepted, it receives the same Server Companion S2C data.

This is not use of a public API. It is imitating the internal official companion handshake.

The important distinction:

- Reading open-source code is expected.
- Contributing features is expected.
- Spoofing an internal production handshake to consume official-only server data is unintended.

## Did We Leave A Loophole?

Yes.

The loophole is not that the code is open source. The loophole is that the proof is static and replayable.

The signed attestation proves:

```text
This build id and jar sha were signed by the release key.
```

It does not prove:

```text
This connected client is currently running that exact official jar.
```

Anyone who can obtain a valid official attestation string can replay it from:

- A fork.
- Another mod.
- A custom packet sender.
- A modified client.

The server will see a valid signature and allowlisted build/sha unless additional non-replayable checks exist.

## The Launcher Proof Field

The client can append a `proof` field sourced from:

- JVM system property.
- Environment variable.
- One-time file path property.

However, the current server companion parser and verifier ignore unknown fields other than the attestation fields. The server does not currently validate `proof` as part of enabling the companion session.

Therefore, launcher proof does not currently close the gap.

## What The Current System Does Protect Against

The current `ENFORCE` setup blocks:

- Unsigned local builds.
- Naive forks that generate fake sha/signature fields.
- Clients that omit attestation fields.
- Clients that use malformed attestation fields.
- Clients presenting a valid signature for a build not in the allowlist.
- Clients using an official channel but no accepted companion handshake.

It does not block:

- Replay of a valid official attestation string.
- Another mod installed alongside the official mod observing data after the official mod receives it.
- Client memory inspection.
- Packet-level observation by the player running the client.
- Scraping data that is already exposed through normal Minecraft surfaces.

## Minecraft Client Trust Boundary

The client JVM is not a secure boundary.

If a player controls the machine running the client, they can inspect:

- The jar.
- JVM args.
- Environment variables.
- Files.
- Memory.
- Network requests.
- Inter-mod calls.
- Custom payloads before/after encoding.

TLS protects against other people on the network, not against the person running the client.

Fabric/Java also does not provide a strong security boundary between mods in the same JVM. If two mods are installed together, one mod may be able to observe or interfere with the other.

The server should assume all client-side code is untrusted.

## Player-Facing Response

Use direct language to avoid it being misconstrued as supported because the mod is open source:

```text
To be clear, this is not a public API you were meant to integrate with.

What you are doing is registering/sending on our internal servercompanion:main custom payload channel and imitating the official companion protocol. That means sending a ClientHelloC2S that looks like it came from the official mod, including the signed build attestation fields the server uses to decide whether to enable companion features.

Once that handshake is accepted, the server starts sending companion-only payloads like HUD widget data, event timers, overlays, markers, and ping support. That data path was intended for official signed builds only, not forks and not other mods.

Open source means you can inspect and contribute to the client. It does not mean you are allowed to spoof the official client handshake or consume internal server companion data from another mod/fork.

So yes, if your fork/mod is accessing that data, it is relying on an unintended loophole. We will be closing it server-side, and you should not build around it or represent it as supported functionality.
```

Short version:

```text
Open source means you can inspect/contribute to the mod, not that servercompanion is a public API. That channel is intended for official signed builds only. If forks or other mods are imitating the handshake and receiving companion data, that is an unintended loophole and we will be closing it server-side.
```

## Should Server Companion Be Closed Source?

No.

Closed source would hide implementation details temporarily, but it would not secure the system. The protocol can still be discovered through:

- Decompilation.
- Packet inspection.
- Runtime instrumentation.
- Reading client memory.
- Observing normal behavior.

Security should not depend on the protocol being secret.

Open source still has value:

- Community contributions.
- Easier client feature development.
- Easier review.
- Protocol tests.
- Compatibility discussion.
- Transparency about safe client-side behavior.

Keep the private pieces private:

- Attestation private key.
- Release signing pipeline.
- Launcher proof issuer secrets.
- Backend token signing keys.
- Server-to-backend credentials.

## How To Close The Official Companion Gap

Static build attestation should remain as release authenticity proof, but it should not be the only production gate.

Add a non-replayable, session-bound proof.

### Recommended Official Flow

1. Player joins the server.
2. Server creates a random challenge/nonce tied to:
   - Player UUID.
   - Current login/session.
   - Server id.
   - Expiry timestamp.
3. Official launcher obtains a short-lived proof token from your backend.
4. The token is bound to:
   - Player UUID.
   - Build id.
   - Jar sha256.
   - Server id.
   - Nonce/challenge.
   - Expiry.
   - Token id (`jti`) for replay tracking.
5. Official mod sends `ClientHelloC2S` with:
   - Build id.
   - Jar sha256.
   - Signed build attestation.
   - Player UUID.
   - Server id.
   - Nonce.
   - Timestamp.
   - Launcher proof token.
6. Server verifies:
   - Protocol version.
   - Attestation signature.
   - Build allowlist.
   - Token signature.
   - Token expiry.
   - Token audience/server id.
   - Player UUID.
   - Nonce matches this login session.
   - Token id has not already been used.
7. Server marks the session official only if all checks pass.

### Why This Helps

Copying an official static attestation string is no longer enough.

An attacker would also need a fresh, short-lived, one-time token for that player, server, build, and session nonce.

This does not make the client impossible to instrument, but it removes the easy replay path and lets the server fail closed.

### Remaining Limits

Even with a nonce/proof flow:

- A malicious mod running beside the official mod might try to steal the short-lived token.
- A modified launcher could try to request proof.
- A compromised player machine can observe local process behavior.

The value comes from:

- Short expiry.
- One-time use.
- Binding to player/session/server/build.
- Server-side verification and audit.
- Revocation.

## Public API Versus Server Companion

Not every data type needs the same level of protection.

### Public Or Low-Risk Data

Good candidates for a documented public API:

- Public event schedules.
- Public leaderboards.
- Changelog data.
- Server status.
- Public metadata.

These can be:

- Open.
- Lightly keyed.
- Rate limited.
- Cached.
- Documented.

Assume other tools will use this data.

### Player-Scoped Data

Requires player authorization and scopes:

- Gang information.
- Player profile-adjacent data.
- Cooldowns.
- Personal state.
- Data that is visible to the player but not public globally.

### Sensitive Live Gameplay Data

Should generally remain server-session-bound:

- Pings.
- Markers.
- Peaceful mining pass-through behavior.
- Inventory overlays.
- Live entity/session state.
- Anything that can affect gameplay or reveal contextual information.

Do not expose these as generic HTTP endpoints unless every request is player-authenticated, session-bound, scoped, rate limited, and server-authoritative.

## Why Static API Keys In Mods Do Not Work

If an API key ships inside a client-side mod, it is public in practice.

Players can extract it by:

- Decompiling the jar.
- Inspecting strings.
- Debugging the JVM.
- Reading config files.
- Inspecting memory.
- Capturing the mod's requests.
- Watching custom payloads.

Obfuscation can slow casual copying, but it cannot make the key secure.

Therefore:

- Do not use mod-shipped API keys as secrets.
- Do not treat `client_id` as proof.
- Do not rely on hidden strings in client code.

## Third-Party API Goals

The desired third-party API properties are:

1. Only approved apps/mods can access scoped data.
2. Everything is tracked.
3. Players do not need a painful authorization process.
4. Mod developers do not need to operate their own backend.
5. Secrets are not shipped client-side.

For client-only mods, you cannot fully prove "this exact approved jar made this request." You can prove:

- The request came from an active logged-in Minecraft player session.
- The request claimed an approved app id.
- The player granted that app/scopes.
- The request stayed within rate limits.
- The request was audited.

That is the realistic secure target.

## Recommended Third-Party API Architecture

Use your own backend and the Minecraft server as a broker.

### Access Levels

1. Public API:
   - Events.
   - Public leaderboards.
   - Changelogs.
   - Server status.
   - Optional `client_id` for analytics.

2. Player-scoped mod API:
   - Approved app id.
   - Player grant.
   - Scopes.
   - Active session.
   - Server-side audit.

3. Live gameplay companion:
   - Official companion only.
   - Hardened with session-bound proof.
   - Not exposed as a general third-party API.

## Backend-Brokered Flow For Third-Party Mods

This avoids requiring every mod developer to run a backend.

### Developer Registration

1. Developer registers an app in your developer portal or admin config.
2. You assign a public `client_id`.
3. Developer declares requested scopes:
   - `events.read`
   - `leaderboards.read`
   - `gang.read`
   - `cooldowns.read`
   - etc.
4. You approve/reject the app.
5. You configure:
   - Allowed scopes.
   - Rate limits.
   - Owner/contact.
   - Display name.
   - Audit level.
   - Revocation status.

No client secret is issued for client-only mods.

### Player Session

1. Player joins Cosmic.
2. Minecraft server already knows the authenticated player UUID/session.
3. Third-party mod sends a request over a new in-game channel, for example:

```text
cosmicapi:request
```

Request fields:

```json
{
  "client_id": "examplemod",
  "request_id": "uuid-or-counter",
  "scope": "gang.read",
  "endpoint": "gang.summary",
  "params": {}
}
```

4. Minecraft server validates:
   - Player is online.
   - Player session is ready.
   - `client_id` exists.
   - App is approved and not revoked.
   - Scope is allowed for that app.
   - Player has granted that app/scope, if required.
   - Request is within rate limits.
   - Endpoint is allowed for the scope.

5. If no grant exists, show an in-game prompt:

```text
ExampleMod wants access to:
- Events
- Leaderboards
- Gang Info

[Allow] [Deny]
```

6. Store the grant server-side:

```text
player_uuid + client_id + scopes
```

7. Minecraft server calls your backend using a server-side secret.
8. Backend returns allowed data.
9. Minecraft server filters if needed.
10. Minecraft server sends the response back to the requesting mod over the in-game channel.

### Why This Works

The client-side mod never receives a real secret.

The backend secret lives only on:

- Your Minecraft server.
- Your backend service.

The player only approves once, in-game.

You can centrally:

- Rate limit.
- Audit.
- Revoke apps.
- Revoke player grants.
- Track usage.
- Change scopes.
- Block abusive apps.

## Easy Player Authorization

If player consent is required, make it in-game and one-time.

Commands:

```text
/api apps
/api app revoke <app>
/api app info <app>
```

Prompt behavior:

- Show app display name.
- Show requested scopes.
- Show whether the app is approved by Cosmic.
- Require one click or command confirmation.
- Store grant server-side.
- Let player revoke any time.

For low-risk public data, no player approval is needed.

For player-specific data, approval should be required.

## Tracking And Auditing

Log every API request with:

- Timestamp.
- Player UUID.
- Player name.
- `client_id`.
- Scope.
- Endpoint.
- Request id.
- Server id.
- Result code.
- Data category.
- Rate-limit decision.
- Grant id/version.
- Source channel or HTTP route.

Rate limit by:

- `client_id`.
- Player UUID.
- IP/session where available.
- Endpoint.
- Scope.
- Global backend budget.

Expose admin tooling:

```text
/api apps list
/api apps revoke <client_id>
/api apps usage <client_id>
/api apps grants <player>
/api apps counters
```

## Client ID Theft

A `client_id` cannot be hidden in a client-side mod.

Mod authors can try:

- Obfuscating strings.
- Fetching id from remote config.
- Splitting strings in code.
- Encrypting the id with a key also in the jar.

These are speed bumps only.

If another mod steals an approved `client_id`, it can claim to be that app. The server cannot reliably distinguish two client-only mods inside the same JVM.

The correct response is to make `client_id` insufficient by itself:

- Require player grant for non-public scopes.
- Scope grants narrowly.
- Audit by `client_id` and player UUID.
- Rate limit by multiple dimensions.
- Let players revoke grants.
- Revoke or rotate abused app ids.
- Require backend-held credentials for higher-trust apps.

## Mods With Their Own Backend

If a third-party developer has a backend, stronger app authentication is possible.

Flow:

1. Developer registers an app.
2. You issue a backend credential or public/private key pair.
3. Their backend stores the secret/private key.
4. Client mod talks to their backend or obtains short-lived backend-signed requests.
5. Your API verifies backend signature and scopes.

This is stronger because the secret is not in the client mod.

However, it increases developer burden. For most community mods, your backend-brokered in-game channel is easier.

## Direct HTTP From Client Mods

Direct client-to-HTTP API can be acceptable for:

- Public data.
- Low-risk cached data.
- OAuth/device-code user auth flows.

It is not appropriate for hidden app secrets.

If direct HTTP is used for player data:

- Use OAuth-style authorization.
- Use PKCE/device code for public clients.
- Issue short-lived access tokens.
- Bind token to player/account, client id, scopes, audience, expiry.
- Never ship client secrets.

Token example:

```json
{
  "sub": "player-uuid",
  "client_id": "examplemod",
  "scopes": ["events.read", "gang.read"],
  "aud": "cosmic-api",
  "exp": 1760000000,
  "jti": "one-time-token-id"
}
```

This is secure user/app authorization, but it may add player friction. The in-game broker flow avoids a separate login because the Minecraft server already knows the player session.

## Recommended Policy

### Official Companion

Use for:

- Live gameplay companion features.
- Pings.
- Markers.
- Inventory overlays.
- Peaceful mining.
- Official HUD data.

Security:

- `ENFORCE` mode.
- Signed build attestation.
- Build allowlist.
- Session-bound nonce/proof.
- One-time short-lived token.
- Server-side audit.

### Public API

Use for:

- Events.
- Public leaderboards.
- Changelogs.
- Server status.

Security:

- Optional app registration.
- Rate limits.
- Caching.
- Abuse monitoring.

### Third-Party Player API

Use for:

- Player-visible but non-public data.
- Gang info.
- Cooldowns.
- Profile-adjacent data.

Security:

- Approved `client_id`.
- Player grant.
- Scopes.
- Active player session.
- In-game request broker.
- Backend called only by your server.
- Full audit.

### Restricted Features

Do not expose broadly:

- Gameplay actions.
- Entity markers with tactical value.
- Inventory-derived live data.
- Anti-cheat-sensitive data.
- Anything that gives advantage beyond intended UI.

Keep those official-only or require very strict session-bound checks.

## Practical Next Steps

1. Confirm production config stays `servercompanion.attestation.policy: "ENFORCE"`.
2. Add server-side validation for launcher/session proof.
3. Add server-issued nonce/challenge to companion handshake.
4. Bind proof tokens to player UUID, build id, sha256, server id, nonce, expiry, and `jti`.
5. Store used `jti`/nonce server-side until expiry.
6. Fail closed when proof verification fails.
7. Keep dev/test environments able to use unsigned builds through explicit non-production config only.
8. Define a separate third-party API channel, such as `cosmicapi:request`.
9. Build app registration and scope config.
10. Build in-game one-click player grant/revoke UI.
11. Add audit/rate-limit storage.
12. Decide which data is public, player-scoped, official-only, or never exposed.

## Key Takeaways

- Open source is not the problem.
- Static client attestation is replayable.
- The current companion feed is gated for both passive data and interaction features, but replay can bypass the intended official-only boundary.
- Do not ship API secrets in mods.
- A `client_id` is public metadata, not authentication.
- For third-party mods without their own backend, your Minecraft server should broker API access.
- For official-only features, use short-lived, one-time, session-bound proof.
- For player-specific third-party data, use scopes and one-time in-game grants.
- For public data, publish a real public API and rate limit it.
