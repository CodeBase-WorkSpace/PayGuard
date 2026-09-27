package dev.amg.payguard.collateral.infrastructure;

import config.kafka.config.PayGuardKafkaConsumerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(PayGuardKafkaConsumerConfiguration.class)
public class CollateralKafkaConfiguration {}
