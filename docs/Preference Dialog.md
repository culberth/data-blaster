I'll trace the theme wiring for the Preferences dialog.

Six pieces, each with a distinct job:

**1. The control the user touches** — [GeneralTabController.java:131](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/controller/preferences/GeneralTabController.java:131), `initTheme()`. A `ChoiceBox<Theme>` populated from `Theme.values()`, seeded from `AppState`, writing back on change, and re-reading `AppState` through a `WeakChangeListener` so it stays in step if the value moves elsewhere (Reset, or a future surface). Its markup is [general-tab.fxml:37](https://claude.ai/epitaxy/src/main/resources/fxml/preferences/general-tab.fxml:37).

**2. The value itself** — `AppState.themeProperty()`. The single channel; the ChoiceBox owns nothing.

**3. What actually repaints the dialog** — [ThemeService.java](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ui/ThemeService.java). It listens on `appState.themeProperty()` and swaps a style class (`theme-light` / `theme-dark`) on every root it has themed. Two details there are load-bearing and documented as such: it's an **`InvalidationListener`, not a `ChangeListener`** (as a ChangeListener it fired once and then silently stopped), and it tracks roots in its **own weak list** rather than walking `Window.getWindows()` (that list only carries _showing_ windows, so a built-but-unshown scene is missed).

**4. How the Preferences dialog gets themed at all** — [ViewLoader.java:83](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ViewLoader.java:83), `applyStylesheet()`. Every scene ViewLoader creates gets `ribbon.css` plus `themeService.applyTo(root)`, so a dialog opened _after_ a theme change is born correct rather than flashing the old one. `DialogService.showModal` builds Preferences through that path.

**5. The colors** — [ribbon.css:36](https://claude.ai/epitaxy/src/main/resources/css/ribbon.css:36), the `.root.theme-dark` block. Dark is only token overrides; `.root.theme-dark` outranks `.root` on specificity, so redefining tokens recolors the whole stylesheet with no reload. This is why hardcoding a hex outside a token block breaks theming.

**6. Persistence** — `Theme.storedName()` / `fromStoredName()` ([Theme.java](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/model/Theme.java)) plus the `theme` key in `SettingsStore`, restored before subscribe in `SettingsService.bind()`.

One adjacent trap worth knowing: an `Alert` built inline gets neither owner nor stylesheet and renders stock light under the dark theme — which is why the Log and SOAP tabs confirm through `DialogService.confirm`, which calls `viewLoader.style(alert.getDialogPane())` explicitly ([DialogService.java:88](https://claude.ai/epitaxy/src/main/java/com/culberth/tools/datablaster/ui/DialogService.java:88)).

So: **`GeneralTabController.initTheme()` is the setting; `ThemeService` is what makes it visible.**