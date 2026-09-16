Just Puggin’ Around

A small third-party RuneLite plugin that gives your follower a little “tired” audio cue after you have travelled a configurable distance together.

Important: Just Puggin’ Around is an independent third-party project. It is not affiliated with, endorsed by, sponsored by, or otherwise associated with Jagex Limited or the RuneLite developers.

What it does

Just Puggin’ Around watches the player’s movement and the currently active follower through RuneLite’s client API.

When all of the following are true:

1. You are logged into the game.
2. You have a qualifying follower.
3. You have travelled at least the configured number of tiles.
4. You stop moving for four consecutive game ticks.
5. Your follower is on the same tile or an adjacent tile.

the plugin plays a local tired sound.

By default, only a follower named Pug qualifies. An optional configuration setting can make the same behavior apply to any follower.

The plugin does not perform gameplay actions on the player’s behalf.

Features

* Tracks player movement in game ticks.
* Configurable distance threshold before the tired cue can trigger.
* Default threshold: 25 tiles.
* Default follower: Pug.
* Optional All Followers Tire setting.
* Configurable audio volume from 0% to 100%.
* Uses a local pug_tired.wav sound for the normal cue.
* Has a 1/100 chance to play puppy_tired.wav instead.
* Resets movement tracking when the player is no longer logged in or when there is no qualifying follower.

Configuration

Setting	Default	Description
Distance Before Tiring	25 tiles	Minimum accumulated travel distance before a tired cue can trigger.
Volume	10%	Volume used for the plugin’s audio cue, where supported by the audio device.
All Followers Tire	Off	When enabled, any current follower can qualify instead of only Pug.

What the plugin does not do

Just Puggin’ Around is intentionally passive. It:

* does not click, move the mouse, or press keys;
* does not inject keyboard or mouse input;
* does not send game actions to Jagex servers;
* does not modify outgoing chat;
* does not automate gameplay;
* does not make menu changes;
* does not alter combat, prayer, inventory, equipment, spellbook, or other click zones;
* does not provide boss or PvP combat assistance;
* does not communicate with an external web server;
* does not collect or transmit player information;
* does not request or store account credentials;
* does not execute external programs;
* does not download or dynamically load executable code.

The repository contains only the plugin source, its RuneLite configuration, tests, and the bundled audio resources used by the plugin.

Privacy

The current implementation has no HTTP/network client and contains no external-server communication.

The plugin does not intentionally collect, store, or transmit personal information, account credentials, or information about other players.

Its operation is based on information already available to the local RuneLite client, such as the local player’s position and current follower.

Fair Play and Third-Party Client Rules

This project is intended to remain within RuneLite’s Plugin Hub requirements and Jagex’s applicable third-party-client rules.

The plugin is a passive audio/notification feature. It does not automate player input or gameplay.

However, no README can guarantee that a plugin will always comply with Jagex’s rules. Jagex’s rules and RuneLite’s Plugin Hub review requirements can change, and RuneLite’s review is based on the actual implementation as well as the documentation.

Users are responsible for following the current Jagex rules when using third-party software.

If the current Jagex or RuneLite guidance changes, the plugin should be updated or disabled as appropriate rather than relying on this README as an authoritative statement of the rules.

RuneLite Plugin Hub

The Plugin Hub contains third-party plugins that are not developed or supported by the RuneLite developers. Plugin Hub submissions are reviewed for security and game-rule compliance, but RuneLite does not guarantee that third-party plugins will work correctly or remain compatible indefinitely.

If this project is distributed through the RuneLite Plugin Hub, use the Plugin Hub installation mechanism provided by the RuneLite client.

For development or local testing, follow the build instructions below.

Building

This project targets Java 11 and uses Gradle.

From the repository root:

./gradlew build

On Windows:

gradlew.bat build

For the development RuneLite client:

./gradlew run

A successful build does not by itself establish that the plugin is compliant with current Jagex or RuneLite requirements. In-game behavior should be tested manually.

Development

The plugin is implemented in Java using RuneLite’s client API.

The main components are:

* JustPugginAroundPlugin — movement tracking and audio triggering.
* JustPugginAroundConfig — RuneLite configuration settings.
* pug_tired.wav — normal tired sound.
* puppy_tired.wav — rare alternate sound.

There is no external network service or runtime code download.

Reporting Issues

Please report bugs and feature requests through the repository’s GitHub Issues page.

When reporting a problem, include:

* RuneLite version;
* plugin version or commit;
* relevant plugin settings;
* steps to reproduce the issue; and
* relevant logs or screenshots, after removing any sensitive information.

Contributing

Contributions are welcome when they preserve the plugin’s purpose and remain compatible with the current RuneLite Plugin Hub requirements and Jagex rules.

Before submitting a change, review the current RuneLite Plugin Hub review guidance and Jagex third-party-client guidance.

Changes that introduce automation, input injection, prohibited combat assistance, credential handling, external player-data collection, or other restricted behavior should not be submitted.

Trademarks

“RuneLite”, “Old School RuneScape”, “RuneScape”, “Jagex”, and related names and marks belong to their respective owners.

Their use in this README is solely to identify the software and services with which this third-party project is intended to work. Nothing in this repository should be interpreted as an endorsement, sponsorship, or affiliation with Jagex or RuneLite.

Official Guidance

For authoritative and current requirements, consult:

* Jagex’s current RuneScape Rules.
* Jagex’s current Third-Party Client Guidelines.
* RuneLite’s current Plugin Hub Review documentation.
* RuneLite’s current Rejected or Rolled-Back Features documentation.
* RuneLite Plugin Hub documentation.

Those sources take precedence over this README if their requirements change.

Disclaimer

Just Puggin’ Around is provided on an “as is” basis.

The author does not guarantee compatibility with every RuneLite release or that the plugin will remain eligible for the RuneLite Plugin Hub as Jagex and RuneLite requirements evolve.

Use of the plugin remains subject to the current rules and policies of Jagex and RuneLite.
