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

import static io.searchbox.core.search.aggregation.AggregationField.BUCKETS;
import static io.searchbox.core.search.aggregation.AggregationField.DOC_COUNT;
import static io.searchbox.core.search.aggregation.AggregationField.KEY;
import static io.searchbox.core.search.aggregation.AggregationField.KEY_AS_STRING;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.core.search.aggregation.Aggregation;
import io.searchbox.core.search.aggregation.AggregationField;
import io.searchbox.core.search.aggregation.Bucket;

public class TimeSliceAggregation extends Aggregation {

    public static final String TYPE = "time_slice";

    private List<TimeSliceAggregation.DateHistogram> dateHistograms = new LinkedList<TimeSliceAggregation.DateHistogram>();

    public TimeSliceAggregation(String name, JsonObject TimeSliceAggregation) {
        super(name, TimeSliceAggregation);
        if (TimeSliceAggregation.has(String.valueOf(BUCKETS)) && TimeSliceAggregation.get(String.valueOf(BUCKETS)).isJsonArray()) {
            parseBuckets(TimeSliceAggregation.get(String.valueOf(BUCKETS)).getAsJsonArray());
        }
    }

    private void parseBuckets(JsonArray bucketsSource) {
        for (JsonElement bucket : bucketsSource) {
            Long time = bucket.getAsJsonObject().get(String.valueOf(KEY)).getAsLong();
            String timeAsString = bucket.getAsJsonObject().get(String.valueOf(KEY_AS_STRING)).getAsString();
            Long count = bucket.getAsJsonObject().get(String.valueOf(DOC_COUNT)).getAsLong();
            Double value = bucket.getAsJsonObject().get(String.valueOf(AggregationField.VALUE)).getAsDouble();

            dateHistograms.add(new TimeSliceAggregation.DateHistogram(bucket.getAsJsonObject(), time, timeAsString, value, count));
        }
    }

    /**
     * @return List of DateHistogram objects if found, or empty list otherwise
     */
    public List<TimeSliceAggregation.DateHistogram> getBuckets() {
        return dateHistograms;
    }

    public class DateHistogram extends Histogram {

        private String timeAsString;
        private Double value;

        DateHistogram(JsonObject bucket, Long time, String timeAsString, Double value, Long count) {
            super(bucket, time, count);
            this.timeAsString = timeAsString;
            this.value = value;
        }

        public Long getTime() {
            return getKey();
        }

        public String getTimeAsString() {
            return timeAsString;
        }

        public Double getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }

            TimeSliceAggregation.DateHistogram rhs = (TimeSliceAggregation.DateHistogram) obj;
            return super.equals(obj) && Objects.equals(timeAsString, rhs.timeAsString) && Objects.equals(value, rhs.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), timeAsString);
        }
    }

    public static class Histogram extends Bucket {

        private Long key;

        Histogram(JsonObject bucket, Long key, Long count) {
            super(bucket, count);
            this.key = key;
        }

        public Long getKey() {
            return key;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }

            Histogram rhs = (Histogram) obj;
            return super.equals(obj) && Objects.equals(key, rhs.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), key);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        TimeSliceAggregation rhs = (TimeSliceAggregation) obj;
        return super.equals(obj) && Objects.equals(dateHistograms, rhs.dateHistograms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dateHistograms);
    }
}
