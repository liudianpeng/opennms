/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2007-2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.dao.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsResource;
import org.opennms.netmgt.model.OnmsResourceType;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.ResourceTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

/**
 * Interface SNMP resources are stored in paths like:
 *   snmp/1/${IfName}/ds.rrd
 *
 */
public class InterfaceSnmpByIfIndexResourceType implements OnmsResourceType {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceSnmpByIfIndexResourceType.class);

    private final InterfaceSnmpResourceType m_interfaceSnmpResourceType;

    public InterfaceSnmpByIfIndexResourceType(InterfaceSnmpResourceType interfaceSnmpResourceType) {
        m_interfaceSnmpResourceType = Objects.requireNonNull(interfaceSnmpResourceType);
    }

    /**
     * <p>getName</p>
     *
     * @return a {@link String} object.
     */
    @Override
    public String getName() {
        return "interfaceSnmpByIfIndex";
    }

    /**
     * <p>getLabel</p>
     *
     * @return a {@link String} object.
     */
    @Override
    public String getLabel() {
        return "SNMP Interface Data (by ifIndex)";
    }

    /** {@inheritDoc} */
    @Override
    public String getLinkForResource(OnmsResource resource) {
        return null;
    }

    @Override
    public boolean isResourceTypeOnParent(OnmsResource parent) {
        return m_interfaceSnmpResourceType.isResourceTypeOnParent(parent);
    }

    @Override
    public List<OnmsResource> getResourcesForParent(OnmsResource parent) {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    public OnmsResource getChildByName(final OnmsResource parent, final String name) {
        // Grab the node entity
        final OnmsNode node = ResourceTypeUtils.getNodeFromResource(parent);

        // Determine the ifIndex
        final int ifIndex = Integer.parseInt(name);

        // Find the associated SNMP interface
        final OnmsSnmpInterface snmpInterface = node.getSnmpInterfaceWithIfIndex(ifIndex);
        if (snmpInterface == null) {
            return null;
        }

        // Find the matching path
        final Set<String> interfaceKeys = Sets.newHashSet();
        interfaceKeys.addAll(Arrays.asList(m_interfaceSnmpResourceType.getKeysFor(snmpInterface)));
        final Optional<String> path = m_interfaceSnmpResourceType.getQueryableInterfaces(parent).stream()
                .filter(interfaceKeys::contains)
                .findFirst();
        if (!path.isPresent()) {
            return null;
        }

        return m_interfaceSnmpResourceType.getChildByName(parent, path.get());
    }
}
