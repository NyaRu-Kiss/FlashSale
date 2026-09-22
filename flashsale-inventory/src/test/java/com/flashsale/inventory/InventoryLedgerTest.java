package com.flashsale.inventory;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class InventoryLedgerTest { @Test void reserveReleaseIsIdempotent(){InventoryLedger l=new InventoryLedger();l.initialize(1,2);var r=l.reserve("a",1,1);assertEquals(1,l.available(1));assertSame(r,l.reserve("a",1,1));l.release("a");l.release("a");assertEquals(2,l.available(1));} @Test void cannotOverSell(){InventoryLedger l=new InventoryLedger();l.initialize(1,1);l.reserve("a",1,1);assertThrows(IllegalArgumentException.class,()->l.reserve("b",1,1));} }
