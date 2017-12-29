/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.flows.rest;

import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.opennms.netmgt.flows.rest.model.FlowSeriesResponse;
import org.opennms.netmgt.flows.rest.model.FlowSummaryResponse;
import org.opennms.netmgt.flows.rest.model.NodeDetails;
import org.opennms.netmgt.flows.rest.model.NodeSummary;
import org.opennms.netmgt.flows.rest.model.SnmpInterface;

@Path("flows")
public interface FlowRestService {

    String DEFAULT_START_MS = "-14400000"; // 4 hours in millis
    String DEFAULT_END_MS = "0"; // Resolves to "now" when queried
    String DEFAULT_STEP_MS = "300000"; // 5 minutes
    String DEFAULT_TOP_N = "10";
    String DEFAULT_LIMIT = "10";

    @GET
    @Path("count")
    Long getFlowCount(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end
    );

    @GET
    @Path("exporters")
    @Produces(MediaType.APPLICATION_JSON)
    List<NodeSummary> getFlowExporters(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end,
            @DefaultValue(DEFAULT_LIMIT) @QueryParam("limit") final int limit
    );

    @GET
    @Path("exporters/{nodeCriteria}")
    @Produces(MediaType.APPLICATION_JSON)
    NodeDetails getFlowExporterInterfaces(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end,
            @DefaultValue(DEFAULT_LIMIT) @QueryParam("limit") final int limit,
            @PathParam("nodeCriteria") String nodeCriteria
    );

    @GET
    @Path("applications/series")
    @Produces(MediaType.APPLICATION_JSON)
    FlowSeriesResponse getTopNApplicationSeries(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end,
            @DefaultValue(DEFAULT_STEP_MS) @QueryParam("step") final long step,
            @DefaultValue(DEFAULT_TOP_N) @QueryParam("N") final int N,
            @QueryParam("exporterNode") final String exporterNodeCriteria,
            @QueryParam("snmpInterfaceId") final Integer snmpInterfaceId
    );

    @GET
    @Path("applications")
    @Produces(MediaType.APPLICATION_JSON)
    FlowSummaryResponse getTopNApplications(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end,
            @DefaultValue(DEFAULT_TOP_N) @QueryParam("N") final int N
    );

    @GET
    @Path("conversations")
    @Produces(MediaType.APPLICATION_JSON)
    FlowSummaryResponse getTopNConversations(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end,
            @DefaultValue(DEFAULT_TOP_N) @QueryParam("N") final int N
    );

    @GET
    @Path("conversations/series")
    @Produces(MediaType.APPLICATION_JSON)
    FlowSeriesResponse getTopNConversationsSeries(
            @DefaultValue(DEFAULT_START_MS) @QueryParam("start") final long start,
            @DefaultValue(DEFAULT_END_MS) @QueryParam("end") final long end,
            @DefaultValue(DEFAULT_STEP_MS) @QueryParam("step") final long step,
            @DefaultValue(DEFAULT_TOP_N) @QueryParam("N") final int N
    );

}
