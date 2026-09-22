package com.flashsale.activity;
import org.junit.jupiter.api.Test;import java.time.OffsetDateTime;import static org.junit.jupiter.api.Assertions.*;
class ActivityQueryServiceTest{@Test void exposesMetricsWithoutInternalRecoveryDetails(){var n=OffsetDateTime.now();var a=new Activity(1,"sale",2,10,3,2,1,n,n.plusHours(1),ActivityStatus.PAUSED,false,1);var m=new ActivityQueryService();var view=m.publicView(a,new ActivityMetrics(ActivityStatus.PAUSED,2,0,0,0,false));assertEquals(ActivityStatus.PAUSED,view.status());assertEquals(2,view.availableStock());}}
