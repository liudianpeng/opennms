{
  "range": {
    "@timestamp": {
      "gte": ${start?long?c},
      "lte": ${end?long?c},
      "format": "epoch_millis"
    }
  }
}