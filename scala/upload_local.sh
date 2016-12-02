#!/bin/bash

echo "Upload fab sample to local tron"

wget -O - --post-file=target/scala-2.11/fabsample-assembly-1.0.jar \
     http://localhost:3000/api/v1/add_func
