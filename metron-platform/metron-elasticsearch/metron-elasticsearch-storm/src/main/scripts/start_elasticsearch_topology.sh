#!/bin/bash
# 
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
METRON_VERSION=${project.version}
METRON_HOME=/usr/metron/$METRON_VERSION
TOPOLOGY_JAR=${project.artifactId}-$METRON_VERSION-uber.jar
STELLAR_JAR=stellar-common-$METRON_VERSION-uber.jar
INDEXING_CONTRIB=${INDEXING_CONTRIB:-$METRON_HOME/indexing_contrib}
if [ -d "$INDEXING_CONTRIB" ]; then
  export EXTRA_JARS=$(ls -m $INDEXING_CONTRIB/*.jar | tr -d ' ' | tr -d '\n' | sed 's/\/\//\//g')
  storm jar $METRON_HOME/lib/$TOPOLOGY_JAR org.apache.storm.flux.Flux --remote $METRON_HOME/flux/indexing/random_access/remote.yaml --filter $METRON_HOME/config/elasticsearch.properties --jars "$METRON_HOME/lib/$STELLAR_JAR,$EXTRA_JARS"
else
  storm jar $METRON_HOME/lib/$TOPOLOGY_JAR org.apache.storm.flux.Flux --remote $METRON_HOME/flux/indexing/random_access/remote.yaml --filter $METRON_HOME/config/elasticsearch.properties --jars "$METRON_HOME/lib/$STELLAR_JAR"
fi
