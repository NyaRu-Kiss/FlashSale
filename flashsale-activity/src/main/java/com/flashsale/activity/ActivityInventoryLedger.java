package com.flashsale.activity;
import java.util.*;
/** In-memory domain model for the transactional sequence/checkpoint rules. */
public final class ActivityInventoryLedger {
 public enum Kind { RESERVE, RELEASE }
 public record Event(long sequence,Kind kind,int quantity) {}
 private final List<Event> events=new ArrayList<>(); private long next=1; private long checkpoint; private Long pauseBarrier;
 public synchronized Event append(Kind kind,int quantity){if(quantity<=0)throw new IllegalArgumentException("VALIDATION_ERROR");Event e=new Event(next++,kind,quantity);events.add(e);return e;}
 public synchronized long checkpoint(){return checkpoint;}
 public synchronized void consume(long sequence){if(sequence!=checkpoint+1)throw new IllegalArgumentException("CHECKPOINT_GAP");if(events.stream().noneMatch(e->e.sequence()==sequence))throw new IllegalArgumentException("EVENT_NOT_FOUND");checkpoint=sequence;}
 public synchronized long pauseBarrier(){pauseBarrier=next-1;return pauseBarrier;}
 public synchronized boolean recoverable(){return pauseBarrier!=null&&checkpoint>=pauseBarrier;}
 public synchronized List<Event> events(){return List.copyOf(events);}
}
