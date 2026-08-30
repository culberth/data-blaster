package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Theme;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javafx.beans.InvalidationListener;
import javafx.scene.Parent;
import org.springframework.stereotype.Component;

/**
 * Applies the current {@link Theme} to node trees, and re-applies it to every tree it has already
 * themed when the theme changes.
 *
 * <p><strong>This is the part that was missing the first time.</strong> A theme control was once
 * removed from Preferences rather than shipped, because it persisted a value nothing honoured. The
 * seam below is what makes it honoured: changing the theme restyles what is already on screen, not
 * merely the next thing created.
 *
 * <p><strong>Why it is only a style class.</strong> Every rule in {@code ribbon.css} refers to a
 * design token rather than a hex literal, and JavaFX resolves looked-up colours from the node
 * outward — so redefining the tokens under {@code .root.theme-dark} re-colours the entire
 * stylesheet. Swapping the class is therefore instant and needs no stylesheet reload; the
 * alternative, a stylesheet per theme, means a parallel copy of every rule.
 *
 * <p><strong>Why a list of roots rather than {@link javafx.stage.Window#getWindows()}.</strong>
 * The obvious implementation walks JavaFX's own window list, and it is wrong in a way that only
 * shows up under test: that list carries windows which are <em>showing</em>, so a scene that has
 * been built but not yet shown is missed, and the membership rules are the toolkit's rather than
 * this application's. Remembering what was actually themed is both narrower and more honest — every
 * tree this class has touched gets re-touched, whatever its window is doing.
 *
 * <p>References are weak, so a dismissed dialog's scene graph is collectable rather than pinned
 * here for the life of the application — the same trap {@code AppState}'s Javadoc warns about for
 * its own listeners.
 *
 * <p>Not thread-safe: like everything touching the scene graph, call it on the JavaFX Application
 * Thread.
 */
@Component
public class ThemeService {

    private final AppState appState;

    /** Every root themed so far, weakly, most recent last. */
    private final List<WeakReference<Parent>> themedRoots = new ArrayList<>();

    /**
     * Strongly held, unlike the ribbon controllers' listeners. Those must be weak because they are
     * prototype-scoped and outlive their scene graph; this is a singleton with the same lifetime as
     * {@link AppState}, and a weak listener here would be liable to collection — silently leaving
     * the theme control looking like it works while nothing repainted.
     *
     * <p><strong>An invalidation listener, not a change listener</strong>, and that distinction is
     * load-bearing. Written as the only {@code ChangeListener} on this property, it was notified of
     * the first switch and not of the second — so the theme changed once and then appeared stuck,
     * with the stored value and the screen disagreeing. Attaching any unrelated second listener made
     * it work again, which is about as misleading as a symptom gets: it looks like the code near the
     * listener is fine and something else is at fault.
     *
     * <p>Invalidation is the right primitive regardless. This class does not care which theme it
     * changed <em>from</em> — it re-reads the current one and applies it — and asking for
     * "something changed, go look" avoids the previous-value bookkeeping entirely. That the
     * {@code ChangeListener} form misbehaved is the reason it is written down; that it was never
     * needed is the reason this is not a workaround.
     */
    private final InvalidationListener themeListener;

    public ThemeService(AppState appState) {
        this.appState = appState;
        this.themeListener = observable -> reapplyToAll();
        // Registered on construction rather than from a start-up call: there is no ordering to get
        // right — nothing has been themed yet — and one less thing for start-up to remember.
        appState.themeProperty().addListener(themeListener);
    }

    /**
     * Puts the current theme's style class on {@code root}, and remembers it so a later change
     * reaches it too.
     *
     * <p>Called for every scene {@link com.culberth.tools.datablaster.ViewLoader} creates, so a dialog opened
     * after the theme changed is born with the right one rather than flashing the old.
     */
    public void applyTo(Parent root) {
        setThemeClass(root, appState.getTheme());
        remember(root);
    }

    private void reapplyToAll() {
        Theme current = appState.getTheme();
        for (Iterator<WeakReference<Parent>> it = themedRoots.iterator(); it.hasNext(); ) {
            Parent root = it.next().get();
            if (root == null) {
                // Collected since it was themed — a dialog that has been dismissed.
                it.remove();
            } else {
                setThemeClass(root, current);
            }
        }
    }

    private static void setThemeClass(Parent root, Theme theme) {
        for (Theme other : Theme.values()) {
            // Remove every theme's class, not just the previous one: it keeps the invariant true
            // even if a fork adds a third theme, and it costs nothing.
            root.getStyleClass().remove(other.styleClass());
        }
        root.getStyleClass().add(theme.styleClass());
    }

    private void remember(Parent root) {
        for (Iterator<WeakReference<Parent>> it = themedRoots.iterator(); it.hasNext(); ) {
            Parent existing = it.next().get();
            if (existing == null) {
                it.remove();
            } else if (existing == root) {
                // Already tracked. Re-theming the same root — which ViewLoader does not do today,
                // but a fork might — must not add a second entry.
                return;
            }
        }
        themedRoots.add(new WeakReference<>(root));
    }
}
