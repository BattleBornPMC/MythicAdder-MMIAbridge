# MMIAbridge

A Paper 1.21.4+ plugin that bridges **MythicMobs** and **ItemsAdder** — letting you use MythicMobs items as ingredients in ItemsAdder recipes, and ItemsAdder items as ingredients in MythicMobs recipes.

No separate config. You edit the other plugins' own YAML files directly.

---

## How it works

On startup (and after either plugin reloads), MMIAbridge scans:

- **ItemsAdder** recipe YAMLs for ingredients prefixed with `mythicmobs:`
- **MythicMobs** item YAMLs for ingredients prefixed with `itemsadder:`

When it finds a cross-plugin ingredient, it registers that recipe internally. When a player opens a crafting table, the plugin checks the grid against its recipes and sets the correct result if one matches.

Both plugins will log a warning about the unknown ingredient — this is expected. MMIAbridge intercepts the crafting event and handles it instead.

---

## Usage

### MythicMobs item in an ItemsAdder recipe

In your ItemsAdder YAML (`plugins/ItemsAdder/...`):

```yaml
info:
  namespace: mypack

recipes:
  crafting_table:
    my_recipe:
      enabled: true
      recipe_type: shaped
      pattern:
        - "XOX"
        - "XOX"
        - "XXX"
      ingredients:
        O: mythicmobs:MySword   # MythicMobs item internal name
        X: AIR                  # empty slot
      result:
        item: mypack:my_item
        amount: 1
```

### ItemsAdder item in a MythicMobs recipe

In your MythicMobs item YAML (`plugins/MythicMobs/Items/...`):

```yaml
MySword:
  Id: DIAMOND_SWORD
  Display: '&bMystic Sword'
  Recipes:
    Crafting:
      Type: SHAPED
      Shape:
        - "ABA"
        - " B "
        - " B "
      Ingredients:
        A: itemsadder:mypack:ruby 1   # itemsadder:<namespace>:<id>
        B: STICK 1
      Amount: 1
```

### Ingredient format summary

| Context | Format | Type |
|---|---|---|
| ItemsAdder YAML | `mythicmobs:InternalName` | MythicMobs item |
| ItemsAdder YAML | `namespace:item_id` | ItemsAdder item |
| ItemsAdder YAML | `MATERIAL_NAME` | Vanilla item |
| MythicMobs YAML | `itemsadder:namespace:id` | ItemsAdder item |
| MythicMobs YAML | `MATERIAL_NAME` | Vanilla item |
| MythicMobs YAML | `MythicItemName` | MythicMobs item |

---

## Commands

Permission: `mythicadder.admin` (default: op)

| Command | Description |
|---|---|
| `/mythicadder reload` | Rescan both plugins' config folders |
| `/mythicadder list` | List all loaded cross-plugin recipes |
| `/mythicadder debug` | Toggle per-craft debug logging |

---

## Installation

1. Drop `MMIAbridge-x.x.x.jar` into your `plugins/` folder
2. Both MythicMobs and ItemsAdder are **soft dependencies** — the plugin starts fine if either is missing
3. Edit your ItemsAdder/MythicMobs YAMLs to use the cross-plugin ingredient format
4. Run `/iazip` or `/mm reload` (or `/mythicadder reload`) — recipes are picked up automatically

---

## Building

Requires Java 21 and Gradle.

```bash
gradlew shadowJar
```

Output: `build/libs/MMIAbridge-1.0.0.jar`

---

## Dependencies

| Plugin | Required |
|---|---|
| Paper 1.21.4+ | Yes |
| MythicMobs 5.x | Soft |
| ItemsAdder 3.x / 4.x | Soft |

---

**Author:** BattleBornPMC
