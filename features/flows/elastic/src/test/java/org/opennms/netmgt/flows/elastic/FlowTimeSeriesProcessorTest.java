/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.opennms.netmgt.flows.api.Directional;

import com.google.common.collect.Table;

public class FlowTimeSeriesProcessorTest {

    @Test
    public void canGenerateTimeSeries() {
        final List<FlowDocument> flows = new FlowBuilder()
                .withExporter("SomeFs", "SomeFid")
                .withSnmpInterfaceId(1)
                // 192.168.1.100:43444 <-> 10.1.1.11:80 (100 bytes in [3,15])
                .withApplication("http").withInitiator(true)
                .withFlow(new Date(3000), new Date(15000), "192.168.1.100", 43444, "10.1.1.11", 80, 100)
                .withInitiator(false)
                .withFlow(new Date(3000), new Date(15000), "10.1.1.11", 80, "192.168.1.100", 43444, 100)
                // 192.168.1.100:43445 <-> 10.1.1.12:443
                .withApplication("https").withInitiator(true)
                .withFlow(new Date(13000), new Date(26000), "192.168.1.100", 43445, "10.1.1.12", 443, 200)
                .withInitiator(false)
                .withFlow(new Date(13000), new Date(26000), "10.1.1.12", 443, "192.168.1.100", 43445, 200)
                .build();

        final Table<Directional<String>, Long, Double> results = FlowTimeSeriesProcessor.getSeriesFromDocs(flows, 5000, 0L, 30000L);
        final TimeSeriesResponse response = new TimeSeriesResponse();
        populateResponseFromTable(results, response);

        assertThat(response.labels, contains("http (In)", "https (In)", "http (Out)", "https (Out)"));
        assertThat(response.timestamps, contains(0L, 5000L, 10000L, 15000L, 20000L, 25000L));
        assertArrayEquals(new double[] {3.333, 8.333, 8.333, 0, 0, 0}, toArray(response.columns.get(0)), 1E-1);
        assertArrayEquals(new double[] {0, 0, 6.154, 15.385, 15.385, 3.077}, toArray(response.columns.get(1)), 1E-1);
        assertArrayEquals(new double[] {-3.333, -8.333, -8.333, 0, 0, 0}, toArray(response.columns.get(2)), 1E-1);
        assertArrayEquals(new double[] {0, 0, -6.154, -15.385, -15.385, -3.077}, toArray(response.columns.get(3)), 1E-1);
    }

    private static double[] toArray(List<Double> list) {
        return list.stream().mapToDouble(Number::doubleValue).toArray();
    }

    private static class TimeSeriesResponse {
        private List<String> labels;
        private List<Long> timestamps;
        private List<List<Double>> columns;
    }


    private static void populateResponseFromTable(Table<Directional<String>, Long, Double> table, TimeSeriesResponse response) {
        response.labels = table.rowKeySet().stream()
                .map((d) -> String.format("%s (%s)", d.getValue(), d.isSource() ? "In" : "Out"))
                .collect(Collectors.toList());

        final List<Long> timestamps = table.columnKeySet().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        final List<List<Double>> columns = new LinkedList<>();
        for (Object rowKey : table.rowKeySet()) {
            final List<Double> column = new ArrayList<>(timestamps.size());
            for (Long ts : timestamps) {
                Double val = table.get(rowKey, ts);
                if (val == null || Double.isNaN(val)) {
                    val = 0d;
                }
                column.add(val);
            }
            columns.add(column);
        }

        response.timestamps = timestamps;
        response.columns = columns;
    }

    private static void populateResponseFromTable(Table<?, Long, Double> table, FlowSeriesResponse response) {
        final List<Long> timestamps = table.columnKeySet().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        final List<List<Double>> columns = new LinkedList<>();
        for (Object rowKey : table.rowKeySet()) {
            final List<Double> column = new ArrayList<>(timestamps.size());
            for (Long ts : timestamps) {
                Double val = table.get(rowKey, ts);
                if (val == null || Double.isNaN(val)) {
                    val = 0d;
                }
                column.add(val);
            }
            columns.add(column);
        }

        response.setTimestamps(timestamps);
        response.setColumns(columns);
    }
}
