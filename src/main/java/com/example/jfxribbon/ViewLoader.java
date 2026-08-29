package com.example.jfxribbon;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import com.example.jfxribbon.ui.ThemeService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Loads FXML views using Spring as the controller factory, so view controllers can
 * receive dependencies (services, repositories, etc.) from the application context.
 *
 * <p>Controllers resolved this way must be prototype-scoped: the loader binds the returned
 * instance's {@code @FXML} fields to the node tree it just built, so a singleton controller
 * would end up shared between — and pointing at only the newest of — several node trees.
 */
@Component
public class ViewLoader {

    /** Stylesheet applied to every scene this loader creates, so dialogs match the main window. */
    public static final String STYLESHEET = "/css/ribbon.css";

    private final ApplicationContext context;
    private final ThemeService themeService;

    public ViewLoader(ApplicationContext context, ThemeService themeService) {
        this.context = context;
        this.themeService = themeService;
    }

    /** Loads {@code fxmlClasspathResource}, returning both the node tree and its controller. */
    public LoadedView load(String fxmlClasspathResource) throws IOException {
        java.net.URL location = getClass().getResource(fxmlClasspathResource);
        if (location == null) {
            throw new IOException("FXML resource not found on the classpath: " + fxmlClasspathResource);
        }
        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(context::getBean);
        Parent root = loader.load();
        return new LoadedView(root, loader.getController());
    }

    public Parent loadParent(String fxmlClasspathResource) throws IOException {
        return load(fxmlClasspathResource).root();
    }

    /** Creates a scene with the application stylesheet already applied. */
    public Scene newScene(Parent root) {
        Scene scene = new Scene(root);
        applyStylesheet(scene);
        return scene;
    }

    /** Creates a sized scene with the application stylesheet already applied. */
    public Scene newScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        applyStylesheet(scene);
        return scene;
    }

    /**
     * Applies the app stylesheet and current theme to a node tree that carries its own styling
     * rather than sitting in a scene this class made — an {@code Alert}'s {@code DialogPane} is the
     * only such case today. Without it, an error dialog raised while the dark theme is on arrives
     * in default light colours.
     */
    public void style(Parent root) {
        root.getStylesheets().add(stylesheetUrl());
        themeService.applyTo(root);
    }

    private void applyStylesheet(Scene scene) {
        scene.getStylesheets().add(stylesheetUrl());
        // Every scene is born with the current theme, so a dialog opened after a theme change does
        // not flash the previous one before the change listener catches up.
        themeService.applyTo(scene.getRoot());
    }

    private static String stylesheetUrl() {
        java.net.URL css = ViewLoader.class.getResource(STYLESHEET);
        if (css == null) {
            throw new IllegalStateException("Stylesheet not found on the classpath: " + STYLESHEET);
        }
        return css.toExternalForm();
    }

    /** A freshly loaded node tree together with the controller instance bound to it. */
    public record LoadedView(Parent root, Object controller) {
    }
}
