# Incog-Claims v3.11

By **Siegeisok67** and the **Incog Dev Team**.
Repo: https://github.com/Siegeisok67/incog-claims

## Building

Requires Java 21 and Maven, and internet access (to pull the Paper API from
`repo.papermc.io`). This was written/assembled without a live build environment,
so you'll want to `mvn clean package` it yourself and fix any version-specific
API drift before deploying to production.

```
mvn clean package
```

The jar will be at `target/Incog-Claims-3.11.jar`. Drop it in `plugins/` on your
Purpur 26.2 server (built against Paper API `1.21.1-R0.1-SNAPSHOT` - bump the
version in `pom.xml` to match whatever Minecraft version your Purpur build
actually runs, since "26.2" is a Purpur build number, not an MC/API version).

## What it does

- **First join:** players get a GUI to pick **Aggressive/PVP** or **Peaceful**.
  Choosing PVP shows an explicit warning that other PVP players can raid them.
- **Claim cores:** `/iclaims core` gives a placeable "Claim Core" item/block
  (default: Beacon, configurable). Placing it creates a cube claim centered on
  it. Right-clicking it opens the claim GUI (info / expand / trust / delete).
  Breaking the core (by the owner, a trusted member, an OP, or - for PVP claims
  only - a PVP raider holding the Claim Breaker pickaxe) deletes the claim.
- **Peaceful claims:** fully protected. No block breaking, no explosions, no
  PVP inside them, period. Peaceful owners can freely trust/invite PVP players
  as members (trust isn't restricted by type in either direction) - trusted
  players just get normal build/break rights regardless of their own type.
- **PVP claims:** protected from normal hand-mining by non-members, but other
  **PVP-flagged** players can raid them by blowing up blocks with TNT
  (`require-pvp-attacker-to-raid` in config controls whether the TNT-lighter
  has to themselves be PVP). The core block is explosion-immune - it can only
  be destroyed by hand (by the owner/trusted, or a raider with the pickaxe).
  Non-members (including other PVP players) cannot place TNT or manually
  light existing TNT while standing inside someone else's claim - raids must
  come from outside (cannons, etc.), not by walking in and detonating by hand.
- **Claim enter notice:** when a player walks into a claim they get a
  non-intrusive action-bar message: "You have entered a Peaceful/Aggressive
  claim (SIZE)". It never reveals the owner's name or the claim's location.
- **Claim Breaker pickaxe:** an ultra-rare Netherite Pickaxe with real
  Efficiency V (`claimbreaker-loot-chance` in config, default 0.15%) that can
  appear in generated structure loot. It lets a PVP player hand-mine blocks
  inside another PVP player's claim - each such break sends the breaker the
  message "You have broken your claim block." It is never obtainable from
  villager trades (a safety-net listener cancels any trade offering it).
- **Expand GUI:** owners spend passively-earned "claim blocks" (earned every
  `earn-interval-minutes`, default every 10 minutes while online, capped at
  `max-claim-blocks` in config so it isn't unlimited) to resize their claim
  between configured cube sizes (48/96/144/192 by default).
- **Trusted members:** `/iclaims trust <player>` / `/iclaims untrust <player>`
  let others build/break freely inside your claim, regardless of playstyle.
- **Switching sides:** `/iclaims switch` toggles PVP/Peaceful with a 3-day
  (configurable) cooldown. OPs can clear a connected player's cooldown with
  `/iclaims admin removecooldown <player>`.
- **OP-only admin commands:** all `/iclaims admin ...` and `/iclaims reload`
  commands require the sender to actually be a server operator (`isOp()`),
  not just hold a permission node. Regular claim protection bypass (breaking
  into any claim in-world) still uses the `incogclaims.admin` permission node
  as before, since that's a separate concern from the admin subcommands.

## Commands

Base command works as both `/iclaims` and `/incogclaims`.

| Command | Description |
|---|---|
| `/iclaims help` | Show help |
| `/iclaims core` | Get a placeable claim core |
| `/iclaims gui` | Open your claim menu |
| `/iclaims trust <player>` | Trust a player in your claim |
| `/iclaims untrust <player>` | Remove trust |
| `/iclaims switch` | Switch PVP/Peaceful (3-day cooldown) |
| `/iclaims info` | View your claim/type/claim-block info |
| `/iclaims delete` | Delete your own claim |
| `/iclaims admin delete <player>` | (OP only) delete any player's claim |
| `/iclaims admin give claimbreaker <player>` | (OP only) give the rare pickaxe |
| `/iclaims admin give blocks <player> <amount>` | (OP only) give claim blocks (respects the cap) |
| `/iclaims admin removecooldown <player>` | (OP only) clear a connected player's switch cooldown |
| `/iclaims reload` | (OP only) reload config.yml |

## Permissions

- `incogclaims.use` - default true, lets players use the claim system
- `incogclaims.admin` - default op, bypasses claim protection in-world
  (breaking/placing/opening containers). Admin *subcommands* additionally
  require the sender to be a real server op, independent of this node.

## Config

See `src/main/resources/config.yml` - claim core material, claim sizes/costs,
earn interval/amount, max claim blocks cap, switch cooldown, TNT raid toggle,
whether the TNT attacker must themselves be PVP to raid, OP bypass toggle, and
the Claim Breaker loot chance.

## Notes / things you may want to extend

- Data is stored in flat YAML (`claims.yml`, `players.yml`) in the plugin's
  data folder, autosaved every 5 minutes and on shutdown. Swap in SQLite/MySQL
  if you expect a large number of claims.
- Claim lookup is a simple linear scan (`ClaimManager#getClaimAt`) - fine for
  normal claim counts, but consider a chunk-indexed lookup if you expect
  thousands of claims on one server.
- Container-open protection currently blocks all non-members from opening
  chests/etc. in a claim outright; you may want to loosen that for successful
  PVP raiders if you want looting to be part of the raid.
