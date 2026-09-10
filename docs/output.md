# Start the app

**ThemeService** : ThemeService ThemeService@342405a0 subscribed to the theme; it starts at Light
**SpringApplication** : Started application in 0.407 seconds (process running for 0.806)
**AppState** : setTheme: Light -> Light (unchanged; no listener will fire)
**SettingsService**  : Settings restored from ...\AppData\Roaming\DataBlaster\settings.properties
**ThemeService** : applyTo: theming new root BorderPane@7510d575 as Light
**ThemeService** : remember: now tracking root BorderPane@7510d575 (1 root(s) tracked)

## Open Preferences

**MainController** : Edit > Preferences selected
**DialogService** : showModal: loading /fxml/preferences.fxml

**GeneralTabController** : General tab GeneralTabController@7cbd4c75 initializing; AppState theme is Light

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: subscribed weakly to AppState.theme

**PreferencesController** : Preferences shell PreferencesController@1ad9e725 initialized with 4 tab(s); its tab controllers have already run

**DialogService** : showModal: loaded root VBox@1923f1ab with controller PreferencesController@1ad9e725

**ThemeService** : applyTo: theming new root VBox@1923f1ab as Light
**ThemeService** : remember: now tracking root VBox@1923f1ab (2 root(s) tracked)
**DialogService** : showModal: showing Preferences (modal; blocks until closed)

## Switch to Dark

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: choice box moved Light -> Dark

**AppState** : setTheme: Light -> Dark
**ThemeService** : reapplyToAll: theme is now Dark; 2 root(s) tracked
**ThemeService** : reapplyToAll: restyling root BorderPane@7510d575 as Dark
**ThemeService** : reapplyToAll: restyling root VBox@1923f1ab as Dark
**ThemeService** : reapplyToAll: restyled 2 root(s), dropped 0 collected since the last change
**SettingsService** : scheduleWrite: queued a write; theme=Dark

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: AppState theme is Dark; writing it back to the choice box (already showing it, so this fires nothing)

## Switch to Light

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: choice box moved Dark -> Light

**AppState** : setTheme: Dark -> Light
**ThemeService** : reapplyToAll: theme is now Light; 2 root(s) tracked
**ThemeService** : reapplyToAll: restyling root BorderPane@7510d575 as Light
**ThemeService** : reapplyToAll: restyling root VBox@1923f1ab as Light
**ThemeService** : reapplyToAll: restyled 2 root(s), dropped 0 collected since the last change
**SettingsService** : scheduleWrite: queued a write; theme=Light

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: AppState theme is Light; writing it back to the choice box (already showing it, so this fires nothing)

## Close Preferences Dialog

**PreferencesController** : Preferences shell PreferencesController@1ad9e725: Close pressed
**DialogService** : showModal: Preferences closed; root VBox@1923f1ab is now discarded
**MainController** : Preferences dismissed; back in the shell

## Reopen Preferences Dialog

**MainController** : Edit > Preferences selected
**DialogService** : showModal: loading /fxml/preferences.fxml

**GeneralTabController** : General tab GeneralTabController@48beda2c initializing; AppState theme is Light

**GeneralTabController** : General tab GeneralTabController@48beda2c: subscribed weakly to AppState.theme

**PreferencesController** : Preferences shell PreferencesController@576c33b4 initialized with 4 tab(s); its tab controllers have already run

**DialogService** : showModal: loaded root VBox@6474bde0 with controller PreferencesController@576c33b4

**ThemeService** : applyTo: theming new root VBox@6474bde0 as Light
**ThemeService** : remember: now tracking root VBox@6474bde0 (3 root(s) tracked)
**DialogService** : showModal: showing Preferences (modal; blocks until closed)

## Switch to Dark

**GeneralTabController** : General tab GeneralTabController@48beda2c: choice box moved Light -> Dark

**AppState** : setTheme: Light -> Dark
**ThemeService** : reapplyToAll: theme is now Dark; 3 root(s) tracked
**ThemeService** : reapplyToAll: restyling root BorderPane@7510d575 as Dark
**ThemeService** : reapplyToAll: restyling root VBox@1923f1ab as Dark
**ThemeService** : reapplyToAll: restyling root VBox@6474bde0 as Dark
**ThemeService** : reapplyToAll: restyled 3 root(s), dropped 0 collected since the last change
**SettingsService** : scheduleWrite: queued a write; theme=Dark

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: AppState theme is Dark; writing it back to the choice box

## Switch to Light

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: choice box moved Light -> Dark

**AppState** : setTheme: Dark -> Dark (unchanged; no listener will fire)

**GeneralTabController** : General tab GeneralTabController@48beda2c: AppState theme is Dark; writing it back to the choice box (already showing it, so this fires nothing)

**GeneralTabController** : General tab GeneralTabController@48beda2c: choice box moved Dark -> Light

**AppState** : setTheme: Dark -> Light
**ThemeService** : reapplyToAll: theme is now Light; 3 root(s) tracked
**ThemeService** : reapplyToAll: restyling root BorderPane@7510d575 as Light
**ThemeService** : reapplyToAll: restyling root VBox@1923f1ab as Light
**ThemeService** : reapplyToAll: restyling root VBox@6474bde0 as Light
**ThemeService** : reapplyToAll: restyled 3 root(s), dropped 0 collected since the last change
**SettingsService** : scheduleWrite: queued a write; theme=Light

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: AppState theme is Light; writing it back to the choice box

**GeneralTabController** : General tab GeneralTabController@7cbd4c75: choice box moved Dark -> Light 

**AppState** : setTheme: Light -> Light (unchanged; no listener will fire)

**GeneralTabController** : General tab GeneralTabController@48beda2c: AppState theme is Light; writing it back to the choice box (already showing it, so this fires nothing)

## Close Preferences Dialog

**PreferencesController**          : Preferences shell PreferencesController@576c33b4: Close pressed
**DialogService**   : showModal: Preferences closed; root VBox@6474bde0 is now discarded
**MainController**        : Preferences dismissed; back in the shell
