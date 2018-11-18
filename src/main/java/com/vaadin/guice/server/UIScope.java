/*
 * Copyright 2000-2017 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.guice.server;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.SessionDestroyEvent;
import com.vaadin.flow.server.SessionDestroyListener;
import com.vaadin.flow.server.VaadinSession;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.synchronizedMap;

class UIScope implements Scope, Serializable, SessionDestroyListener {

    private final transient Map<VaadinSession, Map<UI, Map<Key<?>, Object>>> scopesBySession;

    @SuppressWarnings("WeakerAccess")
    public UIScope(){
        scopesBySession = synchronizedMap(new HashMap<>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Provider<T> scope(Key<T> key, Provider<T> provider) {
        return () -> {

            final VaadinSession vaadinSession = checkNotNull(
                VaadinSession.getCurrent(),
                "VaadinSession is not set up yet."
            );

            vaadinSession.lock();

            try {
                final UI currentUI = checkNotNull(
                    UI.getCurrent(),
                    "current UI is not set up yet"
                );

                return (T) scopesBySession
                    .computeIfAbsent(vaadinSession, session -> new HashMap<>())
                    .computeIfAbsent(currentUI, ui -> new WeakHashMap<>())
                    .computeIfAbsent(key, k -> provider.get());
            } finally {
                vaadinSession.unlock();
            }
        };
    }

    @Override
    public void sessionDestroy(SessionDestroyEvent event) {
        synchronized (event.getSession()) {
            scopesBySession.remove(event.getSession());
        }
    }
}
