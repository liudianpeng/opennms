{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        {
          "range": {
            "@timestamp": {
              "gte": ${start?long?c},
              "lte": ${end?long?c},
              "format": "epoch_millis"
            }
          }
        },
        {
          "terms": {
            "node_exporter.node_criteria": ["${nodeCriteria?json_string}"]
          }
        }
      ]
    }
  },
  "aggs": {
    "input_snmp": {
      "terms": {
        "field": "netflow.input_snmp",
        "size": ${size?long?c}
      }
    },
    "output_snmp": {
      "terms": {
        "field": "netflow.input_snmp",
        "size": ${size?long?c}
      }
    }
  }
}