# All My Need

All My Need is a vanilla-friendly NeoForge mod that adds a customizable creative inventory tab. It is designed for players who often search for the same blocks, tools, or materials and want a personal, paged shortcut tab inside the creative inventory.

## Features

- Adds a dedicated creative tab for frequently used items.
- Starts with two empty custom pages and a `+` page button for adding more.
- Supports editing page names, up to 8 characters.
- Lets players place items into any slot while edit mode is enabled.
- Saves custom pages to the local client config folder.
- Supports deleting the current page and moving a page upward.
- Provides an optional config entry to place this mod's tab at the front of the creative inventory.
- Works without any required external mod dependency.
- Compatible with JEI when JEI is present.

## Usage

1. Open the creative inventory.
2. Select the All My Need tab.
3. Click `Edit` to enter edit mode.
4. Place items into the custom grid.
5. Rename, add, delete, or move pages as needed.
6. Click `Save` or switch pages to store the current layout.

After saving, items can be taken from the tab like normal creative inventory items.

## Configuration

The common config is generated at:

```text
config/allmyneed-common.toml
```

Available option:

```toml
registerTabAsFirst = false
```

When set to `true`, the All My Need creative tab is moved to the first visible creative tab position. The default is `false` to preserve the vanilla creative tab order unless the player chooses otherwise.

Custom page data is stored client-side at:

```text
config/allmyneed/client_items.json
```

## Compatibility

- Minecraft: 1.21.1
- Mod loader: NeoForge 21.1+
- Java: 21
- JEI: optional, compatible when installed

## Building

```bash
./gradlew build
```

The built jar will be generated under:

```text
build/libs/
```

## License

All My Need is released under the MIT License. See [LICENSE](LICENSE) for details.
