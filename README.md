# Highwaybuilder for Rusherhack
### Made by musheck

## Download and setup

1. Download [latest release](linkhere).
2. Place the `.jar` files in the `.minecraft/rusherhack/plugins/` directory. (Type `*folder` ingame to open your rusherhack folder)
3. Add the JVM flag `-Drusherhack.enablePlugins=true` to your Minecraft JVM arguments.

> [!Note]
> Plugins placed in the plugins folder will load automatically on game startup. Use the `*reload command` in-game to reload or load new plugins without restarting.

## Usage
There are 4 menus to change settings into:
- General
  - Mode: Place / Dig
  - Type: Cardinal / `Diagonal WIP`
  - Width `Integer`
  - Height `Integer`
  - Rails
    - Left Rail `Boolean`
    - Right Rail `Boolean`
- Placement:
  - Placement Mode:
    - Rotations `use 1.20.x for best results`
    - Airplace
  - Placement Delay `Integer`
- Breaking
  - Max Breaks / Tick `Integer`
- Render
  - Placement Color `Color Setting`
  - Breaking Color `Color Setting`
  - Line Width `Integer`

    
- Align the player on the highway, when building at an even width, use the right block in the middle as the starting position as shown in the example below.\
- The highwaybuilder will keep running forever until disabled since there is no inventory logic added yet

![Alignment](https://imgur.com/a/pqpzLat)

> [!CAUTION]
> Keep the `Placement Delay` and `Max Breaks / Tick` low enough or 2b2t might kick you for exceeding the packet limit!


### TODO:
- Add support for multiple versions other than `1.21.4`
- Add `AutoTool` and `ToolSaver` for mining blocks
- Add a `Replenish` mode for obsidian and other items
- Pause when the player is trying to `eat` or `kill a mob`
- `Spleef entities` that get in the way of the pavement
- Add `echest farming` to allow the paver to keep running for a long period of time
- Add `inventory management` and `restocking`
