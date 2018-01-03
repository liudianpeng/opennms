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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opennms.netmgt.flows.api.Directional;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

public class FlowTimeSeriesProcessor {

    public static Table<Directional<String>, Long, Double> getSeriesFromDocs(List<FlowDocument> docs, long step, Long start, Long end) {
        final ImmutableTable.Builder<Directional<String>, Long, Double> results = ImmutableTable.builder();
        if (docs.size() < 1) {
            return results.build();
        }

        if (start == null) {
            start = Long.MAX_VALUE;
        }
        if (end == null) {
            end = Long.MIN_VALUE;
        }

        // Group the flows by direction
        final List<FlowDocument> initiatorFlows = new ArrayList<>();
        final List<FlowDocument> nonInitiatorFlows = new ArrayList<>();
        for (FlowDocument doc : docs) {
            if (doc.isInitiator()) {
                initiatorFlows.add(doc);
            } else {
                nonInitiatorFlows.add(doc);
            }
            start = Math.min(start, doc.getFirstSwitched());
            end = Math.max(end, doc.getLastSwitched());
        }

        // Generate the timestamps
        final List<Long> timestamps = new ArrayList<>();
        for (long t = start; t < end; t += step) {
            timestamps.add(t);
        }

        doIt(true, initiatorFlows, step, timestamps, results);
        doIt(false, nonInitiatorFlows, step, timestamps, results);
        return results.build();
    }

    private static void doIt(boolean isSource, List<FlowDocument> docs, long step, List<Long> timestamps, ImmutableTable.Builder<Directional<String>, Long, Double> res) {
        // Group the flows by application
        final Map<String, List<FlowDocument>> docsByApp = new LinkedHashMap<>();
        for (FlowDocument doc : docs) {
            final List<FlowDocument> appDocs = docsByApp.computeIfAbsent(doc.getApplication(), k -> new ArrayList<>());
            appDocs.add(doc);
        }

        for (String app : docsByApp.keySet()) {
            doItAgain(app, isSource, docsByApp.get(app), step, timestamps, res);
        }
    }

    private static void doItAgain(String app, boolean isSource, List<FlowDocument> docs, long step, List<Long> timestamps, ImmutableTable.Builder<Directional<String>, Long, Double> res) {
        // Sort by first switched
        docs.sort(Comparator.comparingLong(FlowDocument::getFirstSwitched));

        for (Long t : timestamps) {
            final long windowStart = t;
            final long windowEnd = t + step;
            double tally = 0.0d;
            for (FlowDocument doc : docs) {
                if (doc.getLastSwitched() < doc.getFirstSwitched()) {
                    // TODO: Check this earlier
                    continue;
                }

                if (windowStart < doc.getLastSwitched() && windowEnd >= doc.getFirstSwitched()) {
                    // We're in range, calculate the rate of the flow
                    final double flowDurationInSecs = (doc.getLastSwitched() - doc.getFirstSwitched()) * 1000d;
                    if (flowDurationInSecs > 0) {
                        final double flowRateInSecs = doc.getBytes() / flowDurationInSecs;

                        // How long is the flow in the window for?
                        long flowDurationInWindowInMs;
                        if (doc.getLastSwitched() >= windowEnd) {
                            flowDurationInWindowInMs = windowEnd - doc.getFirstSwitched();
                        } else {
                            flowDurationInWindowInMs = doc.getLastSwitched() - windowStart;
                        }
                        flowDurationInWindowInMs = Math.min(flowDurationInWindowInMs, step);

                        // Add to the tally
                        tally += flowRateInSecs * flowDurationInWindowInMs * 1000;
                    } else {
                        // Must be 0, so space it out evenly in the window
                        tally += doc.getBytes();
                    }

                }
            }
            double multiplier = 1;
            if (!isSource) {
                multiplier *= -1;
            }


            res.put(new Directional<>(app, isSource), t, tally / step * 1000 * multiplier);
        }
    }
}