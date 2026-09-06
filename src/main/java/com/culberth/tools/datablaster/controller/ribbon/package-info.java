/**
 * One controller per ribbon group, each loaded by an {@code <fx:include>} in {@code main.fxml}.
 *
 * <p>
 * Groups never reference the shell's nodes and the shell never references theirs: everything they change is written to
 * {@link com.culberth.tools.datablaster.model.AppState}, and whatever needs to react observes it. Handing a group a
 * reference to the content pane would reintroduce exactly the shared-mutable-node problem this split exists to avoid —
 * adding a group means adding an FXML file and a controller, touching neither {@code MainController} nor the other
 * groups.
 *
 * <p>
 * <strong>Ordering.</strong> {@code FXMLLoader} builds depth-first, so every group's {@code initialize()} runs
 * <em>before</em> the shell's. A group may therefore subscribe to {@link com.culberth.tools.datablaster.model.AppState}
 * during its own initialise and be certain it sees the shell's first write — but it must not <em>publish</em> state
 * there, because the shell has not run yet and will overwrite it.
 *
 * <p>
 * <strong>Subscribing.</strong> {@code AppState} is an application-lifetime singleton and these controllers are
 * prototype-scoped, so register with {@code javafx.beans.value.WeakChangeListener} and keep the strong reference in a
 * field. A plain lambda pins the group's scene graph for the life of the application and leaves a discarded ribbon
 * still reacting.
 */
package com.culberth.tools.datablaster.controller.ribbon;
