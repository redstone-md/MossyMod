package md.redstone.gui;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.function.Consumer;

final class MossyOwoComponents {
    private static final String[] FACTORY_CLASSES = {
        "io.wispforest.owo.ui.component.UIComponents",
        "io.wispforest.owo.ui.component.Components"
    };

    private MossyOwoComponents() {
    }

    static ButtonComponent button(Component text, Consumer<ButtonComponent> action) {
        return invoke(ButtonComponent.class, "button", text, action);
    }

    static LabelComponent label(Component text) {
        return invoke(LabelComponent.class, "label", text);
    }

    static TextBoxComponent textBox(Sizing sizing) {
        return invoke(TextBoxComponent.class, "textBox", sizing);
    }

    static BoxComponent box(Sizing horizontalSizing, Sizing verticalSizing) {
        return invoke(BoxComponent.class, "box", horizontalSizing, verticalSizing);
    }

    private static <T> T invoke(Class<T> returnType, String methodName, Object... args) {
        RuntimeException lastFailure = null;
        for (String factoryClass : FACTORY_CLASSES) {
            try {
                Class<?> type = Class.forName(factoryClass);
                Method method = findFactory(type, methodName, args.length);
                return returnType.cast(method.invoke(null, args));
            } catch (ClassNotFoundException ignored) {
                continue;
            } catch (ReflectiveOperationException | LinkageError e) {
                lastFailure = new IllegalStateException("Failed to call owo factory " + factoryClass + "." + methodName, e);
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("No supported owo component factory found for " + methodName);
    }

    private static Method findFactory(Class<?> type, String methodName, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName + " with " + parameterCount + " parameter(s)");
    }
}
