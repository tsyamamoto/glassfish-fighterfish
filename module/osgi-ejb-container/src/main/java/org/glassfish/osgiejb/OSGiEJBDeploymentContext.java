/*
 * Copyright (c) 2009, 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.osgiejb;

import org.glassfish.osgijavaeebase.OSGiArchiveHandler;
import org.glassfish.osgijavaeebase.OSGiDeploymentContext;
import org.glassfish.osgijavaeebase.BundleClassLoader;
import org.glassfish.internal.api.Globals;
import org.glassfish.internal.api.ClassLoaderHierarchy;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.api.deployment.OpsParams;
import org.glassfish.api.deployment.InstrumentableClassLoader;
import org.osgi.framework.Bundle;

import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;

import com.sun.enterprise.module.common_impl.CompositeEnumeration;

/**
 * Custom deployment context for the OSGi EJB container.
 */
public final class OSGiEJBDeploymentContext extends OSGiDeploymentContext {

    /**
     * Create a new instance.
     * @param actionReport GlassFish command reporter
     * @param logger logger
     * @param source application archive
     * @param params GlassFish command parameters
     * @param env GlassFish server environment
     * @param bundle application bundle
     * @throws Exception if an error occurs
     */
    public OSGiEJBDeploymentContext(final ActionReport actionReport,
            final Logger logger, final ReadableArchive source,
            final OpsParams params, final ServerEnvironment env,
            final Bundle bundle) throws Exception {

        super(actionReport, logger, source, params, env, bundle);
        // ArchiveHandler must correctly return the ArchiveType for DOL
        // processing to succeed,
        setArchiveHandler(new OSGiArchiveHandler() {
            @Override
            public String getArchiveType() {
                // Since I am not able to reference GF 4.0 APIs as they are not
                // yet staged in a maven repo,
                // I am accessing the value in a round about way.
                // EjbType.ARCHIVE_TYPE;
                return javax.enterprise.deploy.shared.ModuleType.EJB
                        .toString();
            }
        });

    }

    @Override
    protected void setupClassLoader() throws Exception {
        final BundleClassLoader delegate1 = new BundleClassLoader(getBundle());
        final ClassLoader delegate2 =
                Globals.get(ClassLoaderHierarchy.class).getAPIClassLoader();
        ClassLoader cl = new DelegatingInstrumentableClassLoader(delegate1,
                delegate2);
        setShareableTempClassLoader(cl);
        setFinalClassLoader(cl);
    }

    /**
     * Custom class-loader.
     */
    private static final class DelegatingInstrumentableClassLoader
            extends ClassLoader implements InstrumentableClassLoader {

        /**
         * First delegate class-loader.
         */
        private final BundleClassLoader delegate1;

        /**
         * Second delegate class-loader.
         */
        private final ClassLoader delegate2;

        /**
         * Create a new instance.
         * @param cl1 the first delegate class-loader
         * @param cl2 the second delegate class-loader
         */
        private DelegatingInstrumentableClassLoader(final BundleClassLoader cl1,
                final ClassLoader cl2) {
            this.delegate1 = cl1;
            this.delegate2 = cl2;
        }

        @Override
        protected synchronized Class<?> loadClass(final String name,
                final boolean resolve) throws ClassNotFoundException {

            Class c = findLoadedClass(name);
            if (c == null) {
                try {
                    return delegate1.loadClass(name, resolve);
                } catch (ClassNotFoundException cnfe) {
                    return delegate2.loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }

        @Override
        public URL getResource(final String name) {
            URL url = delegate1.getResource(name);
            if (url == null) {
                url = delegate2.getResource(name);
            }
            return url;
        }

        @Override
        public Enumeration<URL> getResources(final String name)
                throws IOException {

            List<Enumeration<URL>> enumerators =
                    new ArrayList<Enumeration<URL>>();
            enumerators.add(delegate1.getResources(name));
            enumerators.add(delegate2.getResources(name));
            return new CompositeEnumeration(enumerators);
        }

        @Override
        public ClassLoader copy() {
            // do nothing, since we don't expect any transformation to take
            // place because of the way we implement
            // our JPA support. We actually do static enhancement.
            return this;
        }

        @Override
        public void addTransformer(final ClassFileTransformer transformer) {
            System.out.println("addTransformer called " + transformer);
            // do nothing, since we don't expect any transformation to take
            // place because of the way we implement
            // our JPA support. We actually do static enhancement.
        }
    }
}
