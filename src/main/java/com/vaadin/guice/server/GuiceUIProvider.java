package com.vaadin.guice.server;

import com.vaadin.guice.annotation.GuiceUI;
import com.vaadin.server.UIClassSelectionEvent;
import com.vaadin.server.UICreateEvent;
import com.vaadin.server.UIProvider;
import com.vaadin.ui.UI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkState;
import static com.vaadin.guice.server.PathUtil.extractUIPathFromRequest;
import static com.vaadin.guice.server.PathUtil.preparePath;

/**
 * Vaadin {@link com.vaadin.server.UIProvider} that looks up UI classes from the Guice application
 * context. The UI classes must be annotated with {@link GuiceUI}.
 *
 * @author Petter Holmström (petter@vaadin.com)
 * @author Henri Sara (hesara@vaadin.com)
 * @author Bernd Hopp (bernd@vaadin.com)
 */
class GuiceUIProvider extends UIProvider {

    private final Map<String, Class<? extends UI>> pathToUIMap;
    private final Map<String, Class<? extends UI>> wildcardPathToUIMap;
    private final GuiceVaadin guiceVaadin;

    GuiceUIProvider(GuiceVaadin guiceVaadin) {
        this.guiceVaadin = guiceVaadin;
        Logger logger = Logger.getLogger(getClass().getName());

        logger.info("Checking the application context for Vaadin UIs");

        pathToUIMap = new ConcurrentHashMap<>();
        wildcardPathToUIMap = new ConcurrentHashMap<>();

        for (Class<? extends UI> uiClass : guiceVaadin.getUis()) {

            GuiceUI annotation = uiClass.getAnnotation(GuiceUI.class);

            if (annotation == null) {
                logger.log(Level.WARNING, "ignoring {0}, because it has no @GuiceUI annotation", new Object[]{uiClass});
                continue;
            }

            String path = annotation.path();
            path = preparePath(path);

            Class<? extends UI> existingUiForPath = pathToUIMap.get(path);

            checkState(
                    existingUiForPath == null,
                    "[%s] is already mapped to the path [%s]",
                    existingUiForPath,
                    path
            );

            logger.log(Level.INFO, "Mapping Vaadin UI [{0}] to path [{1}]",
                    new Object[]{uiClass.getCanonicalName(), path});

            if (path.endsWith("/*")) {
                final String truncatedPath = path.substring(0, path.length() - 2);

                wildcardPathToUIMap.put(truncatedPath, uiClass);
            } else {
                pathToUIMap.put(path, uiClass);
            }
        }

        if (pathToUIMap.isEmpty()) {
            logger.log(Level.WARNING, "Found no Vaadin UIs in the application context");
        }
    }

    @Override
    public Class<? extends UI> getUIClass(
            UIClassSelectionEvent uiClassSelectionEvent) {
        final String path = extractUIPathFromRequest(uiClassSelectionEvent
                .getRequest());

        Class<? extends UI> uiClass = pathToUIMap.get(path);

        if (uiClass != null) {
            return uiClass;
        }

        return wildcardPathToUIMap
                .entrySet()
                .stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public UI createInstance(UICreateEvent event) {
        synchronized (guiceVaadin) {
            try {
                guiceVaadin.getUiScoper().startInitialization();

                UI instance = guiceVaadin.assemble(event.getUIClass());

                guiceVaadin.getUiScoper().endInitialization(instance);

                return instance;
            } catch (RuntimeException e) {
                guiceVaadin.getUiScoper().rollbackInitialization();
                throw e;
            }
        }
    }
}
