Two different things are in play, depending on which text you mean.

**The word "Home" on the tab itself — nothing in this repo styles it.**

[ribbon.css](https://claude.ai/epitaxy/src/main/resources/css/ribbon.css) styles the tab's _background_ ([`.ribbon .tab`](https://claude.ai/epitaxy/src/main/resources/css/ribbon.css:84) and `.ribbon .tab:selected`) but there is no `.tab-label` rule anywhere. So the label falls through to modena's user-agent `.tab-label { -fx-text-fill: -fx-text-base-color; }`:

- **Light theme** — modena's own default `-fx-text-base-color` (a `ladder()` over `-fx-color` that lands on near-black). Nothing in this project touches it.
- **Dark theme** — [`-fx-text-base-color: #ececec`](https://claude.ai/epitaxy/src/main/resources/css/ribbon.css:66), set in the second `.root.theme-dark` block, the one whose comment explains it exists to repaint modena's own chrome that knows nothing about the `-jfx-*` tokens.

The class that selects between them is applied by [ThemeService](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ui/ThemeService.java:120), which adds `theme-dark`/`theme-light` (from [Theme](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/model/Theme.java:21)) to the scene root.

**The text on the Home tab's content — the ribbon groups — is explicitly tokenised.** Each role has its own rule and its own token: `.ribbon-button` → `-jfx-text`, `.ribbon-group-title` and `.ribbon-slider-label` → `-jfx-text-secondary`, `.ribbon-readout` and `.ribbon-inline-button` → `-jfx-text-readout`, `.ribbon-check` → `-jfx-text`. Those tokens are defined once in `.root` (lines 13–15) and redefined in `.root.theme-dark` (lines 43–45).

If you want to control the "Home" label directly rather than inherit modena's, add a rule keyed to a token — e.g. `.ribbon .tab .tab-label { -fx-text-fill: -jfx-text-secondary; }` plus a `:selected` variant using `-jfx-text` — rather than a hex literal; `ThemeContrastTest` parses the token blocks and hardcoded colors outside them will sit outside its contrast checks.

Hypothetically, if when we switched to Dark and the text "Home" was not visible, what would I look for?

Good hypothetical — and this repo has a specific trap that would let exactly that ship green. Here's the triage order I'd use.

## 1. Confirm which text is actually wrong

Toggle to dark and compare the tab label against the menu bar ("File / Edit / Help"). Both are modena-chromed text that ribbon.css never styles directly.

- **Both invisible** → the modena-token block failed to apply. Go to step 2.
- **Menu bar fine, "Home" invisible** → something tab-specific: a nearer looked-up-color definition on `.tab`, or a rule someone added under `.ribbon .tab`. Go to step 4.

## 2. Suspect the second `.root.theme-dark` block first

[ribbon.css:57-67](https://claude.ai/epitaxy/src/main/resources/css/ribbon.css:57) is the _only_ thing that makes that label legible in dark. The `-jfx-*` tokens in the first block don't touch it at all — there is no `.tab-label` rule anywhere in the file. If `-fx-text-base-color` (line 66) and `-fx-base` (line 58) are gone, mistyped, or overridden, the label keeps its light-theme near-black on `-jfx-surface-header` (#2b2b2d) or `-jfx-surface-raised` (#252526) — invisible, exactly as described.

## 3. Know that the test suite would not have caught it

This is the part worth internalizing. [ThemeContrastTest:40](https://claude.ai/epitaxy/src/test/java/com/culberth/tools/datablaster/ui/ThemeContrastTest.java:40) parses only `-jfx-[a-z-]+: #rrggbb;`, so `-fx-text-base-color` is invisible to it — you can delete line 66 and every test still passes.

Worse, line 58 asserts contrast for `-jfx-text` on `-jfx-surface-header`, described as _"tab labels on the ribbon header"_ — but no rule in ribbon.css ever sets a tab label to `-jfx-text`. The test is asserting a pairing the stylesheet doesn't implement. So "contrast tests are green" is not evidence about this label; don't let it end the investigation.

## 4. Then the usual CSS-resolution suspects

- **Did the class land?** `scene.getRoot().getStyleClass()` should contain `theme-dark`. [ThemeService.applyTo](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ui/ThemeService.java:88) only themes roots that went through [ViewLoader](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ViewLoader.java:84) — fine for the main window, but check it rather than assume.
- **Console for `CSS Error parsing`** — JavaFX logs and skips malformed declarations silently as far as the UI goes. One bad line above the block can drop what follows it.
- **A nearer lookup wins.** Looked-up colours resolve from the node outward, so a `-fx-text-base-color` or `-fx-color` declared on any ancestor between the root and the label beats the root's. Modena does set colours on `.tab`.
- **A literal someone added.** `grep -n "tab-label\|-fx-text-fill: #" src/main/resources/css/ribbon.css` — a hex literal outside the token blocks is the failure mode CLAUDE.md warns about, and it wouldn't follow the theme.

## 5. Timing — was it ever right?

If dark looks correct at startup but the label goes wrong only after toggling (or only in a dialog), that's not a colour problem at all — it's [reapplyToAll](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ui/ThemeService.java:94) and the weak root tracking. The Javadoc there records a `ChangeListener` that fired once and then stopped; a regression to that shape would look like "the theme changed once and got stuck."

**The durable fix**, once found, is to give the label its own rule keyed to a token — `.ribbon .tab .tab-label { -fx-text-fill: -jfx-text-secondary; }` plus a `:selected` variant on `-jfx-text` — which both removes the dependence on modena's cascade and makes ThemeContrastTest line 58 describe something that's actually true.