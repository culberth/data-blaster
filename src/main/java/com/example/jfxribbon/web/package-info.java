/**
 * The companion HTTP layer. Endpoints here run on Tomcat worker threads, <em>not</em> the JavaFX
 * Application Thread, and therefore must never touch a JavaFX node or property directly.
 *
 * <p>To read application state, use
 * {@link com.example.jfxribbon.model.AppState#snapshot() AppState.snapshot()}, which returns an
 * immutable copy safe to read from any thread. To change it, hand the mutation to
 * {@link com.example.jfxribbon.model.AppState#onFxThread(Runnable) AppState.onFxThread(Runnable)}.
 *
 * <p>Note also that nothing here is authenticated; the connector is bound to loopback in
 * {@code application.properties} for that reason.
 */
package com.example.jfxribbon.web;
