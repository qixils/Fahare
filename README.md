# Fahare

Fahare (short for **Fa**st **Ha**rdcore **Re**set) is a Minecraft: Java Edition multiplayer server mod that
automatically resets your hardcore world when all online players die. It is currently available for Paper 1.19.3+.

## Configuration

| Setting      | Default | Description                                                                                                                                                                          |
|--------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `backup`     | `true`  | Whether to save and backup worlds before resetting.<br>When disabled, worlds will be permanently deleted upon reset (much faster).                                                   |
| `auto-reset` | `true`  | Whether resets should happen automatically upon the death of all players.<br>When disabled, you may instead run `/fahare reset` to manually reset the world.                         |
| `any-death`  | `false` | Whether to trigger a reset upon *any* death.<br>When disabled, automatic resets will trigger only when all players are dead.<br>This setting is ignored if `auto-reset` is disabled. |
| `setSeed`    | `false` | Whether to use a set seed when resetting                                                                                                                                             |

## Commands

| Command         | Permission     | Description               |
|-----------------|----------------|---------------------------|
| `/fahare reset` | `fahare.reset` | Manually reset the world. |
