# Lazarus Moodles

A Forge 1.20.1 HUD mod for Project Lazarus. Each player has one three-stage fear moodle (Tense → Fearful → Panicked), plus independent Pain, Exhausted, and Relieved moodles.

The fear moodle rises through its stages as nearby monsters, darkness, low health, and damage increase pressure. Only one fear stage appears at a time. Pain is a separate moodle derived from current health: level 1 whenever health is below maximum, level 2 at 50% health or less, and level 3 at 25% or less. Exhausted gains one level per 1,000 blocks traveled on foot (walking, sprinting, or crouching), up to level 5. It never decays and resets only after completing sleep in a bed; leaving the bed early does not reset it. Vehicles, swimming, and teleports do not add distance. Relieved appears after escaping nearby monsters. Fear, exhaustion, and relief persist with the player and synchronize from the server to that player's HUD; Pain is recalculated from health. Existing saves migrate their strongest active fear moodle to the new scale, capped at Panicked. Old sprint-based Exhausted levels restart at zero when upgrading to distance-based Exhausted.

## Gameplay effects

- Fear adds food and Legendary Survival Overhaul thirst exhaustion each second. Tense, Fearful, and Panicked add 0.02/0.05/0.10 food exhaustion and 0.012/0.03/0.06 thirst exhaustion respectively. Aiming with TaCZ or vanilla ranged weapons causes subtle aim drift, which grows after sprinting or taking damage. Fearful and Panicked play a quiet original breathing sound.
- Pain reduces melee attack speed by 4/8/12% and PeakStamina regeneration by 5/10/15%. It also reduces forward input while climbing ladders or vines by 8/16/24%.
- Exhausted reduces PeakStamina regeneration by 5% per level and increases sprint stamina cost by 6% per level. Movement speed falls by 3% at level 4 and 6% at level 5.
- Relieved accelerates fear recovery (9 points per second instead of 5) and reduces aim drift by 40%. Its effects end when the moodle disappears.

Survival and attribute penalties are applied by the server and do not apply in Creative or Spectator mode. TaCZ, PeakStamina, and Legendary Survival Overhaul integrations are optional; their respective effects activate when those mods are present.

## Test commands

Operators can force moodle levels for testing without changing health or distance traveled. The override affects the HUD and gameplay effects until cleared, the player dies, or the player disconnects. The underlying natural moodle continues updating.

- `/lm fear 0..3` — 1 Tense, 2 Fearful, 3 Panicked.
- `/lm pain 0..3` — force Pain independently of health.
- `/lm exhausted 0..5` — force Exhausted independently of travel distance.
- `/lm relieved 0..4` — force Relieved.
- `/lm status` — show current effective levels and identify forced values.
- `/lm clear` — remove all overrides; `/lm clear fear`, `pain`, `exhausted`, or `relieved` clears one.

`/lazarusmoodles` is an alias for `/lm`. Level 0 hides a moodle until the override is cleared. Commands require permission level 2 (cheats enabled in single player).

Install the built JAR in `mods` on both the server and every client. The icons are original generated pixel art stored in `assets/lazarusmoodles/textures/gui/moodles.png`. All mod text is English.

Download the ready-to-install JAR from [`dist/lazarusmoodles-1.6.0.jar`](dist/lazarusmoodles-1.6.0.jar), or build it with JDK 17: `gradlew build`.
