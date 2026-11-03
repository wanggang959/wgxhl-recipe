package com.wgxhl.recipe.commute;

import com.wgxhl.recipe.mail.MailDeliveryService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommuteNoticeTest {
    @Test void unverifiedEmailDoesNotBlockPrivateSiteAndApp() {
        JdbcTemplate db=mock(JdbcTemplate.class); MailDeliveryService mail=mock(MailDeliveryService.class);
        UserPushSubscriptionService push=mock(UserPushSubscriptionService.class);
        CommuteService service=spy(new CommuteService(db,mock(QWeatherClient.class),mail,push));
        Map<String,Object> user=new HashMap<>(); user.put("user_id","u"); user.put("email","test@example.test"); user.put("household_id","home");
        user.put("notice_time","00:00:00");user.put("arrival_time","23:59:59");user.put("work_days","1,2,3,4,5,6,7");
        user.put("site_enabled",1);user.put("email_enabled",1);user.put("push_enabled",1);user.put("email_verified",0);
        user.put("notice_day",1);user.put("work_days",String.valueOf(LocalDate.now(CommuteService.ZONE).plusDays(1).getDayOfWeek().getValue()));
        when(db.queryForList(anyString(),any(LocalTime.class),any(LocalTime.class),any(java.sql.Date.class),any(java.sql.Date.class),any(java.sql.Date.class),any(java.sql.Date.class),any(java.sql.Date.class))).thenReturn(Collections.singletonList(user));
        Map<String,Object> result=new HashMap<>();result.put("departure","2026-11-03T08:00");result.put("arrival","2026-11-03T09:00");result.put("minutes",45);result.put("bufferMinutes",15);result.put("weatherSummary","阴");result.put("sampleCount",2);
        result.put("dry",CommuteRecommendation.calculateForRain(LocalDate.now(),LocalTime.NOON,10,45,"DRY",Collections.emptyList())); result.put("rain",CommuteRecommendation.calculateForRain(LocalDate.now(),LocalTime.NOON,10,45,"RAIN",Collections.emptyList()));
        doReturn(result).when(service).recommend(anyString(),any(LocalDate.class));doNothing().when(service).snapshot(anyString(),anyMap());
        when(push.sendToUserIds(anyList(),anyString(),anyString(),anyString(),anyString())).thenReturn(1);
        service.notifyDaily();
        verify(service).recommend(eq("u"),eq(LocalDate.now(CommuteService.ZONE).plusDays(1)));
        verify(push).sendToUserIds(eq(Collections.singletonList("u")),anyString(),anyString(),eq("/#/commute"),anyString());
        verify(mail,never()).sendCommute(anyString(),anyMap(),anyString());
        verify(db).update(contains("insert into notification"),anyString(),eq("home"),eq("u"),anyString(),anyString());
        verify(db,times(2)).update(contains("update commute_notice_log"),eq("SUCCESS"),isNull(),anyString());
        verify(db).update(contains("update commute_notice_log"),eq("SKIP"),anyString(),anyString());
    }
    @Test void duplicateDailyClaimNeverSendsAnotherMail() {
        JdbcTemplate db=mock(JdbcTemplate.class); MailDeliveryService mail=mock(MailDeliveryService.class);
        CommuteService service=spy(new CommuteService(db,mock(QWeatherClient.class),mail));
        Map<String,Object> user=new HashMap<>(); user.put("user_id","u"); user.put("email","test@example.test");
        user.put("notice_time","00:00:00"); user.put("arrival_time","23:59:59"); user.put("work_days","1,2,3,4,5,6,7");
        user.put("email_enabled",1);user.put("email_verified",1);
        when(db.queryForList(anyString(),any(LocalTime.class),any(LocalTime.class),any(java.sql.Date.class),any(java.sql.Date.class),any(java.sql.Date.class),any(java.sql.Date.class),any(java.sql.Date.class))).thenReturn(Collections.singletonList(user));
        Map<String,Object> result=new HashMap<>(); result.put("date",LocalDate.now(CommuteService.ZONE).toString());
        result.put("departure","2026-11-03T08:00");result.put("arrival","2026-11-03T09:00");
        result.put("dry",CommuteRecommendation.calculateForRain(LocalDate.now(),LocalTime.NOON,10,45,"DRY",Collections.emptyList())); result.put("rain",CommuteRecommendation.calculateForRain(LocalDate.now(),LocalTime.NOON,10,45,"RAIN",Collections.emptyList()));
        doReturn(result).when(service).recommend(anyString(),any(LocalDate.class)); doNothing().when(service).snapshot(anyString(),anyMap());
        when(mail.isReady(MailDeliveryService.Kind.COMMUTE)).thenReturn(true);
        when(db.update(anyString(),any(),any(),any(),any())).thenThrow(new DuplicateKeyException("already claimed"));
        service.notifyDaily();
        verify(db).update(anyString(),any(),any(),any(),any());
        verify(mail,never()).sendCommute(anyString(),anyMap(),anyString());
    }
}
