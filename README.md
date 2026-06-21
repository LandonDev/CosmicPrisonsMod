# Cosmic Prisons Official Mod (Fabric Client Mod)

<p align="center">
  <a href="https://github.com/LandonDev/CosmicPrisonsMod/releases/download/launcher-latest/CosmicPrisons-Mod-Launcher.exe">
    <img alt="Download Windows Launcher" src="https://img.shields.io/badge/Windows%20Launcher-Download-0A66C2?style=for-the-badge&logo=windows&logoColor=white">
  </a>
  <a href="https://github.com/LandonDev/CosmicPrisonsMod/releases/download/launcher-latest/cosmic-launcher-macos-universal.app.zip">
    <img alt="Download macOS Launcher" src="https://img.shields.io/badge/macOS%20Launcher-Download-111111?style=for-the-badge&logo=apple&logoColor=white">
  </a>
  <a href="https://github.com/LandonDev/CosmicPrisonsMod/releases/latest/download/CosmicPrisonsMod-latest.jar">
    <img alt="Download Latest Mod JAR" src="https://img.shields.io/badge/Latest%20Mod%20JAR-Download-2DA44E?style=for-the-badge&logo=java&logoColor=white">
  </a>
</p>

## What This Project Is
Cosmic Prisons Official Mod is an optional Fabric client mod for Cosmic Prisons players. The mod uses the Cosmic API broker for server-scoped data and player-approved hooks, so the server stays authoritative and the client stays safe when data or capabilities are missing.

This repository is for mod contributions and feature proposals. It is not the place for launcher internals, private release tooling, deployment secrets, or server plugin source.

## Contributing Features
If you want to add a feature, start by describing the player-facing behavior in plain terms: what appears on screen, when it appears, and how it should fail gracefully when the server does not support it yet. Then define the server contract your change depends on, including any capability flags, message format, limits, and send timing.

PRs should keep compatibility in mind. New clients should still behave safely on old servers, and old clients should degrade safely when they see new server behavior. Include tests for protocol validation and runtime behavior so regressions are obvious.

## Server Requirements In PRs
When a change needs server work, include a `Server Requirements` section in your PR description. Write it as complete sentences, not shorthand, and clearly state the feature goal, required server messages/fields, validation rules, fallback behavior before server rollout, and any compatibility notes.

If server support is required and not yet available, call that out explicitly so reviewers and server developers can plan rollout correctly.

## Official Builds
Only maintainers can publish official builds. Official builds are signed and released through the project release process.

Community forks and local builds are valuable for development and suggestions, but they are not official releases and should not be represented as official in-game.

## Pull Request Quality
Keep PRs focused, explain exactly what changed and why, and include enough detail for reviewers to understand client impact and any server dependency. If a change affects protocol or security-sensitive behavior, explain the reasoning clearly and include validation coverage.
