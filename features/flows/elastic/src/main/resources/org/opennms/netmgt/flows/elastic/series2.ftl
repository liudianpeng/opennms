{
  "size": ${size?long?c},
  "_source": ["netflow.last_switched", "netflow.first_switched", "netflow.application", "netflow.bytes", "netflow.initiator"],
  "query": {
    "bool": {
      "filter": [
        <#list filters as filter>${filter}<#sep>,</#list>
      ]
    }
  }
}