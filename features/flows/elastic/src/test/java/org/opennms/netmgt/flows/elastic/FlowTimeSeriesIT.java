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

import static com.jayway.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.elastic.ElasticSearchRule;
import org.opennms.core.test.elastic.ElasticSearchServerConfig;
import org.opennms.netmgt.flows.api.Directional;
import org.opennms.netmgt.flows.api.FlowException;
import org.opennms.netmgt.flows.api.FlowSource;
import org.opennms.netmgt.flows.elastic.template.IndexSettings;
import org.opennms.netmgt.flows.filter.api.Filter;
import org.opennms.netmgt.flows.filter.api.TimeRangeFilter;
import org.opennms.plugins.elasticsearch.rest.RestClientFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;

import io.searchbox.client.JestClient;

// "Bytes" were transferred between "First switched" and "Last switched"
// Avg. Bytes per Sec in this interval is "Bytes" / ("Last" - "First")

//     *--- 100 ---*
//               *--- 200 --- *
//  $....$....$....$....$....$....$....$....$....$....$....$....$
//  0    5    1    1    2    2    3    3    4    4    5    5    6
//            0    5    0    5    0    5    0    5    0    5    0


// Need to make sure that the total bytes match

// 100 bytes in [3,15] = 8.33 bps
// 200 bytes in [13,26] = 15.38 bps
// = 300 bytes total

// Expected data points:
// (0, 16.66)  = 8.33 * 2
// (5, 41.65)  = 8.33 * 5
// (10, 72.41) = (8.33 * 5) + (15.38 * 2)
// (15, 76.9) = (15.38 * 5)
// (20, 76.9) = (15.38 * 5)
// (25, 15.38) = (15.38 * 1)
// (30, 0)
// = 299.9

// A record falls in the bucket if (key + interval) >= "first" &&  (key < "last")
// A record can fall into multiple buckets
// Rate in bucket = portion of time in bucket * rate of record

// How:
// ES aggregation plugin (+maintenance)
// Run-time aggregation (retrieve all records) (+network, +cpu)
// Store time series -> Each flow record becomes multiple time series records (+disk)


// Aggregate by both first and last?
// Can't use the bucket key as part of the equation: https://discuss.elastic.co/t/bucket-selector-aggregation-on-date-histogram--key/80986
public class FlowTimeSeriesIT {

    private static final String HTTP_PORT = "9205";
    private static final String HTTP_TRANSPORT_PORT = "9305";

    @Rule
    public ElasticSearchRule elasticServerRule = new ElasticSearchRule(
            new ElasticSearchServerConfig()
                    .withDefaults()
                    .withSetting("http.enabled", true)
                    .withSetting("http.port", HTTP_PORT)
                    .withSetting("http.type", "netty4")
                    .withSetting("transport.type", "netty4")
                    .withSetting("transport.tcp.port", HTTP_TRANSPORT_PORT)
    );

    private ElasticFlowRepository flowRepository;

    @Before
    public void setUp() throws MalformedURLException, FlowException, ExecutionException, InterruptedException {
        MockLogAppender.setupLogging(true, "DEBUG");
        final MockDocumentEnricherFactory mockDocumentEnricherFactory = new MockDocumentEnricherFactory();
        final DocumentEnricher documentEnricher = mockDocumentEnricherFactory.getEnricher();

        final MetricRegistry metricRegistry = new MetricRegistry();
        final RestClientFactory restClientFactory = new RestClientFactory("http://localhost:" + HTTP_PORT, null, null);
        final JestClient client = restClientFactory.createClient();
        flowRepository = new ElasticFlowRepository(metricRegistry, client, IndexStrategy.MONTHLY, documentEnricher);
        final IndexSettings settings = new IndexSettings();
        final ElasticFlowRepositoryInitializer initializer = new ElasticFlowRepositoryInitializer(client, settings);

        // Here we load the flows by building the documents ourselves,
        // so we must initialize the repository manually
        initializer.initialize();

        // The repository should be empty
        assertThat(flowRepository.getFlowCount(getFilters()).get(), equalTo(0L));

        // Load the default set of flows
        loadDefaultFlows();
    }

    @Test
    public void canGetSeries() throws ExecutionException, InterruptedException {
        Table<Directional<String>, Long, Double> appTraffic = flowRepository.getSeries(5000, getFilters()).get();
        assertThat(appTraffic.rowKeySet(), hasSize(4));
        assertThat(appTraffic.get(new Directional<>("http", true), 0), closeTo(0.0d, 0.0001d));
        assertThat(appTraffic.get(new Directional<>("http", false), 0), closeTo(0.0d, 0.0001d));
    }

    private void loadDefaultFlows() throws FlowException {
        // 100 bytes in [3,15] = 8.33 bps
        // 200 bytes in [13,26] = 15.38 bps
        // = 300 bytes total
        final List<FlowDocument> flows = new FlowBuilder()
                .withExporter("SomeFs", "SomeFid")
                .withSnmpInterfaceId(1)
                // 192.168.1.100:43444 <-> 10.1.1.11:80 (100 bytes in [3,15])
                .withFlow(new Date(3), new Date(15), "192.168.1.100", 43444, "10.1.1.11", 80, 100)
                .withFlow(new Date(3), new Date(15), "10.1.1.11", 80, "192.168.1.100", 43444, 100)
                // 192.168.1.100:43445 <-> 10.1.1.12:443
                .withFlow(new Date(13), new Date(26), "192.168.1.100", 43445, "10.1.1.12", 443, 200)
                .withFlow(new Date(13), new Date(26), "10.1.1.12", 443, "192.168.1.100", 43445, 200)
                .build();
        flowRepository.enrichAndPersistFlows(flows, new FlowSource("test", "127.0.0.1"));

        // Retrieve all the flows we just persisted
        await().atMost(30, TimeUnit.SECONDS).until(() -> flowRepository.getFlowCount(getFilters()).get(), equalTo(Long.valueOf(flows.size())));
    }

    private List<Filter> getFilters(Filter... filters) {
        final List<Filter> filterList = Lists.newArrayList(filters);
        filterList.add(new TimeRangeFilter(0, System.currentTimeMillis()));
        return filterList;
    }
}
