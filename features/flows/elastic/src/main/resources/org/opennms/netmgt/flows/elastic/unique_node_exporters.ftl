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
        }
      ]
    }
  },
  "aggs": {
    "criterias": {
      "terms": {
        "field": "node_exporter.node_criteria",
        "size": ${size?long?c}
      }
    }
  }
}