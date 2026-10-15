# Lazarus Moodles

A Forge 1.20.1 HUD mod for Project Lazarus. Each player has one three-stage fear moodle (Tense → Fearful → Panicked), plus independent Pain, Exhausted, and Relieved moodles.

The fear moodle rises through its stages as nearby monsters, darkness, low health, and damage increase pressure. Only one fear stage appears at a time. Pain is a separate moodle derived from current health: level 1 whenever health is below maximum, level 2 at 50% health or less, and level 3 at 25% or less. Exhausted gains one level per 1,000 blocks traveled on foot (walking, sprinting, or crouching), up to level 5. It never decays and resets only after completing sleep in a bed; leaving the bed early does not reset it. Vehicles, swimming, and teleports do not add distance. Relieved appears after escaping nearby monsters. Moodles are informative and do not add combat penalties. Fear, exhaustion, and relief persist with the player and synchronize from the server to that player's HUD; Pain is recalculated from health. Existing saves migrate their strongest active fear moodle to the new scale, capped at Panicked. Old sprint-based Exhausted levels restart at zero when upgrading to distance-based Exhausted.

Install the built JAR in `mods` on both the server and every client. The icons are original generated pixel art stored in `assets/lazarusmoodles/textures/gui/moodles.png`. All mod text is English.

Download the ready-to-install JAR from [`dist/lazarusmoodles-1.4.0.jar`](dist/lazarusmoodles-1.4.0.jar), or build it with JDK 17: `gradlew build`.
