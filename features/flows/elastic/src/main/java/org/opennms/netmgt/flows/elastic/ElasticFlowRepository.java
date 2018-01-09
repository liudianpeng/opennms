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

package org.opennms.netmgt.flows.elastic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opennms.netmgt.flows.api.ConversationKey;
import org.opennms.netmgt.flows.api.Directional;
import org.opennms.netmgt.flows.api.FlowException;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.flows.api.FlowSource;
import org.opennms.netmgt.flows.api.NF5Packet;
import org.opennms.netmgt.flows.api.NodeCriteria;
import org.opennms.netmgt.flows.api.TrafficSummary;
import org.opennms.netmgt.flows.filter.api.Filter;
import org.opennms.netmgt.flows.filter.api.TimeRangeFilter;
import org.opennms.plugins.elasticsearch.rest.BulkResultWrapper;
import org.opennms.plugins.elasticsearch.rest.FailedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;
import io.searchbox.core.Bulk;
import io.searchbox.core.ClearScroll;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.core.search.aggregation.DateHistogramAggregation;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.SumAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.params.Parameters;

public class ElasticFlowRepository implements FlowRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticFlowRepository.class);

    private static final String TYPE = "netflow";

    private final JestClient client;

    private final IndexStrategy indexStrategy;

    private final DocumentEnricher documentEnricher;

    private final Netflow5Converter converter = new Netflow5Converter();

    private final SearchQueryProvider searchQueryProvider = new SearchQueryProvider();

    /**
     * Flows/second throughput
     */
    private final Meter flowsPersistedMeter;

    /**
     * Time taken to convert and enrich the flows in a log
     */
    private final Timer logEnrichementTimer;

    /**
     * Time taken to persist the flows in a log
     */
    private final Timer logPersistingTimer;


    /**
     * Number of flows in a log
     */
    private final Histogram flowsPerLog;


    public ElasticFlowRepository(MetricRegistry metricRegistry, JestClient jestClient, IndexStrategy indexStrategy, DocumentEnricher documentEnricher) {
        this.client = Objects.requireNonNull(jestClient);
        this.indexStrategy = Objects.requireNonNull(indexStrategy);
        this.documentEnricher = Objects.requireNonNull(documentEnricher);

        flowsPersistedMeter = metricRegistry.meter("flowsPersisted");
        logEnrichementTimer = metricRegistry.timer("logEnrichment");
        logPersistingTimer = metricRegistry.timer("logPersisting");
        flowsPerLog = metricRegistry.histogram("flowsPerLog");
    }

    @Override
    public void persistNetFlow5Packets(Collection<? extends NF5Packet> packets, FlowSource source) throws FlowException {
        LOG.debug("Converting {} Netflow 5 packets from {} to flow documents.", packets.size(), source);
        final List<FlowDocument> flowDocuments = packets.stream()
                .map(converter::convert)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        enrichAndPersistFlows(flowDocuments, source);
    }

    public void enrichAndPersistFlows(List<FlowDocument> flowDocuments, FlowSource source) throws FlowException {
        // Track the number of flows per call
        flowsPerLog.update(flowDocuments.size());

        if (flowDocuments.isEmpty()) {
            LOG.info("Received empty flows. Nothing to do.");
            return;
        }

        LOG.debug("Enriching {} flow documents.", flowDocuments.size());
        try (final Timer.Context ctx = logEnrichementTimer.time()) {
            documentEnricher.enrich(flowDocuments, source);
        }

        LOG.debug("Persisting {} flow documents.", flowDocuments.size());
        try (final Timer.Context ctx = logPersistingTimer.time()) {
            final String index = indexStrategy.getIndex(new Date());

            final Bulk.Builder bulkBuilder = new Bulk.Builder();
            for (FlowDocument flowDocument : flowDocuments) {
                final Index.Builder indexBuilder = new Index.Builder(flowDocument)
                        .index(index)
                        .type(TYPE);
                bulkBuilder.addAction(indexBuilder.build());
            }
            final Bulk bulk = bulkBuilder.build();
            final BulkResultWrapper result = new BulkResultWrapper(executeRequest(bulk));
            if (!result.isSucceeded()) {
                final List<FailedItem<FlowDocument>> failedFlows = result.getFailedItems(flowDocuments);
                throw new PersistenceException(result.getErrorMessage(), failedFlows);
            }

            flowsPersistedMeter.mark(flowDocuments.size());
        }
    }

    private static class ScrollState {
        private long size = 1000;
        private String scrollTimeout = "5m";
        private List<FlowDocument> docs = new ArrayList<>();
    }

    private CompletableFuture<JestResult> doScroll(JestResult res, ScrollState state) {
        try {
            final String scrollId = res.getJsonObject().getAsJsonPrimitive("_scroll_id").getAsString();
            final JsonArray hits = res.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");
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
                state.docs.add(doc);
            }

            if (hits.size() < state.size) {
                // We're done
                final ClearScroll clearScroll = new ClearScroll.Builder().addScrollId(scrollId).build();
                return executeAsync(clearScroll);
            } else {
                // Chain
                final SearchScroll scroll = new SearchScroll.Builder(scrollId, state.scrollTimeout).build();
                return executeAsync(scroll).thenCompose(nestedRes -> doScroll(nestedRes, state));
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public CompletableFuture<Table<Directional<String>, Long, Double>> getSeries2(long step, List<Filter> filters) {
        final ScrollState state = new ScrollState();
        final Search search = new Search.Builder(searchQueryProvider.getSeriesQuery2(state.size, filters))
                .addType(TYPE)
                .addSort(new Sort("_doc")) // fastest
                .setParameter(Parameters.SCROLL, state.scrollTimeout)
                .build();

        Optional<TimeRangeFilter> timeRangeFilterOptional = filters.stream()
                .filter(f -> f instanceof TimeRangeFilter)
                .map(f -> (TimeRangeFilter)f)
                .findFirst();
        Long start = null;
        Long end = null;
        if (timeRangeFilterOptional.isPresent()) {
            start = timeRangeFilterOptional.get().getStart();
            end = timeRangeFilterOptional.get().getEnd();
        }

        Long finalStart = start;
        Long finalEnd = end;
        return executeAsync(search).thenCompose(res -> doScroll(res, state))
                .thenApply(res -> FlowTimeSeriesProcessor.getSeriesFromDocs(state.docs, step, finalStart, finalEnd));
    }

    @Override
    public CompletableFuture<Table<Directional<String>, Long, Double>> getSeries(long step, List<Filter> filters) {
        Optional<TimeRangeFilter> timeRangeFilterOptional = filters.stream()
                .filter(f -> f instanceof TimeRangeFilter)
                .map(f -> (TimeRangeFilter)f)
                .findFirst();
        if (!timeRangeFilterOptional.isPresent()) {
            throw new IllegalStateException("Need start and end");
        }

        final Long start = timeRangeFilterOptional.get().getStart();
        final Long end = timeRangeFilterOptional.get().getEnd();

        final String query = searchQueryProvider.getSeriesWithPlugin(start, end, step, "netflow.application", filters);
        return searchAsync(query).thenApply(res -> {
            // Build a table using the search results
            final ImmutableTable.Builder<Directional<String>, Long, Double> results = ImmutableTable.builder();
            final MetricAggregation aggs = res.getAggregations();
            final TermsAggregation directionAgg = aggs.getTermsAggregation("direction");
            for (TermsAggregation.Entry directionBucket : directionAgg.getBuckets()) {
                final boolean isInitiator = Boolean.valueOf(directionBucket.getKeyAsString());
                final TermsAggregation groupedBy = directionBucket.getTermsAggregation("grouped_by");
                for (TermsAggregation.Entry groupedByBucket : groupedBy.getBuckets()) {
                    final TimeSliceAggregation bytesAggs = groupedByBucket.getAggregation("bytes_over_time", TimeSliceAggregation.class);
                    for (TimeSliceAggregation.DateHistogram dateHistogram : bytesAggs.getBuckets()) {
                        final Long time = dateHistogram.getTime();
                        final Double bytes = dateHistogram.getValue();
                        results.put(new Directional<>(groupedByBucket.getKey(), isInitiator), time, bytes);
                    }
                }
            }
            return results.build();
        });
    }

    @Override
    public CompletableFuture<Set<NodeCriteria>> getExportersWithFlows(int limit, List<Filter> filters) {
        final String query = searchQueryProvider.getUniqueNodeExporters(limit, filters);
        return searchAsync(query).thenApply(res -> res.getAggregations().getTermsAggregation("criterias")
                .getBuckets()
                .stream()
                .map(e -> new NodeCriteria(e.getKey()))
                .collect(Collectors.toSet()));
    }

    @Override
    public CompletableFuture<Set<Integer>> getSnmpInterfaceIdsWithFlows(int limit, List<Filter> filters) {
        final String query = searchQueryProvider.getUniqueSnmpInterfaces(limit, filters);
        return searchAsync(query).thenApply(res -> {
            final Set<Integer> interfaces = Sets.newHashSet();
            res.getAggregations().getTermsAggregation("input_snmp").getBuckets()
                    .stream()
                    .map(TermsAggregation.Entry::getKey)
                    .map(Integer::valueOf)
                    .forEach(interfaces::add);
            res.getAggregations().getTermsAggregation("output_snmp").getBuckets()
                    .stream()
                    .map(TermsAggregation.Entry::getKey)
                    .map(Integer::valueOf)
                    .forEach(interfaces::add);
            return interfaces;
        });
    }

    @Override
    public CompletableFuture<Long> getFlowCount(List<Filter> filters) {
        final String query = searchQueryProvider.getFlowCountQuery(filters);
        return searchAsync(query).thenApply(SearchResult::getTotal);
    }

    @Override
    public CompletableFuture<List<TrafficSummary<String>>> getTopNApplications(int N, List<Filter> filters) {
        return getTotalBytesFromTopN(N, "netflow.application", filters);
    }

    @Override
    public CompletableFuture<Table<Directional<String>, Long, Double>> getTopNApplicationsSeries(int N, long step, List<Filter> filters) {
        return getSeriesFromTopN(N, step, "netflow.application", filters).thenApply((res) -> mapTable(res, s -> s));
    }

    @Override
    public CompletableFuture<List<TrafficSummary<ConversationKey>>> getTopNConversations(int N, List<Filter> filters) {
        return getTotalBytesFromTopN(N, "netflow.convo_key", filters).thenApply((res) -> res.stream()
                .map(summary -> {
                    // Map the strings to the corresponding conversation keys
                    final TrafficSummary<ConversationKey> out = new TrafficSummary<>(ConversationKeyUtils.fromJsonString(summary.getEntity()));
                    out.setBytesIn(summary.getBytesIn());
                    out.setBytesOut(summary.getBytesOut());
                    return out;
                })
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Table<Directional<ConversationKey>, Long, Double>> getTopNConversationsSeries(int N, long step, List<Filter> filters) {
        return getSeriesFromTopN(N, step, "netflow.convo_key", filters).thenApply((res) -> mapTable(res, ConversationKeyUtils::fromJsonString));
    }

    private CompletableFuture<List<String>> getTopN(int N, String groupByTerm, List<Filter> filters) {
        // Increase the multiplier for increased accuracy
        // See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html#_size
        final int multiplier = 2;
        final String query = searchQueryProvider.getTopNQuery(multiplier*N, groupByTerm, filters);
        return searchAsync(query).thenApply(res -> res.getAggregations().getTermsAggregation("grouped_by").getBuckets().stream()
                .map(TermsAggregation.Entry::getKey)
                .limit(N)
                .collect(Collectors.toList()));
    }

    private CompletableFuture<Table<Directional<String>, Long, Double>> getSeriesFromTopN(List<String> topN, long step, String groupByTerm, List<Filter> filters) {
        final String query = searchQueryProvider.getSeriesFromTopNQuery(topN, step, groupByTerm, filters);
        return searchAsync(query).thenApply(res -> {
            // Build a table using the search results
            final ImmutableTable.Builder<Directional<String>, Long, Double> results = ImmutableTable.builder();
            final MetricAggregation aggs = res.getAggregations();
            final TermsAggregation groupedBy = aggs.getTermsAggregation("grouped_by");
            for (TermsAggregation.Entry groupedByBucket : groupedBy.getBuckets()) {
                final DateHistogramAggregation bytesAggs = groupedByBucket.getDateHistogramAggregation("bytes_over_time");
                for (DateHistogramAggregation.DateHistogram dateHistogram : bytesAggs.getBuckets()) {
                    final Long time = dateHistogram.getTime();
                    final TermsAggregation directionAgg = dateHistogram.getTermsAggregation("direction");

                    // Make sure we have values for both directions, since we may only have one bucket returned here
                    Double bytesWhenInitiator = Double.NaN;
                    Double bytesWhenNotInitiator = Double.NaN;
                    for (TermsAggregation.Entry directionBucket : directionAgg.getBuckets()) {
                        final boolean isInitiator = Boolean.valueOf(directionBucket.getKeyAsString());
                        final SumAggregation sumAgg = directionBucket.getSumAggregation("total_bytes");
                        if (isInitiator) {
                            bytesWhenInitiator = sumAgg.getSum();
                        } else {
                            bytesWhenNotInitiator = sumAgg.getSum();
                        }
                    }
                    results.put(new Directional<>(groupedByBucket.getKey(), true), time, bytesWhenInitiator);
                    results.put(new Directional<>(groupedByBucket.getKey(), false), time, bytesWhenNotInitiator);
                }
            }
            return results.build();
        });
    }

    private CompletableFuture<Table<Directional<String>, Long, Double>> getSeriesFromTopN(int N, long step,
                                                                                          String groupByTerm,
                                                                                          List<Filter> filters) {
        return getTopN(N, groupByTerm, filters)
                .thenCompose((topN) -> getSeriesFromTopN(topN, step, groupByTerm, filters));
    }

    private CompletableFuture<List<TrafficSummary<String>>> getTotalBytesFromTopN(List<String> topN, String groupByTerm,
                                                                                  List<Filter> filters) {
        final String query = searchQueryProvider.getTotalBytesFromTopNQuery(topN, groupByTerm, filters);
        return searchAsync(query).thenApply(res -> {
            // Build the traffic summaries from the search results
            final Map<String, TrafficSummary<String>> summaries = new HashMap<>();
            final MetricAggregation aggs = res.getAggregations();
            final TermsAggregation groupedBy = aggs.getTermsAggregation("grouped_by");
            for (TermsAggregation.Entry bucket : groupedBy.getBuckets()) {
                final TrafficSummary<String> trafficSummary = new TrafficSummary<>(bucket.getKey());
                final TermsAggregation directionAgg = bucket.getTermsAggregation("direction");
                for (TermsAggregation.Entry directionBucket : directionAgg.getBuckets()) {
                    final boolean isInitiator = Boolean.valueOf(directionBucket.getKeyAsString());
                    final SumAggregation sumAgg = directionBucket.getSumAggregation("total_bytes");
                    final Double sum = sumAgg.getSum();
                    if (!isInitiator) {
                        trafficSummary.setBytesOut(sum.longValue());
                    } else {
                        trafficSummary.setBytesIn(sum.longValue());
                    }
                }
                summaries.put(bucket.getKey(), trafficSummary);
            }
            // Now build a list in the same order as the given top N list
            final List<TrafficSummary<String>> topNRes = new ArrayList<>(topN.size());
            for (String topNEntry : topN) {
                final TrafficSummary<String> summary = summaries.get(topNEntry);
                if (summary != null) {
                    topNRes.add(summary);
                }
            }
            return topNRes;
        });
    }

    private CompletableFuture<List<TrafficSummary<String>>> getTotalBytesFromTopN(int N, String groupByTerm,
                                                                                  List<Filter> filters) {
        return getTopN(N, groupByTerm, filters)
                .thenCompose((topN) -> getTotalBytesFromTopN(topN, groupByTerm, filters));
    }

    private <T extends JestResult> T executeRequest(Action<T> clientRequest) throws FlowException {
        try {
            return client.execute(clientRequest);
        } catch (IOException ex) {
            LOG.error("An error occurred while executing the given request: {}", clientRequest, ex);
            throw new FlowException(ex.getMessage(), ex);
        }
    }

    private CompletableFuture<SearchResult> searchAsync(String query) {
        LOG.debug("Executing asynchronous query: {}", query);
        return executeAsync(new Search.Builder(query)
                .addType(TYPE)
                .build());
    }

    private <T extends JestResult> CompletableFuture<T> executeAsync(Action<T> action) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        client.executeAsync(action, new JestResultHandler<T>() {
            @Override
            public void completed(T result) {
                if (!result.isSucceeded()) {
                    future.completeExceptionally(new Exception(result.getErrorMessage()));
                } else {
                    future.complete(result);
                }
            }
            @Override
            public void failed(Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    /**
     * Rebuilds the table, mapping the row keys using the given function and fills
     * in missing cells with NaN values.
     */
    private static <T> Table<Directional<T>, Long, Double> mapTable(Table<Directional<String>, Long, Double> source, Function<String, T> fn) {
        final ImmutableTable.Builder<Directional<T>, Long, Double> target = ImmutableTable.builder();
        final Set<Long> columnKeys = source.columnKeySet();
        for (Directional<String> sourceRowKey : source.rowKeySet()) {
            final Directional<T> targetRowKey = new Directional<>(fn.apply(sourceRowKey.getValue()), sourceRowKey.isSource());
            for (Long columnKey : columnKeys) {
                Double value = source.get(sourceRowKey, columnKey);
                if (value == null) {
                    value = Double.NaN;
                }
                target.put(targetRowKey, columnKey, value);
            }
        }
        return target.build();
    }
}
