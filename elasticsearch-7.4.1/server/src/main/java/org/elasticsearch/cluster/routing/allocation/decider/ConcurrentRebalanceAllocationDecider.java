/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.routing.allocation.decider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;

/**
 * Similar to the {@link ClusterRebalanceAllocationDecider} this
 * {@link AllocationDecider} controls the number of currently in-progress
 * re-balance (relocation) operations and restricts node allocations if the
 * configured threshold is reached. The default number of concurrent rebalance
 * operations is set to {@code 2}
 * <p>
 * Re-balance operations can be controlled in real-time via the cluster update API using
 * {@code cluster.routing.allocation.cluster_concurrent_rebalance}. Iff this
 * setting is set to {@code -1} the number of concurrent re-balance operations
 * are unlimited.
 * 类似于{@link ClusterRebalanceAllocationDecider} {@link AllocationDecider}控制当前进行中的数量重新平衡（重定位）操作并限制节点分配（如果
 * 达到配置的阈值。 并发重新平衡操作的默认数量设置为{@code 2}
 * <p>
 * 重新平衡操作可以使用{@code cluster.routing.allocation.cluster_concurrent_rebalance}通过集群更新API进行实时控制。
 * 如果将此设置设置为{@code -1}，则并发重新平衡操作的数量是无限的。
 */
public class ConcurrentRebalanceAllocationDecider extends AllocationDecider {

    private static final Logger logger = LogManager.getLogger(ConcurrentRebalanceAllocationDecider.class);

    public static final String NAME = "concurrent_rebalance";

    public static final Setting<Integer> CLUSTER_ROUTING_ALLOCATION_CLUSTER_CONCURRENT_REBALANCE_SETTING =
        Setting.intSetting("cluster.routing.allocation.cluster_concurrent_rebalance", 2, -1,
            Property.Dynamic, Property.NodeScope);
    private volatile int clusterConcurrentRebalance;

    public ConcurrentRebalanceAllocationDecider(Settings settings, ClusterSettings clusterSettings) {
        this.clusterConcurrentRebalance = CLUSTER_ROUTING_ALLOCATION_CLUSTER_CONCURRENT_REBALANCE_SETTING.get(settings);
        logger.debug("using [cluster_concurrent_rebalance] with [{}]", clusterConcurrentRebalance);
        clusterSettings.addSettingsUpdateConsumer(CLUSTER_ROUTING_ALLOCATION_CLUSTER_CONCURRENT_REBALANCE_SETTING,
                this::setClusterConcurrentRebalance);
    }

    private void setClusterConcurrentRebalance(int concurrentRebalance) {
        clusterConcurrentRebalance = concurrentRebalance;
    }

    @Override
    public Decision canRebalance(ShardRouting shardRouting, RoutingAllocation allocation) {
        return canRebalance(allocation);
    }

    @Override
    public Decision canRebalance(RoutingAllocation allocation) {
        if (clusterConcurrentRebalance == -1) {
            return allocation.decision(Decision.YES, NAME, "unlimited concurrent rebalances are allowed");
        }
        int relocatingShards = allocation.routingNodes().getRelocatingShardCount();
        if (relocatingShards >= clusterConcurrentRebalance) {
            return allocation.decision(Decision.THROTTLE, NAME,
                    "reached the limit of concurrently rebalancing shards [%d], cluster setting [%s=%d]",
                    relocatingShards,
                    CLUSTER_ROUTING_ALLOCATION_CLUSTER_CONCURRENT_REBALANCE_SETTING.getKey(),
                    clusterConcurrentRebalance);
        }
        return allocation.decision(Decision.YES, NAME,
                "below threshold [%d] for concurrent rebalances, current rebalance shard count [%d]",
                clusterConcurrentRebalance, relocatingShards);
    }
}
