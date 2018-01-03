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

package org.opennms.netmgt.flows.elastic;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opennms.netmgt.flows.api.NodeCriteria;

public class FlowBuilder {

    private final List<FlowDocument> flows = new ArrayList<>();

    private NodeDocument exporterNode;
    private Integer snmpInterfaceId;
    private boolean initiator = false;
    private String application = null;

    public FlowBuilder withExporter(String fs, String fid) {
        exporterNode = new NodeDocument();
        exporterNode.setForeignSource(fs);
        exporterNode.setForeignId(fid);
        exporterNode.setNodeCriteria(String.format("%s:%s", fs, fid));
        return this;
    }


    public FlowBuilder withSnmpInterfaceId(Integer snmpInterfaceId) {
        this.snmpInterfaceId = snmpInterfaceId;
        return this;
    }

    public FlowBuilder withInitiator(boolean initiator) {
        this.initiator = initiator;
        return this;
    }

    public FlowBuilder withApplication(String application) {
        this.application = application;
        return this;
    }

    public FlowBuilder withFlow(Date date, String sourceIp, int sourcePort, String destIp, int destPort, long numBytes) {
        return withFlow(date, date, sourceIp, sourcePort, destIp, destPort, numBytes);
    }

    public FlowBuilder withFlow(Date firstSwitched, Date lastSwitched, String sourceIp, int sourcePort, String destIp, int destPort, long numBytes) {
        final FlowDocument flow = new FlowDocument();
        flow.setTimestamp(lastSwitched.getTime());
        flow.setFirstSwitched(firstSwitched.getTime());
        flow.setLastSwitched(lastSwitched.getTime());
        flow.setSrcAddr(sourceIp);
        flow.setSrcPort(sourcePort);
        flow.setDstAddr(destIp);
        flow.setDstPort(destPort);
        flow.setBytes(numBytes);
        flow.setProtocol(6); // TCP
        if (exporterNode !=  null) {
            flow.setNodeExporter(exporterNode);
        }
        flow.setInputSnmp(snmpInterfaceId);
        flow.setOutputSnmp(snmpInterfaceId);
        flow.setInitiator(initiator);
        flow.setApplication(application);
        flows.add(flow);
        return this;
    }

    public List<FlowDocument> build() {
        return flows;
    }

}
