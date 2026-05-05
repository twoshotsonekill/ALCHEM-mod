# Alchemod - AI-Powered Minecraft Magic Mod

A sophisticated Fabric mod for Minecraft 1.21.x that uses AI (OpenRouter API) to generate magical items, transmute materials, build structures, and create procedural content.

## Features

### 🎨 Item Creator
- **AI-Generated Items**: Combine two items to create entirely new magical items with unique properties
- **Dynamic Item Identity**: Items store all metadata (effects, rarity, behavior scripts) in NBT components
- **Persistent Across Restarts**: Item data is saved with the world and survives server restarts
- **Unlimited Creation**: No slot exhaustion — unlimited items can be created
- **Custom Behaviors**: Optional JavaScript sandboxing for custom item behavior
- **Sprite Generation**: AI generates custom pixel-art sprites for each unique item

### ⚗️ Alchemical Forge
- **Item Transmutation**: Combine two vanilla items to get a transmuted result
- **Enrichment Options**: Add custom names, lore, enchantments, and color to results
- **Smart Defaults**: AI intelligently suggests enhancements based on ingredient combinations
- **NBT Customization**: Fine-tune item properties with a dedicated UI

### 🏗️ World Builder (Build Creator)
- **Procedural Building**: Describe any structure and the AI generates building code
- **Efficient Placement**: Asynchronous block placement (256 blocks/tick) prevents server freezes
- **Full Block Palette**: Access to 90+ vanilla Minecraft blocks plus custom alchemod blocks
- **Geometry Primitives**: Built-in drawing functions for lines, boxes, and spheres
- **Seedable Randomness**: Reproducible builds with seed control

### 🔮 Infuser & Transmuter
- **Advanced Alchemy**: Specialized blocks for different transformation types
- **Configuration**: Full customization of model selection, timeouts, and token budgets

## Installation

### Prerequisites
- **Java 21+** (Minecraft 1.21.x requirement)
- **Fabric Loader** (0.16.9 or newer)
- **Fabric API** (0.119.4+1.21.4 or newer)
- **OpenRouter API Key** (Get a free key at https://openrouter.ai)

### Steps

1. **Download the Mod**
   - Grab `alchem-mod-1.0.0.jar` from releases
   - Place it in your Minecraft `mods` folder

2. **Set Your API Key**
   - **Option A (Recommended)**: Set an environment variable
     - Windows: System Properties → Environment Variables → New Variable
     - Name: `OPENROUTER_API_KEY`
     - Value: `sk-or-v1-xxxxxxxx...`
   - **Option B**: Edit `config/alchemod.properties` and paste your key there

3. **Launch Minecraft**
   - Restart your launcher to apply environment changes if using Option A
   - Load a Fabric instance with Alchemod installed

## Usage

### Item Creator Block
1. **Placement**: Place the **Item Creator** block using the creative menu or crafting
2. **Combine Items**: Insert two different items into the input slots (left side)
3. **Crafting**: The block will query the AI to generate a new magical item
4. **Collection**: Wait for the progress bar to fill, then take the result from the output slot

**New Items Support:**
- **Effects**: Speed, Strength, Regeneration, Resistance, Fire Resistance, Night Vision, Absorption, Luck, Haste, Jump Boost, Slow Falling, Water Breathing
- **Special Abilities**: Ignite, Knockback, Heal Aura, Launch, Freeze, Drain, Phase, Lightning, Void Step
- **Rarities**: Common, Uncommon, Rare, Epic, Legendary
- **Item Types**: Use Items, Bows, Spawn Eggs, Food, Swords, Totems, Throwables
- **Behavior Scripts**: JavaScript sandboxing for custom behavior

### Alchemical Forge Block
1. **Placement**: Place the **Alchemical Forge** block
2. **Load Items**: Insert two items into the input slots
3. **Transmute**: The AI generates a transmutation result
4. **Optional Customization**: Click the NBT button to customize the output (name, lore, enchantments, color)
5. **Collect**: Take the result from the output slot

### World Builder (Build Creator) Block
1. **Placement**: Place the **Build Creator** block
2. **Text Entry**: Click the block and enter your building prompt
   - Examples: "Small wooden cabin", "Tall stone tower", "Floating island with waterfalls"
3. **Building**: The AI generates building code and places blocks asynchronously
4. **Completion**: Watch the progress bar as blocks are placed (non-blocking)
5. **Result**: Your structure appears smoothly without server lag

**Supported Block Palettes:**
- Vanilla: Stone, wood, bricks, ores, glass, concrete, terracotta, and 60+ more
- Alchemod: Arcane Bricks, Void Stone, Ether Crystal, Glowstone Bricks, Reinforced Obsidian, Alchemical Glass

## Configuration

Edit `config/alchemod.properties` to customize:

```properties
# API Configuration
openrouter_api_key=YOUR_KEY_HERE

# Model Selection (use any model from https://openrouter.ai)
builder_model=openai/gpt-5.4-mini
creator_model=google/gemini-2.5-flash-lite-preview-05-20
forge_model=openai/gpt-4o-mini
infuser_model=google/gemini-2.5-flash-lite-preview-05-20

# Token Limits
builder_max_tokens=3500
creator_max_tokens_scripted=1200
creator_max_tokens_plain=400

# Timeout (seconds)
builder_timeout_seconds=60
creator_timeout_seconds=40
forge_timeout_seconds=10
infuser_timeout_seconds=40
```

## Architecture

### NBT-Driven Item System
- **Design**: Items store all identity data (name, effects, rarity, behavior) in NBT components
- **Benefit**: Items survive world reloads and server restarts
- **Persistence**: NBT data is automatically saved with the world
- **No Exhaustion**: Unlimited items can be created without slot limits

### Asynchronous Block Placement
- **Design**: Block placement happens in a queue, draining 256 blocks per tick
- **Benefit**: No server freezes, even when building large structures
- **Progress Tracking**: Real-time progress updates as blocks are placed
- **Error Handling**: Failed placements are logged but don't interrupt the build

### Per-Block Cooldowns
- **Design**: 30-second cooldown after prompts to prevent API spam
- **Auto-Reset**: Block state automatically resets 3 seconds after completion
- **User Feedback**: Clear UI indicators for block state and cooldown status

## Troubleshooting

### "API key is MISSING"
- Ensure your `OPENROUTER_API_KEY` environment variable is set, or
- Paste your key into `config/alchemod.properties`
- Restart Minecraft after environment variable changes

### Block placeholders appear instead of real blocks
- Ensure the block IDs are valid in the current Minecraft version
- Check console logs for detailed error messages
- Try with a simpler block palette (more common blocks)

### Creation is slow or times out
- Increase timeout values in `config/alchemod.properties`
- Try a faster model (e.g., `google/gemini-2.5-flash-lite-preview-05-20`)
- Reduce `max_tokens` if you're hitting rate limits

### Out of Memory errors
- Increase JVM heap size in launcher settings
- Default is 2GB; try 4GB or more for large worlds
- Reduce concurrent builds to one at a time

## Performance Tips

1. **Use Async Block Placement**: All block placements happen asynchronously (256 blocks/tick)
2. **Batch Creations**: Create multiple items back-to-back; the system queues them efficiently
3. **Monitor Token Usage**: Larger prompts use more tokens; check your OpenRouter dashboard
4. **Use Appropriate Models**: Faster models cost less; faster models have less cost
5. **Set Realistic Timeouts**: Too-short timeouts cause failures; too-long timeouts waste time

## Development

### Building from Source
```bash
git clone https://github.com/your-repo/alchem-mod
cd alchem-mod
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Project Structure
- `src/main/java/com/alchemod/` — Core mod logic
  - `ai/` — OpenRouter API integration and config
  - `block/` — Block entity logic (Creator, Forge, Builder, etc.)
  - `screen/` — GUI screens and handlers
  - `item/` — Custom item classes
  - `builder/` — Building code generation and execution
  - `creator/` — Item creation and registry
  - `script/` — Item behavior scripting engine
- `src/client/java/` — Client-side rendering and screens
- `src/test/java/` — Unit and integration tests

## Known Limitations

- **Cooldowns**: 30-second cooldown between creations on the same block
- **Max Blocks**: World Builder limited to 24,576 blocks per prompt
- **Sphere Radius**: Max 16 blocks for sphere generation
- **Script Size**: Item behavior scripts limited to 20 lines
- **Model Constraints**: Depends on selected OpenRouter model availability

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see LICENSE file for details.

## Credits

- Built for Minecraft 1.21.x using Fabric
- AI integrations powered by [OpenRouter](https://openrouter.ai)
- Uses [Rhino](https://mozilla.org/rhino) JavaScript engine for item behavior scripting
- NBT component system based on Minecraft's data component architecture

## Support

- **Issues**: Report bugs on GitHub Issues
- **Discussions**: Ask questions in GitHub Discussions
- **Documentation**: Check the wiki for detailed guides

---

**Disclaimer**: This mod requires an OpenRouter API key, which may incur costs for API usage. Please review OpenRouter's pricing before using this mod extensively.
