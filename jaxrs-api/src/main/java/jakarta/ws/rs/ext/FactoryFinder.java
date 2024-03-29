/*
 * Copyright (c) 2010, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
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

    private FactoryFinder() {
        // prevents instantiation
    }

    private static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
    	// function, rather than anonymous subclass, goes back to at least official release 2.1.1. 
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
     * Creates an instance of the specified class using the specified {@code ClassLoader} object.
     *
     * @param className name of the class to be instantiated.
     * @param classLoader class loader to be used.
     * @return instance of the specified class.
     * @throws ClassNotFoundException if the given class could not be found or could not be instantiated.
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
            // getDecloaredConstructor() goes back to at least official release 2.1.1.
            return spiClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException x) {
            throw x;
        } catch (Exception x) {
            throw new ClassNotFoundException("Provider " + className + " could not be instantiated: " + x, x);
        }
    }

    // loadService() added in https://github.com/jboss/jboss-jaxrs-api_spec/pull/9.
    private static <T> Object loadService(Class<T> service, ClassLoader cl)
    {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
           Iterator<T> iterator = ServiceLoader.load(service, cl).iterator();
           if (iterator.hasNext())
           {
              return iterator.next();
           }
           else
           {
              return null;
           }
        } else {
           return AccessController.doPrivileged(new PrivilegedAction<Object>()
           {
               @Override
               public Object run()
               {
                  Iterator<T> iterator = ServiceLoader.load(service, cl).iterator();
                  if (iterator.hasNext())
                  {
                     return iterator.next();
                  }
                  else
                  {
                     return null;
                  }
               }
           });
        }
    }

    /**
     * Finds the implementation {@code Class} for the given factory name and create its instance.
     * <p>
     * This method is package private so that this code can be shared.
     *
     * @param factoryId the name of the factory to find, which is a system property.
     * @param service service to be found.
     * @param <T> type of the service to be found.
     * @return the instance of the specified service; may not be {@code null}.
     * @throws ClassNotFoundException if the given class could not be found or could not be instantiated.
     * 
     * fallbackClassName argument removed in https://github.com/eclipse-ee4j/jaxrs-api/pull/755.
     */
    static <T> Object find(final String factoryId, final Class<T> service) throws ClassNotFoundException {
        ClassLoader classLoader = getContextClassLoader();

        try {
            Object res = loadService(service, classLoader);
            if (res != null) {
               return res;
            }
        } catch (Exception | ServiceConfigurationError ex) {
            LOGGER.log(Level.FINER, "Failed to load service " + factoryId + ".", ex);
        }

        try {
            Object res = loadService(service, FactoryFinder.class.getClassLoader());
            if (res != null) {
               return res;
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
        	// finally clause goes back at least to official release 2.1.1.
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

        throw new ClassNotFoundException(
                "Provider for " + factoryId + " cannot be found", null);
    }
}
