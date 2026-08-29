package com.example.jfxribbon;

import com.example.jfxribbon.ui.StageRegistry;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.net.BindException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;

public class JFXRibbonApplication extends Application {

    private static final System.Logger LOG = System.getLogger(JFXRibbonApplication.class.getName());

    private ConfigurableApplicationContext springContext;

    /** True when the HTTP layer was dropped because its port was taken; surfaced in the title. */
    private boolean httpLayerDisabled;

    @Override
    public void init() {
        String[] args = getParameters().getRaw().toArray(new String[0]);
        try {
            springContext = startSpring(args);
        } catch (RuntimeException e) {
            // Retry ONLY for a port conflict. The HTTP layer is a companion feature the desktop UI
            // does not need, and aborting launch because some other process holds the port would
            // leave the user with a windowed build that never opens and writes its only diagnostic
            // to a console that does not exist. Any other startup failure is a real bug: rethrow it
            // rather than mislabelling it as a web-server problem and booting the context twice.
            if (!isPortConflict(e)) {
                throw e;
            }
            springContext = startSpring(args, "--spring.main.web-application-type=none");
            httpLayerDisabled = true;

            // Logged after the replacement context is up, and that ordering is the whole point.
            // Spring Boot tears its logging system down when a context fails, so anything logged
            // between the failure and the next context starting is written to a Logback that has
            // been stopped and is silently discarded. This warning used to sit above the retry and
            // never appeared anywhere — in the console build that exists specifically to show
            // start-up diagnostics.
            LOG.log(System.Logger.Level.WARNING,
                    "Port already in use; started without the HTTP layer", e);
        }
    }

    /** True if {@code e} was caused by the web server failing to bind its port. */
    private static boolean isPortConflict(Throwable e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        return cause instanceof PortInUseException || cause instanceof BindException;
    }

    private ConfigurableApplicationContext startSpring(String[] args, String... extraArgs) {
        String[] all = new String[args.length + extraArgs.length];
        System.arraycopy(args, 0, all, 0, args.length);
        System.arraycopy(extraArgs, 0, all, args.length, extraArgs.length);
        return new SpringApplicationBuilder(AppConfig.class)
                .headless(false)
                .run(all);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Registered before the shell loads so that any bean — not just MainController — can
            // obtain a window owner.
            // Recorded here rather than probed via Platform.isFxApplicationThread(), which would
            // initialise the toolkit from anywhere AppState's guard runs — including plain tests.
            com.example.jfxribbon.model.AppState.markFxApplicationThread();

            springContext.getBean(StageRegistry.class).setPrimaryStage(primaryStage);

            // Before the shell loads, not after: the ribbon controls read their initial values
            // from AppState in their initialize() methods, so restoring later would leave the
            // sliders showing defaults while the state said otherwise. Reading here rather than in
            // a Spring @PostConstruct also keeps a hand-edited settings file from being able to
            // fail context refresh, which is the launch NFR1 exists to protect.
            springContext.getBean(com.example.jfxribbon.model.SettingsService.class)
                    .bind(springContext.getBean(com.example.jfxribbon.model.AppState.class));

            ViewLoader viewLoader = springContext.getBean(ViewLoader.class);
            Parent root = viewLoader.loadParent("/fxml/main.fxml");
            Scene scene = viewLoader.newScene(root, 1000, 650);

            // The fallback above is otherwise invisible in the windowed build, which has no
            // console for the warning; say so where the user can actually see it.
            primaryStage.setTitle(httpLayerDisabled ? "JFXRibbon (HTTP layer disabled)" : "JFXRibbon");
            primaryStage.getIcons().add(appIcon());
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(640);
            primaryStage.setMinHeight(400);
            primaryStage.show();
        } catch (Exception e) {
            // JavaFX only calls stop() when start() completed, so without this the already-booted
            // Spring context and its non-daemon Tomcat threads would keep the JVM alive with no
            // window ever appearing.
            LOG.log(System.Logger.Level.ERROR, "Failed to start the JFXRibbon UI", e);
            reportFatal(e);
            closeSpringContext();
            javafx.application.Platform.exit();
        }
    }

    /**
     * The window/taskbar icon, drawn rather than loaded so the app carries no binary asset.
     * Without it the app shows the generic Java icon and is indistinguishable in Alt-Tab from
     * every other Java process.
     */
    private static WritableImage appIcon() {
        int size = 64;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0078d4"));
        g.fillRoundRect(4, 4, size - 8, size - 8, 12, 12);
        // Three bars echoing the ribbon: one wide tab strip over two content rows.
        g.setFill(Color.WHITE);
        g.fillRoundRect(14, 17, size - 28, 8, 3, 3);
        g.fillRoundRect(14, 31, size - 28, 6, 3, 3);
        g.fillRoundRect(14, 43, (size - 28) * 0.6, 6, 3, 3);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private void reportFatal(Throwable cause) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("JFXRibbon");
            alert.setHeaderText("JFXRibbon could not start.");
            alert.setContentText(String.valueOf(cause.getMessage()));
            alert.showAndWait();
        } catch (RuntimeException ignored) {
            // A failure this early can leave the toolkit unable to show a dialog; the log above
            // is then the only record, and suppressing this keeps the shutdown path intact.
        }
    }

    @Override
    public void stop() {
        closeSpringContext();
    }

    private void closeSpringContext() {
        if (springContext != null) {
            springContext.close();
            springContext = null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
