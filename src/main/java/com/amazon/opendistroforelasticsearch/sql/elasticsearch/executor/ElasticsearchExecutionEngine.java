/*
 *    Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License").
 *    You may not use this file except in compliance with the License.
 *    A copy of the License is located at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    or in the "license" file accompanying this file. This file is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language governing
 *    permissions and limitations under the License.
 *
 */

package com.amazon.opendistroforelasticsearch.sql.elasticsearch.executor;

import com.amazon.opendistroforelasticsearch.sql.common.response.ResponseListener;
import com.amazon.opendistroforelasticsearch.sql.data.model.ExprValue;
import com.amazon.opendistroforelasticsearch.sql.elasticsearch.client.ElasticsearchClient;
import com.amazon.opendistroforelasticsearch.sql.elasticsearch.executor.protector.ExecutionProtector;
import com.amazon.opendistroforelasticsearch.sql.elasticsearch.storage.ElasticsearchIndexScan;
import com.amazon.opendistroforelasticsearch.sql.executor.ExecutionEngine;
import com.amazon.opendistroforelasticsearch.sql.executor.Explain;
import com.amazon.opendistroforelasticsearch.sql.planner.physical.PhysicalPlan;
import com.amazon.opendistroforelasticsearch.sql.storage.TableScanOperator;
import com.google.common.collect.ImmutableMap;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch execution engine implementation.
 */
public class ElasticsearchExecutionEngine implements ExecutionEngine {

    private final ElasticsearchClient client;

    private final ExecutionProtector executionProtector;

    @Inject
    public ElasticsearchExecutionEngine(ElasticsearchClient client, ExecutionProtector executionProtector) {
        this.client = client;
        this.executionProtector = executionProtector;
    }

    @Override
    public QueryResponse execute(PhysicalPlan physicalPlan) {
        PhysicalPlan plan = executionProtector.protect(physicalPlan);
        try {
            return run(physicalPlan, plan);
        } finally {
            plan.close();
        }

    }

    @Override
    public void execute(PhysicalPlan physicalPlan, ResponseListener<QueryResponse> listener) {
        PhysicalPlan plan = executionProtector.protect(physicalPlan);
        client.schedule(() -> {
            try {
                QueryResponse response = run(physicalPlan, plan);
                listener.onResponse(response);
            } catch (Exception e) {
                listener.onFailure(e);
            } finally {
                plan.close();
            }
        });
    }

    private QueryResponse run(PhysicalPlan physicalPlan, PhysicalPlan plan) {
        List<ExprValue> result = new ArrayList<>();
        plan.open();

        while (plan.hasNext()) {
            result.add(plan.next());
        }

        return new QueryResponse(physicalPlan.schema(), result);
    }

    @Override
    public void explain(PhysicalPlan plan, ResponseListener<ExplainResponse> listener) {
        client.schedule(() -> {
            try {
                listener.onResponse(explain(plan));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        });
    }

    @Override
    public ExplainResponse explain(PhysicalPlan plan) {
        return new Explain() {
            @Override
            public ExplainResponseNode visitTableScan(TableScanOperator node, Object context) {
                return explain(node, context, explainNode -> {
                    ElasticsearchIndexScan indexScan = (ElasticsearchIndexScan) node;
                    explainNode.setDescription(ImmutableMap.of(
                            "request", indexScan.getRequest().toString()));
                });
            }
        }.apply(plan);
    }

}
