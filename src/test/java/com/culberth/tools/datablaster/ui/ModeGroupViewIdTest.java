package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Closes the loop on the {@code userData} view-id convention, which has two halves that live in
 * different files and are wired together by nothing but a string.
 *
 * <p>{@code mode-group.fxml} declares which view each toggle selects in its {@code userData};
 * {@link ViewRegistry} maps those ids to FXML resources. Neither knows about the other. A typo in
 * either — or a view renamed on one side only — produces a ribbon button that throws
 * {@code IllegalArgumentException} the first time it is pressed, which is a run-time discovery in
 * a build that is otherwise green.
 *
 * <p>The FXML is read as XML rather than loaded, so this test needs no JavaFX toolkit. The
 * convention being checked is a fact about the file's text, and reading it that way keeps this
 * test in the display-free majority of the suite. That the file also <em>loads</em> is
 * {@code FxmlSmokeTest}'s job.
 */
class ModeGroupViewIdTest {

    private static final String MODE_GROUP_FXML = "/fxml/ribbon/mode-group.fxml";

    /** The four Mode toggles. Asserted so an XPath that matched nothing cannot pass quietly. */
    private static final int EXPECTED_TOGGLE_COUNT = 4;

    @Test
    @DisplayName("every view id declared in the ribbon resolves through the registry")
    void everyViewIdDeclaredInTheRibbonResolvesThroughTheRegistry() throws Exception {
        List<String> declared = declaredViewIds();
        ViewRegistry registry = new ViewRegistry();

        assertEquals(EXPECTED_TOGGLE_COUNT, declared.size(),
                "expected " + EXPECTED_TOGGLE_COUNT + " toggles carrying a userData view id in "
                        + MODE_GROUP_FXML + " but found " + declared);

        for (String viewId : declared) {
            assertDoesNotThrow(() -> registry.resourceFor(viewId),
                    "the ribbon declares view id '" + viewId
                            + "' but ViewRegistry does not register it");
        }
    }

    /**
     * The other direction. A view registered but not reachable from the ribbon is not a defect —
     * a fork may route to it some other way — so this only reports it, by asserting the default
     * view is one the ribbon can actually select. A default no button selects would leave the
     * ribbon showing nothing highlighted at start-up.
     */
    @Test
    @DisplayName("the registry's default view is one the ribbon can select")
    void theRegistrysDefaultViewIsOneTheRibbonCanSelect() throws Exception {
        String defaultViewId = new ViewRegistry().defaultViewId();
        assertTrue(declaredViewIds().contains(defaultViewId),
                "ViewRegistry defaults to '" + defaultViewId
                        + "', which no Mode toggle declares; the ribbon would start with no "
                        + "button selected");
    }

    /**
     * Reads the {@code <userData><String fx:value="..."/></userData>} of every toggle in the Mode
     * group, in document order.
     */
    private static List<String> declaredViewIds() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The FXML carries processing instructions and the fx: prefix; namespace-aware parsing is
        // unnecessary for reading fx:value and would require declaring the prefix here.
        factory.setNamespaceAware(false);

        Document document;
        try (InputStream in = ModeGroupViewIdTest.class.getResourceAsStream(MODE_GROUP_FXML)) {
            assertTrue(in != null, MODE_GROUP_FXML + " is not on the test classpath");
            document = factory.newDocumentBuilder().parse(in);
        }

        List<String> viewIds = new ArrayList<>();
        NodeList userDataElements = document.getElementsByTagName("userData");
        for (int i = 0; i < userDataElements.getLength(); i++) {
            NodeList children = userDataElements.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child instanceof Element element && "String".equals(element.getTagName())) {
                    viewIds.add(element.getAttribute("fx:value"));
                }
            }
        }
        return viewIds;
    }
}
