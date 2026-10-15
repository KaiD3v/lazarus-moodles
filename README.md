# Lazarus Moodles

A Forge 1.20.1 HUD mod for Project Lazarus. Each player has one three-stage fear moodle (Tense → Fearful → Panicked), plus independent Exhausted and Relieved moodles.

The fear moodle rises through its stages as nearby monsters, darkness, low health, and damage increase pressure. Only one fear stage appears at a time; Irritated is currently inactive and reserved for a separate pain moodle. Exhausted responds to sprinting and hunger; Relieved appears after escaping nearby monsters. Moodles are informative and do not add combat penalties. Values persist with the player and synchronize from the server to that player's HUD. Existing saves migrate their strongest active fear moodle to the new scale, capped at Panicked.

Install the built JAR in `mods` on both the server and every client. The icons are original generated pixel art stored in `assets/lazarusmoodles/textures/gui/moodles.png`. All mod text is English.

Download the ready-to-install JAR from [`dist/lazarusmoodles-1.2.0.jar`](dist/lazarusmoodles-1.2.0.jar), or build it with JDK 17: `gradlew build`.
