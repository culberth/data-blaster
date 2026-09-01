package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.model.PortTailMapping;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

/**
 * Configures a {@code Spinner<Integer>} to edit a TCP port.
 *
 * <p><strong>Here rather than in each controller.</strong> The Blast Port has two surfaces — the
 * Global ribbon group and the Preferences General tab — and a port spinner needs three things that
 * are each easy to leave out and invisible when they are: bounds taken from
 * {@link PortTailMapping}'s range so a control cannot offer a value the store would reject on the
 * next launch, a converter that survives nonsense, and a commit when focus leaves. Two copies of
 * that would be two chances for one surface to accept what the other refuses.
 *
 * <p><strong>The converter is the load-bearing part.</strong> {@code Spinner.commitEditorText()}
 * does not guard the conversion, so the default {@code IntegerStringConverter} turns a stray
 * keystroke into a {@code NumberFormatException} thrown out of a focus listener — into FX's default
 * handler, which reports to a console the windowed build does not have. Keeping the current value
 * instead means a typo is simply not accepted, which is what the arrows and the range already
 * imply.
 *
 * <p><strong>The commit is the one people notice.</strong> An editable {@code Spinner} holds typed
 * text in its editor until something commits it, so tabbing away — or pressing Close on the
 * dialog — would otherwise discard a port the user had just typed and watched appear.
 * {@code increment(0)} is the commit; the factory clamps afterwards, so a typed {@code 0} becomes
 * {@code 1} rather than a silently rejected setting.
 */
public final class PortSpinner {

    private PortSpinner() {
    }

    /**
     * Points {@code spinner} at a new factory over the valid port range, starting at
     * {@code initialValue}.
     *
     * @return the factory, which the caller keeps so it can put the control back on a reset — the
     *         spinner's own value property is read-only from the outside
     */
    public static SpinnerValueFactory.IntegerSpinnerValueFactory configure(Spinner<Integer> spinner,
                                                                          int initialValue) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        PortTailMapping.PORT_MIN, PortTailMapping.PORT_MAX, initialValue);
        factory.setConverter(converterKeeping(factory));
        spinner.setValueFactory(factory);
        spinner.focusedProperty().addListener((observable, was, hasFocus) -> {
            if (!hasFocus) {
                spinner.increment(0);
            }
        });
        return factory;
    }

    private static StringConverter<Integer> converterKeeping(
            SpinnerValueFactory.IntegerSpinnerValueFactory factory) {
        return new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : Integer.toString(value);
            }

            @Override
            public Integer fromString(String text) {
                if (text == null) {
                    return factory.getValue();
                }
                try {
                    return Integer.valueOf(text.trim());
                } catch (NumberFormatException notANumber) {
                    return factory.getValue();
                }
            }
        };
    }
}
