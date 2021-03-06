/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.autoscaling.action;

import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlock;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.autoscaling.AutoscalingMetadata;
import org.elasticsearch.xpack.autoscaling.AutoscalingTestCase;
import org.elasticsearch.xpack.autoscaling.policy.AutoscalingPolicy;

import java.util.EnumSet;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

public class TransportGetAutoscalingPolicyActionTests extends AutoscalingTestCase {

    public void testReadBlock() {
        final TransportGetAutoscalingPolicyAction action = new TransportGetAutoscalingPolicyAction(
            mock(TransportService.class),
            mock(ClusterService.class),
            mock(ThreadPool.class),
            mock(ActionFilters.class),
            mock(IndexNameExpressionResolver.class)
        );
        final ClusterBlocks blocks = ClusterBlocks.builder()
            .addGlobalBlock(
                new ClusterBlock(
                    randomIntBetween(128, 256),
                    "metadata read block",
                    false,
                    false,
                    false,
                    RestStatus.SERVICE_UNAVAILABLE,
                    EnumSet.of(ClusterBlockLevel.METADATA_READ)
                )
            )
            .build();
        final ClusterState state = ClusterState.builder(new ClusterName(randomAlphaOfLength(8))).blocks(blocks).build();
        final ClusterBlockException e = action.checkBlock(new GetAutoscalingPolicyAction.Request(randomAlphaOfLength(8)), state);
        assertThat(e, not(nullValue()));
    }

    public void testNoReadBlock() {
        final TransportGetAutoscalingPolicyAction action = new TransportGetAutoscalingPolicyAction(
            mock(TransportService.class),
            mock(ClusterService.class),
            mock(ThreadPool.class),
            mock(ActionFilters.class),
            mock(IndexNameExpressionResolver.class)
        );
        final ClusterBlocks blocks = ClusterBlocks.builder().build();
        final ClusterState state = ClusterState.builder(new ClusterName(randomAlphaOfLength(8))).blocks(blocks).build();
        final ClusterBlockException e = action.checkBlock(new GetAutoscalingPolicyAction.Request(randomAlphaOfLength(8)), state);
        assertThat(e, nullValue());
    }

    public void testGetPolicy() {
        final ClusterState state;
        {
            final ClusterState.Builder builder = ClusterState.builder(new ClusterName(randomAlphaOfLength(8)));
            builder.metadata(
                Metadata.builder().putCustom(AutoscalingMetadata.NAME, randomAutoscalingMetadataOfPolicyCount(randomIntBetween(1, 8)))
            );
            state = builder.build();
        }
        final AutoscalingMetadata metadata = state.metadata().custom(AutoscalingMetadata.NAME);
        final String name = randomFrom(metadata.policies().keySet());
        final AutoscalingPolicy policy = TransportGetAutoscalingPolicyAction.getAutoscalingPolicy(state, name);

        assertThat(metadata.policies().get(name).policy(), equalTo(policy));
    }

    public void testGetNonExistentPolicy() {
        final ClusterState state;
        {
            final ClusterState.Builder builder = ClusterState.builder(new ClusterName(randomAlphaOfLength(8)));
            builder.metadata(
                Metadata.builder().putCustom(AutoscalingMetadata.NAME, randomAutoscalingMetadataOfPolicyCount(randomIntBetween(1, 8)))
            );
            state = builder.build();
        }
        final AutoscalingMetadata metadata = state.metadata().custom(AutoscalingMetadata.NAME);
        final String name = randomValueOtherThanMany(metadata.policies().keySet()::contains, () -> randomAlphaOfLength(8));
        final IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> TransportGetAutoscalingPolicyAction.getAutoscalingPolicy(state, name)
        );
        assertThat(e.getMessage(), containsString("autoscaling policy with name [" + name + "] does not exist"));
    }

}
