/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package jakarta.ws.rs.client;

import java.io.IOException;

/**
 * An extension interface implemented by client request filters.
 *
 * Filters implementing this interface MUST be annotated with
 * {@link jakarta.ws.rs.ext.Provider &#64;Provider} to be discovered by the
 * runtime. This type of filters is supported only as part of the Client API.
 *
 * @author Marek Potociar
 * @author Santiago Pericas-Geertsen
 * @see jakarta.ws.rs.client.ClientResponseFilter
 * @since 2.0
 */
public interface ClientRequestFilter {

    /**
     * Filter method called before a request has been dispatched to a client
     * transport layer.
     *
     * Filters in the filter chain are ordered according to their {@code javax.annotation.Priority}
     * class-level annotation value.
     *
     * @param requestContext request context.
     * @throws IOException if an I/O exception occurs.
     */
    public void filter(ClientRequestContext requestContext) throws IOException;
}
