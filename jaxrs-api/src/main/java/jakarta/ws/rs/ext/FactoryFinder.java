/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

package jakarta.ws.rs.ext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory finder utility class.
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @author Marek Potociar
 * @since 1.0
 */
final class FactoryFinder {

    private static final Logger LOGGER = Logger.getLogger(FactoryFinder.class.getName());
    private static final String RESTEASY_JAXRS_API_MODULE = "org.jboss.resteasy.resteasy-jaxrs-api";

    private FactoryFinder() {
        // prevents instantiation
    }

    private static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            ClassLoader cl = null;
            try {
                cl = Thread.currentThread().getContextClassLoader();
            } catch (SecurityException ex) {
                LOGGER.log(
                        Level.WARNING,
                        "Unable to get context classloader instance.",
                        ex);
            }
            return cl;
        });
    }

    /**
     * Creates an instance of the specified class using the specified
     * {@code ClassLoader} object.
     *
     * @param className   name of the class to be instantiated.
     * @param classLoader class loader to be used.
     * @return instance of the specified class.
     * @throws ClassNotFoundException if the given class could not be found
     *                                or could not be instantiated.
     */
    private static Object newInstance(final String className, final ClassLoader classLoader) throws ClassNotFoundException {
        try {
            Class<?> spiClass;
            if (classLoader == null) {
                spiClass = Class.forName(className);
            } else {
                try {
                    spiClass = Class.forName(className, false, classLoader);
                } catch (ClassNotFoundException ex) {
                    LOGGER.log(
                            Level.FINE,
                            "Unable to load provider class " + className
                            + " using custom classloader " + classLoader.getClass().getName()
                            + " trying again with current classloader.",
                            ex);
                    spiClass = Class.forName(className);
                }
            }
            return spiClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException x) {
            throw x;
        } catch (Exception x) {
            throw new ClassNotFoundException("Provider " + className + " could not be instantiated: " + x, x);
        }
    }

    /**
     * Finds the implementation {@code Class} for the given factory name,
     * or if that fails, finds the {@code Class} for the given fallback
     * class name and create its instance. The arguments supplied MUST be
     * used in order. If using the first argument is successful, the second
     * one will not be used.
     * <p>
     * This method is package private so that this code can be shared.
     *
     * @param factoryId         the name of the factory to find, which is
     *                          a system property.
     * @param fallbackClassName the implementation class name, which is
     *                          to be used only if nothing else.
     *                          is found; {@code null} to indicate that
     *                          there is no fallback class name.
     * @param service           service to be found.
     * @param <T>               type of the service to be found.
     * @return the instance of the specified service; may not be {@code null}.
     * @throws ClassNotFoundException if the given class could not be found
     *                                or could not be instantiated.
     */
    static <T> Object find(final String factoryId, final String fallbackClassName, Class<T> service) throws ClassNotFoundException {
        ClassLoader classLoader = getContextClassLoader();

        try {
            Iterator<T> iterator = ServiceLoader.load(service, FactoryFinder.getContextClassLoader()).iterator();

            if(iterator.hasNext()) {
                return iterator.next();
            }
        } catch (Exception | ServiceConfigurationError ex) {
            LOGGER.log(Level.FINER, "Failed to load service " + factoryId + ".", ex);
        }

        try {
            Iterator<T> iterator = ServiceLoader.load(service, FactoryFinder.class.getClassLoader()).iterator();

            if(iterator.hasNext()) {
                return iterator.next();
            }
        } catch (Exception | ServiceConfigurationError ex) {
            LOGGER.log(Level.FINER, "Failed to load service " + factoryId + ".", ex);
        }

        // try to read from $java.home/lib/jaxrs.properties
        FileInputStream inputStream = null;
        String configFile = null;
        try {
            String javah = System.getProperty("java.home");
            configFile = javah + File.separator + "lib" + File.separator + "jaxrs.properties";
            File f = new File(configFile);
            if (f.exists()) {
                Properties props = new Properties();
                inputStream = new FileInputStream(f);
                props.load(inputStream);
                String factoryClassName = props.getProperty(factoryId);
                return newInstance(factoryClassName, classLoader);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINER, "Failed to load service " + factoryId
                    + " from $java.home/lib/jaxrs.properties", ex);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.FINER, String.format("Error closing %s file.", configFile), ex);
                }
            }
        }

        // Use the system property
        try {
            String systemProp = System.getProperty(factoryId);
            if (systemProp != null) {
                return newInstance(systemProp, classLoader);
            }
        } catch (SecurityException se) {
            LOGGER.log(Level.FINER, "Failed to load service " + factoryId
                    + " from a system property", se);
        }

        ClassLoader moduleClassLoader = getModuleClassLoader();
        if (moduleClassLoader != null) {
           String serviceId = "META-INF/services/" + factoryId;
           try (InputStream is = moduleClassLoader.getResourceAsStream(serviceId)) {
              if( is!=null ) {
                  try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

                      String factoryClassName = rd.readLine();

                      if (factoryClassName != null &&
                          ! "".equals(factoryClassName)) {
                          return newInstance(factoryClassName, moduleClassLoader);
                      }
                  }
              }
          } catch( Exception ex ) {
          }
        }

        if (fallbackClassName == null) {
            throw new ClassNotFoundException(
                    "Provider for " + factoryId + " cannot be found", null);
        }

        return newInstance(fallbackClassName, classLoader);
    }

    private static ClassLoader getModuleClassLoader() {
        try {
            final Class<?> moduleClass = Class.forName("org.jboss.modules.Module");
            final Class<?> moduleIdentifierClass = Class.forName("org.jboss.modules.ModuleIdentifier");
            final Class<?> moduleLoaderClass = Class.forName("org.jboss.modules.ModuleLoader");
            final Object moduleLoader;
            final SecurityManager sm = System.getSecurityManager();
            if (sm == null) {
                moduleLoader = moduleClass.getMethod("getBootModuleLoader").invoke(null);
            } else {
                try {
                    moduleLoader = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                        public Object run() throws Exception {
                            return moduleClass.getMethod("getBootModuleLoader").invoke(null);
                        }
                    });
                } catch (PrivilegedActionException pae) {
                    throw pae.getException();
                }
            }

            Object module;
            try {
                Object moduleIdentifier = moduleIdentifierClass.getMethod("create", String.class).invoke(null, RESTEASY_JAXRS_API_MODULE);
                module = moduleLoaderClass.getMethod("loadModule", moduleIdentifierClass).invoke(moduleLoader, moduleIdentifier);
            } catch (Exception e) {
                //ignore, module not found
                return null;
            }

            if (sm == null) {
                return (ClassLoader)moduleClass.getMethod("getClassLoader").invoke(module);
            }
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<ClassLoader>() {
                    @Override
                    public ClassLoader run() throws Exception {
                        return (ClassLoader) moduleClass.getMethod("getClassLoader").invoke(module);
                    }
                });
            } catch (PrivilegedActionException pae) {
                throw pae.getException();
            }
         } catch (ClassNotFoundException e) {
            //ignore, JBoss Modules might not be available at all
             return null;
         } catch (RuntimeException e) {
            throw e;
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
     }
}
