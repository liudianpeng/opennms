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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.opennms.core.test.xml.JsonTest;
import org.opennms.netmgt.flows.api.Directional;

import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MathTest {

    @Test
    public void canCalculateTimeInWindow() {
        // Range is same as window
        assertThat(FlowTimeSeriesProcessor.getTimeInWindow(0, 1, 0, 1), equalTo(1L));
        // Range is outside of window
        assertThat(FlowTimeSeriesProcessor.getTimeInWindow(0, 1, 1, 2), equalTo(0L));
        // Range is inside of window
        assertThat(FlowTimeSeriesProcessor.getTimeInWindow(0, 3, 1, 2), equalTo(1L));
        // Range is greater than window
        assertThat(FlowTimeSeriesProcessor.getTimeInWindow(1, 2, 0, 4), equalTo(1L));
        // Part of range is at beginning of window
        assertThat(FlowTimeSeriesProcessor.getTimeInWindow(1, 3, 1, 2), equalTo(1L));
        // Part of range is at end of window
        assertThat(FlowTimeSeriesProcessor.getTimeInWindow(1, 3, 2, 4), equalTo(1L));
    }

    @Test
    public void doIt() throws Exception {

        String flowJson = Files.toString(new File("/home/jesse/labs/drift/debug/flows.json"), StandardCharsets.UTF_8);
        Gson gson = new GsonBuilder().create();
        JsonObject flows = gson.fromJson(flowJson, JsonObject.class);
        final JsonArray hits = flows.getAsJsonObject("hits").getAsJsonArray("hits");

        List<FlowDocument> docs = new ArrayList<>();
        for (JsonElement el : hits) {
            final FlowDocument doc = new FlowDocument();
            final JsonObject obj = el.getAsJsonObject().getAsJsonObject("_source");
            doc.setBytes(obj.getAsJsonPrimitive("netflow.bytes").getAsLong());
            if (obj.has("netflow.application")) {
                doc.setApplication(obj.getAsJsonPrimitive("netflow.application").getAsString());
            } else {
                doc.setApplication("Other");
            }
            doc.setFirstSwitched(obj.getAsJsonPrimitive("netflow.first_switched").getAsLong());
            doc.setLastSwitched(obj.getAsJsonPrimitive("netflow.last_switched").getAsLong());
            doc.setInitiator(obj.getAsJsonPrimitive("netflow.initiator").getAsBoolean());
            docs.add(doc);
        }

        System.out.printf("Number of documents: %d\n", docs.size());
        long bytesOut = docs.stream()
                .filter(FlowDocument::isInitiator)
                .mapToLong(FlowDocument::getBytes)
                .sum();
        long bytesIn = docs.stream()
                .filter(d -> !d.isInitiator())
                .mapToLong(FlowDocument::getBytes)
                .sum();
        System.out.printf("Bytes in: %d, Bytes out: %d, Total: %d\n",
                bytesIn, bytesOut, bytesIn + bytesOut);

        // In:
        // 83989752 bytes
        // 82021.2421875 kilobytes
        // 80.09 megabytes

        // Out:
        // 532387
        // 519.9091796875 kilobytes

        long start = 1515093900000L;
        long end = 1515094200000L;
        double tally = 0.0d;
        for (FlowDocument doc : docs) {
            if (doc.getLastSwitched() < doc.getFirstSwitched()) {
                // TODO: Check this earlier
                continue;
            }
            if (start < doc.getLastSwitched() && end >= doc.getFirstSwitched()) {
                // We're in range, calculate the rate of the flow
                final double flowDurationInMs = (doc.getLastSwitched() - doc.getFirstSwitched());
                if (flowDurationInMs > 0) {
                    final double flowRateInMs = doc.getBytes() / flowDurationInMs;

                    // How long is the flow in the window for?
                    final long flowDurationInWindowInMs = FlowTimeSeriesProcessor.getTimeInWindow(start, end, doc.getFirstSwitched(), doc.getLastSwitched());

                    // Add to the tally
                    tally += flowRateInMs * flowDurationInWindowInMs;
                } else {
                    // Must be 0, so space it out evenly in the window
                    tally += doc.getBytes();
                }
            } else {
                throw new Exception("Flow outside of range!");
            }
        }
        System.out.printf("Tally: %.2f\n", tally);


        double tally2 = 0.0d;
        long step = 156;
        final Table<Directional<String>, Long, Double> results = FlowTimeSeriesProcessor.getSeriesFromDocs(docs, step, start, end);
        for (Directional<String> app : results.rowKeySet()) {
            for (Long t : results.columnKeySet()) {
                Double val = results.get(app, t);
                if (val != null) {
                    tally2 += Math.abs(val) * step / 1000;
                }
            }
        }
        System.out.printf("Tally2: %.2f\n", tally2);


        final FlowSeriesResponse response = new FlowSeriesResponse();
        response.setStart(start);
        response.setEnd(end);
        response.setLabels(results.rowKeySet().stream()
                .map((d) -> String.format("%s (%s)", d.getValue(), d.isSource() ? "In" : "Out"))
                .collect(Collectors.toList()));
        populateResponseFromTable(results, response);
        System.out.println(JsonTest.marshalToJson(response));


    }

    @Test
    public void testItGain() throws IOException {
        String restJson = Files.toString(new File("/home/jesse/labs/drift/debug/rest2.json"), StandardCharsets.UTF_8);
        FlowSeriesResponse res = JsonTest.unmarshalFromJson(restJson, FlowSeriesResponse.class);


        double tally = 0.0d;
        for (List<Double> column : res.getColumns()) {
            tally += column.stream()
                    .mapToDouble(Math::abs)
                    .sum();
        }
        System.out.printf("Tally3: %.2f, %.2f\n", tally, tally*30000/1000);
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
