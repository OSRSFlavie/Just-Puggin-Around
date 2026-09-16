# Just Puggin' Around

A RuneLite plugin that plays a configurable sound notification when your pug/follower has traveled a certain distance with you.

## Features

* Configurable travel-distance threshold
* Configurable notification volume
* Option to trigger for all followers or only a pug
* Plays a local notification sound when the configured distance is reached
* Includes a rare alternate puppy sound for a little variety
* Resets its travel counter when the follower is no longer present

## Configuration

The plugin provides the following configuration options:

### Distance

Sets how many tiles the player must travel before the tired sound can trigger.

### Volume

Controls the volume of the notification sound.

### All Followers

When enabled, the plugin works with any follower.

When disabled, the notification is limited to the pug.

## How It Works

Just Puggin' Around passively monitors the local player's movement and follower state.

When the configured travel distance is reached and the follower is adjacent to the player, the plugin plays a notification sound.

The plugin does not:

* Automate mouse or keyboard input
* Perform game actions on the player's behalf
* Send gameplay actions to the game server
* Control the player's character
* Interact with external services

All notification sounds are bundled with the plugin and played locally.

## RuneLite Plugin Hub

Just Puggin' Around is a third-party RuneLite plugin.

The plugin provides a passive audio notification based on local player movement and follower state. It does not automate player input or perform gameplay actions.

Plugin Hub eligibility is determined by RuneLite's current review requirements and may change over time.

## Installation

The recommended installation method is through the RuneLite Plugin Hub.

Search for **Just Puggin' Around** in RuneLite's plugin configuration panel and enable the plugin.

## Development

This project uses Gradle and follows the standard RuneLite plugin project structure.

To build the project locally:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The project also includes the RuneLite development launcher used for local testing.

## License

See the `LICENSE` file included with this repository.
