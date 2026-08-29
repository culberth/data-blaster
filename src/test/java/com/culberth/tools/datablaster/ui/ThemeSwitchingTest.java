package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Theme;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The bar this feature had to clear.
 *
 * <p>Theme switching was removed from this project once already rather than shipped, because it
 * persisted a value nothing honoured. The requirement written for its return was that it changes
 * what the user sees <em>in every window that is already open</em> — not just the next one created.
 * That is the assertion below, and it is the one that would have failed the first time.
 */
@SpringBootTest
class ThemeSwitchingTest {

    @Autowired
    private ViewLoader viewLoader;

    @Autowired
    private AppState appState;

    @BeforeAll
    static void startToolkit() {
        HeadlessToolkit.start();
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    @AfterEach
    void resetSharedState() {
        HeadlessToolkit.onFxThread(() -> appState.setTheme(Theme.LIGHT));
    }

    private static boolean hasTheme(Parent root, Theme theme) {
        return root.getStyleClass().contains(theme.styleClass());
    }

    @Test
    @DisplayName("a scene is born with the current theme")
    void aSceneIsBornWithTheCurrentTheme() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setTheme(Theme.DARK);

            Scene scene = viewLoader.newScene(viewLoader.loadParent("/fxml/preferences.fxml"));

            assertTrue(hasTheme(scene.getRoot(), Theme.DARK),
                    "a dialog opened after the theme changed must not flash the old one");
            assertFalse(hasTheme(scene.getRoot(), Theme.LIGHT));
        });
    }

    /**
     * The whole point. A window that is already on screen has to change; applying the theme only at
     * scene creation is the failure mode that got this feature pulled before.
     */
    @Test
    @DisplayName("switching restyles a window that is already open")
    void switchingRestylesAWindowThatIsAlreadyOpen() {
        HeadlessToolkit.onFxThread(() -> {
            Stage stage = new Stage();
            stage.setScene(viewLoader.newScene(viewLoader.loadParent("/fxml/main.fxml")));
            stage.show();
            try {
                assertTrue(hasTheme(stage.getScene().getRoot(), Theme.LIGHT),
                        "should start on the light theme");

                appState.setTheme(Theme.DARK);

                assertTrue(hasTheme(stage.getScene().getRoot(), Theme.DARK),
                        "the open window should have been restyled without being rebuilt");
                assertFalse(hasTheme(stage.getScene().getRoot(), Theme.LIGHT),
                        "the previous theme's class must be removed, not merely outranked");

                appState.setTheme(Theme.LIGHT);
                assertTrue(hasTheme(stage.getScene().getRoot(), Theme.LIGHT),
                        "and back again");
            } finally {
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("every open window is restyled, not just the last one")
    void everyOpenWindowIsRestyledNotJustTheLastOne() {
        HeadlessToolkit.onFxThread(() -> {
            Stage shell = new Stage();
            shell.setScene(viewLoader.newScene(viewLoader.loadParent("/fxml/main.fxml")));
            shell.show();
            Stage dialog = new Stage();
            dialog.setScene(viewLoader.newScene(viewLoader.loadParent("/fxml/preferences.fxml")));
            dialog.show();
            try {
                appState.setTheme(Theme.DARK);

                assertTrue(hasTheme(shell.getScene().getRoot(), Theme.DARK),
                        "the shell should follow even while a dialog is up");
                assertTrue(hasTheme(dialog.getScene().getRoot(), Theme.DARK),
                        "the dialog should follow too");
            } finally {
                dialog.close();
                shell.close();
            }
        });
    }

    /**
     * The style class is only half of it — the tokens it selects have to reach the rules. This
     * checks the stylesheet is attached at all, which is what makes {@code .root.theme-dark}
     * resolve to anything.
     */
    @Test
    @DisplayName("the themed scene carries the stylesheet the theme lives in")
    void theThemedSceneCarriesTheStylesheetTheThemeLivesIn() {
        HeadlessToolkit.onFxThread(() -> {
            Scene scene = viewLoader.newScene(viewLoader.loadParent("/fxml/preferences.fxml"));

            assertEquals(1, scene.getStylesheets().size(), "exactly one stylesheet is expected");
            assertTrue(scene.getStylesheets().get(0).endsWith("ribbon.css"),
                    "got " + scene.getStylesheets());
        });
    }
}
