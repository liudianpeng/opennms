/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.assets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.opennms.web.assets.api.AssetResource;
import org.springframework.util.FileCopyUtils;

public class AssetLocatorImplTest {
    private AssetLocatorImpl m_locator;

    @Before
    public void setUp() throws Exception {
        m_locator = new AssetLocatorImpl();
        m_locator.afterPropertiesSet();
    }

    @Test
    public void testGetAssets() throws Exception {
        assertNotNull(m_locator.getAssets());
        assertEquals(1, m_locator.getAssets().size());
        assertEquals("test-asset", m_locator.getAssets().iterator().next());
    }

    @Test
    public void testGetResources() throws Exception {
        final Collection<AssetResource> resources = m_locator.getResources("test-asset");
        assertNotNull(resources);
        assertEquals(1, resources.size());
        final AssetResource resource = resources.iterator().next();
        assertEquals("test-asset", resource.getAsset());
        assertEquals("js", resource.getType());
        assertEquals("assets/test.js", resource.getPath());
    }

    @Test
    public void testReadResource() throws Exception {
        final AssetResource r = new AssetResource("test-asset", "js", "assets/test.js");
        final InputStream is = r.open();
        final InputStreamReader isr = new InputStreamReader(is);
        final String contents = FileCopyUtils.copyToString(isr);
        assertTrue(contents.contains("yo"));
        assertTrue(contents.contains("console.log"));
    }
}
