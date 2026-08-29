package com.culberth.tools.datablaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.ui.DialogService;
import com.culberth.tools.datablaster.ui.StageRegistry;
import com.culberth.tools.datablaster.ui.ViewRegistry;
import com.culberth.tools.datablaster.ui.ViewSwitcher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the real application context and checks the wiring rules that nothing else verifies.
 *
 * <p><strong>A bare {@code @SpringBootTest}, with nothing switched off.</strong> Every
 * context-loading test in this suite used to carry
 * {@code properties = "spring.main.web-application-type=none"}, because the inherited HTTP layer
 * bound port 8080 and a plain context load would fail on any machine already running the
 * application — or on a CI runner executing two jobs on one host. The HTTP layer is gone and the
 * project no longer depends on the web starter, so the context has no server to start and the
 * override has nothing left to suppress.
 *
 * <p>This class starts no JavaFX toolkit. Spring instantiates controllers but never calls their
 * {@code initialize()} methods — that is {@code FXMLLoader}'s job, and it is covered by
 * {@code FxmlSmokeTest}.
 */
@SpringBootTest
class SpringContextTest {

    /** Every FXML-backed controller lives under here, including the {@code ribbon} subpackage. */
    private static final String CONTROLLER_PACKAGE = "com.culberth.tools.datablaster.controller";

    /**
     * The controllers that exist today. Asserted as an exact count so that adding a controller
     * without a scope annotation fails here rather than passing a check that scanned nothing.
     */
    private static final int EXPECTED_CONTROLLER_COUNT = 10;

    private final ConfigurableApplicationContext context;

    SpringContextTest(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Test
    @DisplayName("the application context starts")
    void theApplicationContextStarts() {
        assertTrue(context.isActive(), "context should be active");
        assertTrue(context.isRunning(), "context should be running");
    }

    /**
     * The defect this catches is invisible until two views are open at once: {@code FXMLLoader}
     * binds the returned controller's {@code @FXML} fields to the node tree it has just built, so a
     * singleton controller ends up shared between several trees and pointing at only the newest.
     * The first shell then stops responding, with nothing in the log to say why.
     */
    @Test
    @DisplayName("every FXML controller is prototype-scoped")
    void everyFxmlControllerIsPrototypeScoped() {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();

        List<String> controllers = new ArrayList<>();
        List<String> wronglyScoped = new ArrayList<>();

        for (String name : beanFactory.getBeanDefinitionNames()) {
            Class<?> type = beanFactory.getType(name);
            if (type == null || !type.getName().startsWith(CONTROLLER_PACKAGE + ".")) {
                continue;
            }
            controllers.add(type.getSimpleName());
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            if (!definition.isPrototype()) {
                wronglyScoped.add(type.getSimpleName() + " (" + definition.getScope() + ")");
            }
        }

        // Without this, a renamed package or a broken scan would leave the loop finding nothing
        // and the assertion below passing vacuously.
        assertEquals(EXPECTED_CONTROLLER_COUNT, controllers.size(),
                "expected " + EXPECTED_CONTROLLER_COUNT + " controllers under " + CONTROLLER_PACKAGE
                        + " but found " + controllers
                        + "; update EXPECTED_CONTROLLER_COUNT when adding one");

        assertTrue(wronglyScoped.isEmpty(),
                "FXML controllers must be @Scope(SCOPE_PROTOTYPE); these are not: " + wronglyScoped);
    }

    /** The scope annotation is the rule; this is the behaviour it is there to produce. */
    @Test
    @DisplayName("asking twice for a controller yields two instances")
    void askingTwiceForAControllerYieldsTwoInstances() {
        assertNotSame(context.getBean(com.culberth.tools.datablaster.controller.MainController.class),
                context.getBean(com.culberth.tools.datablaster.controller.MainController.class),
                "a second shell must not share the first shell's controller");
    }

    /**
     * The mirror of the rule above. {@link AppState} in particular is documented as an
     * application-lifetime singleton, and its weak-listener contract only makes sense if it is one:
     * a prototype {@code AppState} would give each controller a private copy of the shared state
     * and the ribbon would stop tracking the content area.
     */
    @Test
    @DisplayName("shared services are singletons")
    void sharedServicesAreSingletons() {
        for (Class<?> type : List.of(AppState.class, ViewLoader.class, ViewRegistry.class,
                ViewSwitcher.class, StageRegistry.class, DialogService.class)) {
            assertSame(context.getBean(type), context.getBean(type),
                    type.getSimpleName() + " must be a singleton");
        }
    }
}
