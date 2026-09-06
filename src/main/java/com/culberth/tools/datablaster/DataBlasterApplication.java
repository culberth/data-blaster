package com.culberth.tools.datablaster;

import com.culberth.tools.datablaster.ui.StageRegistry;
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
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class DataBlasterApplication extends Application
{

    private static final System.Logger LOG = System.getLogger(DataBlasterApplication.class.getName());

    private ConfigurableApplicationContext springContext;

    /**
     * Boots Spring before any window exists.
     *
     * <p>
     * <strong>No try/catch here any more.</strong> This method used to catch a startup failure, check whether its most
     * specific cause was a port conflict, and retry without the web layer — because an embedded Tomcat that could not
     * bind its port would otherwise abort the launch and write its only diagnostic to a console the windowed build does
     * not have. There is no embedded server now, so there is no port to conflict over and nothing left that a retry
     * could fix. A failure here is a real bug and should propagate.
     */
    @Override
    public void init()
    {
        springContext = new SpringApplicationBuilder(AppConfig.class).headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage)
    {
        try
        {
            // Registered before the shell loads so that any bean — not just MainController — can
            // obtain a window owner.
            // Recorded here rather than probed via Platform.isFxApplicationThread(), which would
            // initialise the toolkit from anywhere AppState's guard runs — including plain tests.
            com.culberth.tools.datablaster.model.AppState.markFxApplicationThread();

            springContext.getBean(StageRegistry.class).setPrimaryStage(primaryStage);

            // Before the shell loads, not after: the ribbon controls read their initial values
            // from AppState in their initialize() methods, so restoring later would leave the
            // sliders showing defaults while the state said otherwise. Reading here rather than in
            // a Spring @PostConstruct also keeps a hand-edited settings file from being able to
            // fail context refresh, which is the launch NFR1 exists to protect.
            springContext.getBean(com.culberth.tools.datablaster.model.SettingsService.class)
                    .bind(springContext.getBean(com.culberth.tools.datablaster.model.AppState.class));

            ViewLoader viewLoader = springContext.getBean(ViewLoader.class);
            Parent root = viewLoader.loadParent("/fxml/main.fxml");
            Scene scene = viewLoader.newScene(root, 1000, 650);

            primaryStage.setTitle("Data Blaster");
            primaryStage.getIcons().add(appIcon());
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(640);
            primaryStage.setMinHeight(400);
            primaryStage.show();
        }
        catch (Exception e)
        {
            // JavaFX only calls stop() when start() completed, so without this the already-booted
            // Spring context would be left open with no window ever appearing — and its shutdown
            // hooks, including the settings flush, would never run.
            LOG.log(System.Logger.Level.ERROR, "Failed to start the Data Blaster UI", e);
            reportFatal(e);
            closeSpringContext();
            javafx.application.Platform.exit();
        }
    }

    /**
     * The window/taskbar icon, drawn rather than loaded so the app carries no binary asset. Without it the app shows
     * the generic Java icon and is indistinguishable in Alt-Tab from every other Java process.
     */
    private static WritableImage appIcon()
    {
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

    private void reportFatal(Throwable cause)
    {
        try
        {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Data Blaster");
            alert.setHeaderText("Data Blaster could not start.");
            alert.setContentText(String.valueOf(cause.getMessage()));
            alert.showAndWait();
        }
        catch (RuntimeException ignored)
        {
            // A failure this early can leave the toolkit unable to show a dialog; the log above
            // is then the only record, and suppressing this keeps the shutdown path intact.
        }
    }

    @Override
    public void stop()
    {
        closeSpringContext();
    }

    private void closeSpringContext()
    {
        if (springContext != null)
        {
            springContext.close();
            springContext = null;
        }
    }

    public static void main(String[] args)
    {
        launch(args);
    }
}
