/*
 *  Copyright (c) 2021 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.extensions.api;

import org.eclipse.dataspaceconnector.aws.dataplane.s3.S3DataSourceFactory;
import org.eclipse.dataspaceconnector.aws.s3.core.S3BucketSchema;
import org.eclipse.dataspaceconnector.dataloading.AssetLoader;
import org.eclipse.dataspaceconnector.dataplane.spi.pipeline.DataTransferExecutorServiceContainer;
import org.eclipse.dataspaceconnector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.dataspaceconnector.policy.model.Action;
import org.eclipse.dataspaceconnector.policy.model.Permission;
import org.eclipse.dataspaceconnector.policy.model.Policy;
import org.eclipse.dataspaceconnector.spi.asset.AssetSelectorExpression;
import org.eclipse.dataspaceconnector.spi.contract.offer.store.ContractDefinitionStore;
import org.eclipse.dataspaceconnector.spi.system.Inject;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.types.domain.DataAddress;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractDefinition;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import java.nio.file.Path;
import java.util.Objects;

import static org.eclipse.dataspaceconnector.common.configuration.ConfigurationFunctions.propOrEnv;

public class FileTransferExtension implements ServiceExtension {

    public static final String USE_POLICY = "use-eu";
    private static final String EDC_ASSET_PATH = "edc.samples.04.asset.path";
    @Inject
    private ContractDefinitionStore contractStore;
    @Inject
    private AssetLoader loader;
    @Inject
    private PipelineService pipelineService;
    @Inject
    private DataTransferExecutorServiceContainer executorContainer;

//    @Inject
//    private S3ClientProvider s3ClientProvider;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        var s3Endpoint = context.getSetting("s3.endpoint", "http://localhost:9000");
        var s3ClientProvider = new MyS3ClientProvider(getCredentials(context),s3Endpoint);

        var credentialsProvider = DefaultCredentialsProvider.create();
        //var sourceFactory = new FileTransferDataSourceFactory();
        var sourceFactory = new S3DataSourceFactory(s3ClientProvider, credentialsProvider);
        pipelineService.registerFactory(sourceFactory);

        var sinkFactory = new FileTransferDataSinkFactory(monitor, executorContainer.getExecutorService(), 5);
        pipelineService.registerFactory(sinkFactory);

        var policy = createPolicy();

        registerDataEntries(context);
        registerContractDefinition(policy);

        context.getMonitor().info("File Transfer Extension initialized!");
    }

    private Policy createPolicy() {

        var usePermission = Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type("USE").build())
                .build();

        return Policy.Builder.newInstance()
                .id(USE_POLICY)
                .permission(usePermission)
                .build();
    }

    private void registerDataEntries(ServiceExtensionContext context) {
        var assetPathSetting = context.getSetting(EDC_ASSET_PATH, "/tmp/provider/test-document.txt");
        var assetPath = Path.of(assetPathSetting);

        var dataAddress = DataAddress.Builder.newInstance()
                .property("type", S3BucketSchema.TYPE)
                .property(S3BucketSchema.BUCKET_NAME, "test")
                .property(S3BucketSchema.REGION, USE_POLICY)
                .property(S3BucketSchema.ACCESS_KEY_ID, "root")
                .property(S3BucketSchema.SECRET_ACCESS_KEY, "password")
                .property("path", assetPath.getParent().toString())
                .property("filename", assetPath.getFileName().toString())
                .keyName(assetPath.getFileName().toString())
                .build();

        var assetId = "test-document";
        var asset = Asset.Builder.newInstance().id(assetId).build();

        loader.accept(asset, dataAddress);
    }

    private void registerContractDefinition(Policy policy) {

        var contractDefinition = ContractDefinition.Builder.newInstance()
                .id("1")
                .accessPolicy(policy)
                .contractPolicy(policy)
                .selectorExpression(AssetSelectorExpression.Builder.newInstance().whenEquals(Asset.PROPERTY_ID, "test-document").build())
                .build();

        contractStore.save(contractDefinition);
    }

    protected @NotNull AwsCredentials getCredentials(ServiceExtensionContext context) {
        String profile = propOrEnv("AWS_PROFILE", null);
        if (profile != null) {
            return ProfileCredentialsProvider.create(profile).resolveCredentials();
        }

        var accessKeyId = context.getSetting("s3.access.key.id", null);
        var secretKey = context.getSetting("s3.secret.access.key", null);
//        var accessKeyId = propOrEnv("S3_ACCESS_KEY_ID", null);
        Objects.requireNonNull(accessKeyId, "S3_ACCESS_KEY_ID cannot be null!");
//        var secretKey = propOrEnv("S3_SECRET_ACCESS_KEY", null);
        Objects.requireNonNull(secretKey, "S3_SECRET_ACCESS_KEY cannot be null");

        return AwsBasicCredentials.create(accessKeyId, secretKey);
    }
}
