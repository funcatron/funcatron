#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd "$DIR"

docker build -t funcatron/frontend:latest .

docker build -t funcatron/frontend:working .
