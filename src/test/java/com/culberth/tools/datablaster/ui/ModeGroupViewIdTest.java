package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.model.Mode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Closes the loop on the {@code userData} convention, which has two halves that live in different files and are wired
 * together by nothing but a string.
 *
 * <p>
 * {@code mode-group.fxml} declares which {@link Mode} each toggle selects in its {@code userData}; {@link ViewRegistry}
 * maps modes to FXML resources. Keying the registry by the enum turned half of the original risk into a compiler error
 * — the half that remains is the markup, where a mode name is still just text. A typo there produces a ribbon button
 * that throws the first time it is pressed, which is a run-time discovery in a build that is otherwise green.
 *
 * <p>
 * The FXML is read as XML rather than loaded, so this test needs no JavaFX toolkit. The convention being checked is a
 * fact about the file's text, and reading it that way keeps this test in the display-free majority of the suite. That
 * the file also <em>loads</em> is {@code FxmlSmokeTest}'s job.
 */
class ModeGroupViewIdTest
{

    private static final String MODE_GROUP_FXML = "/fxml/ribbon/mode-group.fxml";

    /** The four Mode toggles. Asserted so a lookup that matched nothing cannot pass quietly. */
    private static final int EXPECTED_TOGGLE_COUNT = 4;

    @Test
    @DisplayName("every userData in the ribbon names a real Mode")
    void everyUserDataInTheRibbonNamesARealMode() throws Exception
    {
        List<String> declared = declaredModeNames();

        assertEquals(EXPECTED_TOGGLE_COUNT, declared.size(), "expected " + EXPECTED_TOGGLE_COUNT
                + " toggles carrying a userData mode name in " + MODE_GROUP_FXML + " but found " + declared);

        for (String name : declared)
        {
            assertDoesNotThrow(() -> Mode.valueOf(name),
                    "the ribbon declares userData '" + name + "', which is not a Mode constant");
        }
    }

    /**
     * The other direction, which only became checkable once the modes were a closed set. A mode with no toggle is
     * unreachable from the ribbon, and since every mode is meant to be selectable that is a defect rather than a fork's
     * prerogative.
     */
    @Test
    @DisplayName("every Mode has a toggle")
    void everyModeHasAToggle() throws Exception
    {
        Set<Mode> declared = EnumSet.noneOf(Mode.class);
        for (String name : declaredModeNames())
        {
            declared.add(Mode.valueOf(name));
        }

        assertEquals(EnumSet.allOf(Mode.class), declared, "every mode should be reachable from the ribbon");
    }

    @Test
    @DisplayName("every mode the ribbon declares resolves to a view")
    void everyModeTheRibbonDeclaresResolvesToAView() throws Exception
    {
        ViewRegistry registry = new ViewRegistry();

        for (String name : declaredModeNames())
        {
            Mode mode = Mode.valueOf(name);
            assertDoesNotThrow(() -> registry.resourceFor(mode),
                    "the ribbon can select " + mode + " but no view is registered for it");
        }
    }

    /**
     * The first toggle carries {@code selected="true"}, so it is what the ribbon shows before anything restores a
     * stored mode. It should be the mode a first run starts in, or the ribbon highlights one button while the content
     * area shows another view.
     */
    @Test
    @DisplayName("the toggle selected in the markup is the default mode")
    void theToggleSelectedInTheMarkupIsTheDefaultMode() throws Exception
    {
        assertEquals(com.culberth.tools.datablaster.model.Settings.DEFAULTS.mode(),
                Mode.valueOf(declaredModeNames().get(0)),
                "mode-group.fxml marks the first toggle selected, so it must be the default mode");
    }

    /**
     * Reads the {@code <userData><String fx:value="..."/></userData>} of every toggle in the Mode group, in document
     * order.
     */
    private static List<String> declaredModeNames() throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The FXML carries processing instructions and the fx: prefix; namespace-aware parsing is
        // unnecessary for reading fx:value and would require declaring the prefix here.
        factory.setNamespaceAware(false);

        Document document;
        try (InputStream in = ModeGroupViewIdTest.class.getResourceAsStream(MODE_GROUP_FXML))
        {
            assertTrue(in != null, MODE_GROUP_FXML + " is not on the test classpath");
            document = factory.newDocumentBuilder().parse(in);
        }

        List<String> names = new ArrayList<>();
        NodeList userDataElements = document.getElementsByTagName("userData");
        for (int i = 0; i < userDataElements.getLength(); i++)
        {
            NodeList children = userDataElements.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++)
            {
                Node child = children.item(j);
                if (child instanceof Element element && "String".equals(element.getTagName()))
                {
                    names.add(element.getAttribute("fx:value"));
                }
            }
        }
        return names;
    }
}
