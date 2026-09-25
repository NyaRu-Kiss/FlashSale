package com.flashsale.inventory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
public class InventoryConfiguration { @Bean InventoryLedger inventoryLedger() { return new InventoryLedger(); } }
