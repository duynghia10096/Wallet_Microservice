package com.wallet.transaction.infrastructure.config;

import com.wallet.common.event.TransactionCompletedEvent;
import com.wallet.common.event.TransactionFailedEvent;
import com.wallet.transaction.saga.orchestration.TransferSagaTopics;
import com.wallet.transaction.saga.orchestration.command.SagaCommands;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionCompletedTopic() {
        return TopicBuilder.name(TransactionCompletedEvent.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionFailedTopic() {
        return TopicBuilder.name(TransactionFailedEvent.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaTransferInitiatedTopic() {
        return TopicBuilder.name(TransferSagaTopics.TRANSFER_INITIATED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaTransferCompletedTopic() {
        return TopicBuilder.name(TransferSagaTopics.TRANSFER_COMPLETED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaTransferFailedTopic() {
        return TopicBuilder.name(TransferSagaTopics.TRANSFER_FAILED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaSourceDebitedTopic() {
        return TopicBuilder.name(TransferSagaTopics.SOURCE_WALLET_DEBITED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaDestCreditedTopic() {
        return TopicBuilder.name(TransferSagaTopics.DEST_WALLET_CREDITED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaSourceDebitFailedTopic() {
        return TopicBuilder.name(TransferSagaTopics.SOURCE_DEBIT_FAILED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaDestCreditFailedTopic() {
        return TopicBuilder.name(TransferSagaTopics.DEST_CREDIT_FAILED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaSourceDebitReversedTopic() {
        return TopicBuilder.name(TransferSagaTopics.SOURCE_DEBIT_REVERSED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaCmdDebitTopic() {
        return TopicBuilder.name(SagaCommands.DEBIT_SOURCE_CMD)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaCmdCreditTopic() {
        return TopicBuilder.name(SagaCommands.CREDIT_DEST_CMD)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaCmdReverseTopic() {
        return TopicBuilder.name(SagaCommands.REVERSE_DEBIT_CMD)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaReplyDebitTopic() {
        return TopicBuilder.name(SagaCommands.DEBIT_REPLY)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaReplyCreditTopic() {
        return TopicBuilder.name(SagaCommands.CREDIT_REPLY)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sagaReplyReverseTopic() {
        return TopicBuilder.name(SagaCommands.REVERSE_REPLY)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
