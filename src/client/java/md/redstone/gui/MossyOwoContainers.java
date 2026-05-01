package md.redstone.gui;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;
//? if <1.21.11
/*import io.wispforest.owo.ui.core.Component;*/
//? if >=1.21.11
import io.wispforest.owo.ui.core.UIComponent;

import java.lang.reflect.Method;

final class MossyOwoContainers {
    private static final String[] FACTORY_CLASSES = {
        "io.wispforest.owo.ui.container.UIContainers",
        "io.wispforest.owo.ui.container.Containers"
    };

    private MossyOwoContainers() {
    }

    static FlowLayout verticalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return invoke(FlowLayout.class, "verticalFlow", horizontalSizing, verticalSizing);
    }

    static FlowLayout horizontalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return invoke(FlowLayout.class, "horizontalFlow", horizontalSizing, verticalSizing);
    }

    //? if <1.21.11
    /*static <C extends Component> ScrollContainer<C> verticalScroll(Sizing horizontalSizing, Sizing verticalSizing, C child) {
    *///? if >=1.21.11
    static <C extends UIComponent> ScrollContainer<C> verticalScroll(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        return invoke(ScrollContainer.class, "verticalScroll", horizontalSizing, verticalSizing, child);
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
        throw new IllegalStateException("No supported owo container factory found for " + methodName);
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
