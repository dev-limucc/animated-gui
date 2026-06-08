# Working in this repo

This repo doubles as a **reusable GUI style library**. If the task is to build or restyle a Fabric mod GUI in
the **Animated Limucc UI** style (flat dark Sodium-inspired panels + custom framerate-independent animations),
read **[`STYLE.md`](STYLE.md)** first — it's the canonical, copy-paste-ready guide and is self-contained.

Fast path to reproduce the style in another mod, copy these self-contained files:

- `src/client/java/dev/limucc/animatedgui/client/anim/Easing.java` — named easing curves
- `src/client/java/dev/limucc/animatedgui/client/anim/Tween.java` — retargetable interpolation (every animation is one)
- `src/client/java/dev/limucc/animatedgui/client/gui/widget/FlatButton.java` — flat hand-drawn button
- `src/client/java/dev/limucc/animatedgui/client/gui/widget/ToggleSwitch.java` — animated pill switch

…then follow the screen skeleton and the do/don't rules in `STYLE.md`. The full working example is
`src/client/java/dev/limucc/animatedgui/client/gui/AnimatedGuiScreen.java`.

Non-negotiables of the style: no vanilla `Button`/`Checkbox` widgets — draw and hit-test by hand in
`extractRenderState(...)` / `mouseClicked(...)`; every animated value is a `Tween` driven by `Util.getMillis()`;
accent blue is `0xFF3A6EA5`. See `STYLE.md` for the full palette and rationale.

Target: Minecraft **26.1.2**, Fabric, client-side, JDK 25. Built by **dev-limucc**.
