{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        <#list filters as filter>${filter}<#sep>,</#list>
      ]
    }
  },
  "aggs": {
    "grouped_by": {
      "terms": {
        "field": "${groupByTerm?json_string}"
      },
      "aggs": {
        "bytes_over_time": {
          "date_histogram": {
            "field": "@timestamp",
            "interval": "${step?long?c}ms"
          },
          "aggs": {
            "direction": {
              "terms": {
                "field": "netflow.initiator",
                "size": 2
              },
              "aggs": {
                "total_bytes": {
                  "sum": {
                    "field": "netflow.bytes"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}