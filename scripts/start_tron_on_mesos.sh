#!/bin/bash

curl -v -X PUT -H "Content-type: application/json" -d "@start.json" "http://m1.dcos:8080/v2/groups"
