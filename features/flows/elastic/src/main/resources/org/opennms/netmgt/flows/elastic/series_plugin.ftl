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
    "direction": {
      "terms": {
        "field": "netflow.initiator",
        "size": 2
      },
      "aggs": {
        "grouped_by": {
          "terms": {
            "field": "${groupByTerm?json_string}"
          },
          "aggs": {
            "bytes_over_time": {
              "time_slice": {
                "fields": [
                  "netflow.first_switched",
                  "netflow.last_switched",
                  "netflow.bytes"
                ],
                "interval": "${step?long?c}ms",
                "start": ${start?long?c},
                "end": ${end?long?c}
              }
            }
          }
        }
      }
    }
  }
}