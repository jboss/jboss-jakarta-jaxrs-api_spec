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

package javax.ws.rs.core;

import org.junit.Test;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Variant;

import static org.junit.Assert.assertNull;

/**
 * Variant regression unit tests.
 *
 * @author Marek Potociar
 */
public class VariantTest {

    @Test
    public void npeInConstructor() {
        // Regression test for JAX_RS_SPEC-250
        new Variant(MediaType.TEXT_PLAIN_TYPE, (String) null, null);
        new Variant(MediaType.TEXT_PLAIN_TYPE, (String) null, "deflate");
    }

    @Test
    public void npeInGetLanguageString() {
        // Regression test for JAX_RS_SPEC-251
        final Variant variant = new Variant(MediaType.TEXT_PLAIN_TYPE, (String) null, null);
        assertNull(variant.getLanguageString());
    }
}
