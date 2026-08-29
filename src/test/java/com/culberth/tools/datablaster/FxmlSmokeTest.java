package com.culberth.tools.datablaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.culberth.tools.datablaster.model.AppState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Loads every FXML file through the real {@link ViewLoader} — the same Spring-backed controller
 * factory production uses, not a stub.
 *
 * <p><strong>What this catches that nothing else does.</strong> A renamed {@code onAction} handler,
 * a dropped {@code fx:id}, a controller moved between packages, and a controller that lost its
 * {@code @Component}: each of these currently fails at run time, with the window already open and
 * the stack trace going to a console the windowed build does not have. Each of them fails here
 * instead, because every {@code initialize()} in this project dereferences its injected fields —
 * so an unbound field is an exception during {@code load()}, not a control that quietly does
 * nothing.
 *
 * <p><strong>Why it needs a toolkit and why that is still headless.</strong> FXML cannot be
 * verified without instantiating controls. {@link HeadlessToolkit} runs those on Monocle's
 * software-only Glass platform, so this needs no display and CI's Linux runner proves it. The rest
 * of the suite initialises no toolkit at all and should stay that way.
 */
@SpringBootTest(properties = "spring.main.web-application-type=none")
class FxmlSmokeTest {

    /** The FXML files that exist today, asserted so a glob matching nothing cannot pass quietly. */
    private static final int EXPECTED_FXML_COUNT = 10;

    // Field injection rather than a constructor parameter: Spring's JUnit extension only
    // autowires constructor parameters that carry @Autowired themselves (ApplicationContext types
    // are the one special case), and a parameterized test class makes that noisier than it is
    // worth.
    @Autowired
    private ViewLoader viewLoader;

    @BeforeAll
    static void startToolkit() {
        HeadlessToolkit.start();
        // Exactly what DataBlasterApplication.start() does as its first act on the FX thread. Doing
        // it here is not bookkeeping: it makes AppState's threading guard live while the FXML
        // loads, so an initialize() that writes state off the FX thread fails this suite instead
        // of being waved through by a null.
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    /**
     * Every FXML on the classpath, discovered rather than listed, so a new view or ribbon group is
     * covered the moment it is added instead of when someone remembers to extend this test.
     */
    static List<String> fxmlResources() throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/fxml/**/*.fxml");

        List<String> paths = new ArrayList<>();
        for (Resource resource : found) {
            String url = resource.getURL().toString();
            paths.add(url.substring(url.lastIndexOf("/fxml/")));
        }
        paths.sort(String::compareTo);
        return paths;
    }

    @Test
    @DisplayName("the discovery glob finds every FXML file")
    void theDiscoveryGlobFindsEveryFxmlFile() throws IOException {
        List<String> found = fxmlResources();
        assertEquals(EXPECTED_FXML_COUNT, found.size(),
                "expected " + EXPECTED_FXML_COUNT + " FXML files but found " + found
                        + "; update EXPECTED_FXML_COUNT when adding one");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fxmlResources")
    @DisplayName("loads through the real controller factory")
    void loadsThroughTheRealControllerFactory(String resource) {
        HeadlessToolkit.onFxThread(() -> {
            ViewLoader.LoadedView loaded = viewLoader.load(resource);
            assertNotNull(loaded.root(), resource + " produced no node tree");
            assertNotNull(loaded.controller(),
                    resource + " produced no controller; is fx:controller declared?");
        });
    }

    /**
     * The shell is the one that matters most: it pulls in all three ribbon groups through
     * {@code <fx:include>} and, via {@code MainController.initialize()}, the default content view
     * as well. Loading it exercises the include wiring that loading the groups individually does
     * not.
     */
    @Test
    @DisplayName("the shell loads its ribbon includes and default view")
    void theShellLoadsItsRibbonIncludesAndDefaultView() {
        HeadlessToolkit.onFxThread(() -> {
            ViewLoader.LoadedView shell = viewLoader.load("/fxml/main.fxml");
            assertNotNull(shell.root());
            assertEquals("com.culberth.tools.datablaster.controller.MainController",
                    shell.controller().getClass().getName());
        });
    }
}
