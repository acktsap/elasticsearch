/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ccr;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.project.ProjectResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.injection.guice.Inject;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.protocol.xpack.XPackUsageRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.action.XPackUsageFeatureAction;
import org.elasticsearch.xpack.core.action.XPackUsageFeatureResponse;
import org.elasticsearch.xpack.core.action.XPackUsageFeatureTransportAction;
import org.elasticsearch.xpack.core.ccr.AutoFollowMetadata;
import org.elasticsearch.xpack.core.ccr.CcrConstants;

import java.time.Instant;

public class CCRUsageTransportAction extends XPackUsageFeatureTransportAction {

    private final Settings settings;
    private final XPackLicenseState licenseState;
    private final ProjectResolver projectResolver;

    @Inject
    public CCRUsageTransportAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        Settings settings,
        XPackLicenseState licenseState,
        ProjectResolver projectResolver
    ) {
        super(XPackUsageFeatureAction.CCR.name(), transportService, clusterService, threadPool, actionFilters);
        this.settings = settings;
        this.licenseState = licenseState;
        this.projectResolver = projectResolver;
    }

    @Override
    protected void localClusterStateOperation(
        Task task,
        XPackUsageRequest request,
        ClusterState state,
        ActionListener<XPackUsageFeatureResponse> listener
    ) {
        final var project = projectResolver.getProjectMetadata(state);

        int numberOfFollowerIndices = 0;
        long lastFollowerIndexCreationDate = 0L;
        for (IndexMetadata imd : project) {
            if (imd.getCustomData("ccr") != null) {
                numberOfFollowerIndices++;
                if (lastFollowerIndexCreationDate < imd.getCreationDate()) {
                    lastFollowerIndexCreationDate = imd.getCreationDate();
                }
            }
        }
        AutoFollowMetadata autoFollowMetadata = project.custom(AutoFollowMetadata.TYPE);
        int numberOfAutoFollowPatterns = autoFollowMetadata != null ? autoFollowMetadata.getPatterns().size() : 0;

        Long lastFollowTimeInMillis;
        if (numberOfFollowerIndices == 0) {
            // Otherwise we would return a value that makes no sense.
            lastFollowTimeInMillis = null;
        } else {
            lastFollowTimeInMillis = Math.max(0, Instant.now().toEpochMilli() - lastFollowerIndexCreationDate);
        }

        CCRInfoTransportAction.Usage usage = new CCRInfoTransportAction.Usage(
            CcrConstants.CCR_FEATURE.checkWithoutTracking(licenseState),
            XPackSettings.CCR_ENABLED_SETTING.get(settings),
            numberOfFollowerIndices,
            numberOfAutoFollowPatterns,
            lastFollowTimeInMillis
        );
        listener.onResponse(new XPackUsageFeatureResponse(usage));
    }
}
