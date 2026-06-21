# Official Mod API Migration

The official Fabric mod now identifies itself through the Cosmic API broker as a
normal approved client mod.

## Active API Identity

The mod registers `cosmicapi:main` and sends a no-secret hello when a server
advertises that channel:

- `clientId`: `cosmic_official`
- `modId`: `cosmicprisonsmod`
- `modLoader`: `fabric`
- `installId`: locally generated stable id
- requested scopes:
  - `events:read`
  - `leaderboards:read`
  - `player.profile:read`
  - `gang.profile:read`
  - `player.cooldowns:read`
- requested hooks:
  - `server.event.changed`
  - `player.enchant_proc`
  - `player.absorber.used`
  - `player.command.succeeded`
  - `bandit.killed`

Client mods do not contain API secrets. The Minecraft server resolves the hello
against Cosmic API and Convex using server-side credentials.

## Removed Server Companion Usage

The mod no longer registers or initializes the legacy `servercompanion:main`
network path. The old runtime initializer is inert so it cannot accidentally
send a legacy companion hello.

## API Gaps To Fill

These features are skipped until the new API/broker exposes equivalent data:

- HUD widget snapshots for cooldowns, pets, events, satchels, gang, and
  leaderboards.
- Entity marker deltas for same-gang, peaceful-mining pass-through, gang ping,
  and truce ping markers.
- Inventory item overlays.
- Gang/truce ping actions.
- API-backed settings capability metadata that replaces old server feature
  flags.

The game server needs a `cosmicapi:main` payload handler, broker session resolve
calls to `/v1/cosmic-api/broker/sessions/resolve`, and normal scoped event/action
delivery for the items above.
