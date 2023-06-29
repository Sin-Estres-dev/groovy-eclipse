/*
 * Copyright 2009-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.eclipse;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.codehaus.groovy.eclipse.adapters.ClassFileEditorAdapterFactory;
import org.codehaus.groovy.eclipse.adapters.GroovyIFileEditorInputAdapterFactory;
import org.codehaus.groovy.eclipse.core.preferences.PreferenceConstants;
import org.codehaus.groovy.eclipse.debug.ui.EnsureJUnitFont;
import org.codehaus.groovy.eclipse.debug.ui.GroovyDebugOptionsEnforcer;
import org.codehaus.groovy.eclipse.debug.ui.GroovyJavaDebugElementAdapterFactory;
import org.codehaus.groovy.eclipse.editor.GroovyAwareFoldingStructureProvider;
import org.codehaus.groovy.eclipse.editor.GroovyOutlineTools;
import org.codehaus.groovy.eclipse.editor.GroovyTextTools;
import org.codehaus.groovy.eclipse.refactoring.actions.DelegatingCleanUpPostSaveListener;
import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.groovy.core.util.ReflectionUtils;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileEditor;
import org.eclipse.jdt.internal.ui.text.folding.JavaFoldingStructureProviderRegistry;
import org.eclipse.jdt.ui.text.folding.DefaultJavaFoldingStructureProvider;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.util.PrefUtil;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class GroovyPlugin extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "org.codehaus.groovy.eclipse.ui";
    public static final String GROOVY_TEMPLATE_CTX = "org.codehaus.groovy.eclipse.templates";
    public static final String COMPILER_MISMATCH_MARKER = "org.codehaus.groovy.eclipse.core.compilerMismatch";

    private static GroovyPlugin plugin;

    public static GroovyPlugin getDefault() {
        return plugin;
    }

    public static IWorkbenchPage getActiveWorkbenchPage() {
        IWorkbenchWindow window = getActiveWorkbenchWindow();
        if (window != null) return window.getActivePage();
        return null;
    }

    public static Shell getActiveWorkbenchShell() {
        IWorkbenchWindow window = getActiveWorkbenchWindow();
        if (window == null) {
            return null;
        }
        Shell shell = window.getShell();
        if (shell == null) {
            shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
        }
        return shell;
    }

    public static IWorkbenchWindow getActiveWorkbenchWindow() {
        if (plugin == null) {
            return null;
        }
        IWorkbench workbench = PlatformUI.getWorkbench();
        if (workbench == null) {
            return null;
        }
        return workbench.getActiveWorkbenchWindow();
    }

    public static void trace(String message) {
        if (plugin.isDebugging()) {
            System.out.println(message);
        }
    }

    //--------------------------------------------------------------------------

    private EnsureJUnitFont junitMono;
    private GroovyTextTools textTools;
    private GroovyOutlineTools outlineTools;

    public GroovyPlugin() {
        plugin = this;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        textTools = new GroovyTextTools();
        outlineTools = new GroovyOutlineTools();

        addMonospaceFontListener();
        setStructureProviderRegistry();
        DelegatingCleanUpPostSaveListener.installCleanUp();

        IAdapterManager adapterManager = Platform.getAdapterManager();
        adapterManager.registerAdapters(new ClassFileEditorAdapterFactory(), ClassFileEditor.class);
        // register our own stack frame label provider so that groovy stack frames are shown differently
        adapterManager.registerAdapters(new GroovyJavaDebugElementAdapterFactory(), IJavaStackFrame.class);
        adapterManager.registerAdapters(new GroovyIFileEditorInputAdapterFactory(), IFileEditorInput.class);

        if (Boolean.parseBoolean(System.getProperty("eclipse.groovy.debug", "true"))) addDebugLaunchListener();
        if (getPreferenceStore().getBoolean(PreferenceConstants.GROOVY_DEBUG_FORCE_DEBUG_OPTIONS_ON_STARTUP)) {
            new GroovyDebugOptionsEnforcer().maybeForce(getPreferenceStore());
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            textTools.dispose();
            textTools = null;

            outlineTools.dispose();
            outlineTools = null;

            removeMonospaceFontListener();
            DelegatingCleanUpPostSaveListener.uninstallCleanUp();
        } finally {
            super.stop(context);
        }
    }

    private void addDebugLaunchListener() {
        DebugPlugin.getDefault().getLaunchManager().addLaunchListener(new ILaunchListener() {
            @Override
            public void launchAdded(ILaunch launch) {
            }

            @Override
            public void launchChanged(ILaunch launch) {
                for (IDebugTarget target : launch.getDebugTargets()) {
                    if (target instanceof org.eclipse.jdt.debug.core.IJavaDebugTarget) try { //@formatter:off
                        if (ReflectionUtils.throwableGetPrivateField(target.getClass(), "fEngines", target) == null) {
                            ReflectionUtils.throwableSetPrivateField(target.getClass(), "fEngines", target, evalEngineInterceptor(target));
                        }
                    } catch (Exception e) {
                        logError("Error installing evaluation engine hooks", e);
                    }
                    //@formatter:on
                }
            }

            @Override
            public void launchRemoved(ILaunch launch) {
            }
        });
    }

    private void addMonospaceFontListener() {
        junitMono = new EnsureJUnitFont();
        try {
            if (PlatformUI.isWorkbenchRunning() && getActiveWorkbenchPage() != null) {
                getActiveWorkbenchPage().addPartListener(junitMono);
            }
            getPreferenceStore().addPropertyChangeListener(junitMono);
            PrefUtil.getInternalPreferenceStore().addPropertyChangeListener(junitMono);
        } catch (Exception e) {
            logError("Error installing JUnit monospace font listener", e);
        }
    }

    private void removeMonospaceFontListener() {
        try {
            if (!PlatformUI.getWorkbench().isClosing()) {
                getActiveWorkbenchPage().removePartListener(junitMono);
            }
        } catch (RuntimeException e) {
            // best-effort removal
        } finally {
            PrefUtil.getInternalPreferenceStore().removePropertyChangeListener(junitMono);
            getPreferenceStore().removePropertyChangeListener(junitMono);
            junitMono = null;
        }
    }

    private void setStructureProviderRegistry() {
        try {
            // JavaEditor.createSourceViewer is final so GroovyEditor cannot override
            // to extend the default folding structure provider, override the provider registry
            ReflectionUtils.setPrivateField(JavaPlugin.class, "fFoldingStructureProviderRegistry", JavaPlugin.getDefault(), new JavaFoldingStructureProviderRegistry() {
                @Override
                public IJavaFoldingStructureProvider getCurrentFoldingProvider() {
                    IJavaFoldingStructureProvider provider = super.getCurrentFoldingProvider();
                    if (provider.getClass().equals(DefaultJavaFoldingStructureProvider.class)) {
                        provider = new GroovyAwareFoldingStructureProvider();
                    }
                    return provider;
                }
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            // use Java defaults
        }
    }

    public GroovyTextTools getTextTools() {
        return textTools;
    }

    public GroovyOutlineTools getOutlineTools() {
        return outlineTools;
    }

    public void logMessage(String message) {
        log(IStatus.INFO, message, null);
    }

    public void logWarning(String message) {
        log(IStatus.WARNING, message, null);
    }

    public void logError(String message, Throwable error) {
        log(IStatus.ERROR, message, error);
    }

    private void log(int severity, String message, Throwable cause) {
        getLog().log(new Status(severity, PLUGIN_ID, message, cause));
    }

    //--------------------------------------------------------------------------

    private static Object evalEngineInterceptor(IDebugTarget target) {
        return Collections.synchronizedMap(new AbstractMap<org.eclipse.jdt.core.IJavaProject, org.eclipse.jdt.debug.eval.IAstEvaluationEngine>() {

            private Set<Entry<org.eclipse.jdt.core.IJavaProject, org.eclipse.jdt.debug.eval.IAstEvaluationEngine>> entries = new HashSet<>(2);

            @Override
            public  Set<Entry<org.eclipse.jdt.core.IJavaProject, org.eclipse.jdt.debug.eval.IAstEvaluationEngine>> entrySet() {
                return entries;
            }

            @Override
            public org.eclipse.jdt.debug.eval.IAstEvaluationEngine get(Object key) {
                if (!containsKey(key) && key instanceof org.eclipse.jdt.core.IJavaProject) {
                    org.eclipse.jdt.core.IJavaProject javaProject = (org.eclipse.jdt.core.IJavaProject) key;
                    if (org.codehaus.jdt.groovy.model.GroovyNature.hasGroovyNature(javaProject.getProject())) {
                        org.eclipse.jdt.debug.eval.IAstEvaluationEngine evalEngine =
                            new org.codehaus.groovy.eclipse.debug.EvaluationEngine(javaProject, (org.eclipse.jdt.debug.core.IJavaDebugTarget) target);
                        entries.add(new SimpleEntry<>(javaProject, evalEngine));
                        return evalEngine;
                    }
                }
                return super.get(key);
            }

            @Override
            public org.eclipse.jdt.debug.eval.IAstEvaluationEngine put(org.eclipse.jdt.core.IJavaProject key, org.eclipse.jdt.debug.eval.IAstEvaluationEngine value) {
                if (org.codehaus.jdt.groovy.model.GroovyNature.hasGroovyNature(key.getProject())) {
                    value.dispose();
                    value = new org.codehaus.groovy.eclipse.debug.EvaluationEngine(key, (org.eclipse.jdt.debug.core.IJavaDebugTarget) target);
                }
                for (Entry<org.eclipse.jdt.core.IJavaProject, org.eclipse.jdt.debug.eval.IAstEvaluationEngine> entry : entrySet()) {
                    if (entry.getKey().equals(key)) {
                        return entry.setValue(value);
                    }
                }
                entries.add(new SimpleEntry<>(key, value));
                return null;
            }
        });
    }
}
