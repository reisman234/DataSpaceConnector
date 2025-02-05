/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.connector.store.azure.cosmos.policydefinition;

import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.models.SqlQuerySpec;
import dev.failsafe.RetryPolicy;
import org.eclipse.edc.azure.cosmos.CosmosDbApi;
import org.eclipse.edc.azure.cosmos.CosmosDocument;
import org.eclipse.edc.connector.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.store.azure.cosmos.policydefinition.model.PolicyDocument;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.store.azure.cosmos.policydefinition.TestFunctions.generateDocument;
import static org.eclipse.edc.connector.store.azure.cosmos.policydefinition.TestFunctions.generatePolicy;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CosmosPolicyDefinitionStoreTest {
    private static final String TEST_PART_KEY = "test_part_key";
    private CosmosPolicyDefinitionStore store;
    private CosmosDbApi cosmosDbApiMock;

    @BeforeEach
    void setup() {
        cosmosDbApiMock = mock(CosmosDbApi.class);
        var typeManager = new TypeManager();
        var retryPolicy = RetryPolicy.ofDefaults();
        store = new CosmosPolicyDefinitionStore(cosmosDbApiMock, typeManager, retryPolicy, TEST_PART_KEY, mock(Monitor.class));
    }

    @Test
    void findAll() {
        var doc1 = generateDocument(TEST_PART_KEY);
        var doc2 = generateDocument(TEST_PART_KEY);
        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(Stream.of(doc1, doc2));

        var all = store.findAll(QuerySpec.none());

        assertThat(all).hasSize(2).containsExactlyInAnyOrder(doc1.getWrappedInstance(), doc2.getWrappedInstance());
        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
    }

    @Test
    void findAll_noReload() {
        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(Stream.empty());

        var all = store.findAll(QuerySpec.none());
        assertThat(all).isEmpty();
        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
    }

    @Test
    void save() {
        var captor = ArgumentCaptor.forClass(CosmosDocument.class);
        doNothing().when(cosmosDbApiMock).createItem(captor.capture());
        var definition = generatePolicy();

        store.create(definition);

        assertThat(captor.getValue().getWrappedInstance()).isEqualTo(definition);
        verify(cosmosDbApiMock).createItem(captor.capture());
    }

    @Test
    void save_verifyWriteThrough() {
        var captor = ArgumentCaptor.forClass(PolicyDocument.class);
        doNothing().when(cosmosDbApiMock).createItem(captor.capture());
        var definition = generatePolicy();

        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(IntStream.range(0, 1).mapToObj((i) -> captor.getValue()));

        store.create(definition); //should write through the cache

        var all = store.findAll(QuerySpec.none());

        assertThat(all).isNotEmpty().containsExactlyInAnyOrder(captor.getValue().getWrappedInstance());
        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
        verify(cosmosDbApiMock).createItem(captor.capture());
    }

    @Test
    void update() {
        var captor = ArgumentCaptor.forClass(CosmosDocument.class);
        doNothing().when(cosmosDbApiMock).createItem(captor.capture());
        var definition = generatePolicy();

        store.create(definition);

        assertThat(captor.getValue().getWrappedInstance()).isEqualTo(definition);
        verify(cosmosDbApiMock).createItem(captor.capture());
    }

    @Test
    void deleteById_whenMissing_returnsNull() {
        when(cosmosDbApiMock.deleteItem(any())).thenThrow(new NotFoundException());
        var contractDefinition = store.delete("some-id");
        assertThat(contractDefinition).isNotNull().extracting(StoreResult::reason).isEqualTo(NOT_FOUND);
        verify(cosmosDbApiMock).deleteItem(notNull());
    }

    @Test
    void delete_whenContractDefinitionPresent_deletes() {
        var contractDefinition = generatePolicy();
        var document = new PolicyDocument(contractDefinition, TEST_PART_KEY);
        when(cosmosDbApiMock.deleteItem(document.getId())).thenReturn(document);

        var deletedDefinition = store.delete(document.getId());
        assertThat(deletedDefinition.succeeded()).isTrue();
        assertThat(deletedDefinition.getContent()).isEqualTo(contractDefinition);
    }

    @Test
    void delete_whenCosmoDbApiThrows_throws() {
        var id = "some-id";
        when(cosmosDbApiMock.deleteItem(id)).thenThrow(new EdcPersistenceException("Something went wrong"));
        assertThatThrownBy(() -> store.delete(id)).isInstanceOf(EdcPersistenceException.class);
    }

    @Test
    void findAll_noQuerySpec() {

        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(IntStream.range(0, 10).mapToObj(i -> generateDocument(TEST_PART_KEY)));

        var all = store.findAll(QuerySpec.Builder.newInstance().build());
        assertThat(all).hasSize(10);
        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
        verifyNoMoreInteractions(cosmosDbApiMock);
    }

    @Test
    void findAll_verifyFiltering() {
        var doc = generateDocument(TEST_PART_KEY);
        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(Stream.of(doc));
        store.reload();

        var all = store.findAll(QuerySpec.Builder.newInstance().filter("id=" + doc.getId()).build());
        assertThat(all).hasSize(1).extracting(PolicyDefinition::getUid).containsOnly(doc.getId());
        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
        verifyNoMoreInteractions(cosmosDbApiMock);
    }

    @Test
    void findAll_verifyFiltering_invalidFilterExpression() {
        assertThatThrownBy(() -> store.findAll(QuerySpec.Builder.newInstance().filter("something foobar other").build())).isInstanceOfAny(IllegalArgumentException.class);
    }

    @Test
    void findAll_verifySorting_asc() {
        var stream = IntStream.range(0, 10).mapToObj(i -> generateDocument(TEST_PART_KEY)).sorted(Comparator.comparing(PolicyDocument::getId).reversed()).map(Object.class::cast);
        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(stream);
        store.reload();

        var all = store.findAll(QuerySpec.Builder.newInstance().sortField("id").sortOrder(SortOrder.DESC).build()).collect(Collectors.toList());
        assertThat(all).hasSize(10).isSortedAccordingTo((c1, c2) -> c2.getUid().compareTo(c1.getUid()));

        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
        verifyNoMoreInteractions(cosmosDbApiMock);
    }

    @Test
    void findAll_verifySorting_desc() {
        var stream = IntStream.range(0, 10).mapToObj(i -> generateDocument(TEST_PART_KEY)).sorted(Comparator.comparing(PolicyDocument::getId)).map(Object.class::cast);
        when(cosmosDbApiMock.queryItems(any(SqlQuerySpec.class))).thenReturn(stream);
        store.reload();

        var all = store.findAll(QuerySpec.Builder.newInstance().sortField("id").sortOrder(SortOrder.ASC).build()).collect(Collectors.toList());
        assertThat(all).hasSize(10).isSortedAccordingTo(Comparator.comparing(PolicyDefinition::getUid));

        verify(cosmosDbApiMock).queryItems(any(SqlQuerySpec.class));
        verifyNoMoreInteractions(cosmosDbApiMock);
    }

    @Test
    void findAll_verifySorting_invalidField() {
        when(cosmosDbApiMock.queryItems(isA(SqlQuerySpec.class))).thenReturn(Stream.empty());

        assertThat(store.findAll(QuerySpec.Builder.newInstance().sortField("nonexist").sortOrder(SortOrder.DESC).build())).isEmpty();
    }
}
