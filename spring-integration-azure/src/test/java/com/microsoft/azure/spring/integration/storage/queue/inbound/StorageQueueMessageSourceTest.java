/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.integration.storage.queue.inbound;

import com.microsoft.azure.spring.integration.core.Checkpointer;
import com.microsoft.azure.spring.integration.eventhub.inbound.CheckpointMode;
import com.microsoft.azure.spring.integration.storage.queue.StorageQueueOperation;
import com.microsoft.azure.spring.integration.storage.queue.StorageQueueRuntimeException;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StorageQueueMessageSourceTest {

    @Mock
    private StorageQueueOperation mockOperation;

    @Mock
    private Checkpointer<CloudQueueMessage> mockCheckpointer;

    @Mock
    private CloudQueueMessage cloudQueueMessage;

    private String destination = "test-destination";
    private StorageQueueMessageSource messageSource;
    private CompletableFuture<CloudQueueMessage> future = new CompletableFuture<>();

    @Before
    public void setup() throws StorageException {
        when(this.mockOperation.getCheckpointer(eq(destination))).thenReturn(this.mockCheckpointer);
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        messageSource = new StorageQueueMessageSource(destination, mockOperation);
    }

    @Test
    public void testDoReceiveWhenHaveNoMessage() {
        future.complete(null);
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        assertNull(messageSource.doReceive());
    }

    @Test(expected = StorageQueueRuntimeException.class)
    public void testReceiveFailure() {
        future.completeExceptionally(new StorageQueueRuntimeException("Failed to receive message."));
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        messageSource.doReceive();
    }

    @Test(expected = StorageQueueRuntimeException.class)
    public void testGetMessageFailure() throws StorageException {
        haveMessage();
        when(cloudQueueMessage.getMessageContentAsByte()).thenThrow(StorageException.class);
        messageSource.doReceive();
    }

    @Test
    public void testDoReceiveWithRecordCheckpointerMode() throws StorageException {
        haveMessage();
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        Message<byte[]> message = (Message<byte[]>) messageSource.doReceive();
        verify(mockCheckpointer, times(1)).checkpoint(this.cloudQueueMessage);
        assertEquals(message.getPayload(), this.cloudQueueMessage.getMessageContentAsByte());
    }

    @Test
    public void testDoReceiveWithManualCheckpointerMode() throws StorageException {
        haveMessage();
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        this.messageSource.setCheckpointMode(CheckpointMode.MANUAL);
        Message<byte[]> message = (Message<byte[]>) messageSource.doReceive();
        verify(mockCheckpointer, times(0)).checkpoint(this.cloudQueueMessage);
        assertEquals(message.getPayload(), this.cloudQueueMessage.getMessageContentAsByte());
    }

    public void haveMessage() throws StorageException {
        when(cloudQueueMessage.getMessageContentAsByte()).thenReturn(new byte[]{1, 2, 3});
        future.complete(cloudQueueMessage);
    }
}
