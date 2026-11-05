package com.wgxhl.recipe.commute;

import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named="test.integration.enabled",matches="true")
@Transactional
class CommuteIntegrationTest {
    @Autowired CommuteController controller;
    @Autowired JdbcTemplate db;
    @AfterEach void cleanup(){ UserContext.clear(); }
    private void login(String id){ AppUser u=new AppUser(); u.setId(id); UserContext.set(u); }
    @Test void statisticsCountOnlyRecentIncludedOutboundRecordsAndKeepUsersPrivate() {
        String owner=UUID.randomUUID().toString(); login(owner); LocalDate today=LocalDate.now(CommuteService.ZONE);
        Object[][] values={{today,30,"DRY",1,"OUTBOUND"},{today.minusDays(1),50,"RAIN",1,"OUTBOUND"},{today.minusDays(2),40,null,1,"OUTBOUND"},{today.minusDays(3),90,"RAIN",0,"OUTBOUND"},{today.minusDays(4),100,"DRY",1,"RETURN"},{today.minusDays(30),100,"DRY",1,"OUTBOUND"},{today.plusDays(1),100,"RAIN",1,"OUTBOUND"}};
        for(Object[] v:values) db.update("insert into commute_record(id,user_id,commute_date,direction,duration_minutes,actual_rain,include_stats,create_time,update_time) values(?,?,?,?,?,?,?,now(),now())",UUID.randomUUID().toString(),owner,v[0],v[4],v[1],v[2],v[3]);
        Map<String,Object> stats=controller.statistics().getData();
        assertEquals(3,((Number)stats.get("allCount")).intValue()); assertEquals(40,((Number)stats.get("allAverage")).intValue());
        assertEquals(1,((Number)stats.get("dryCount")).intValue()); assertEquals(30,((Number)stats.get("dryAverage")).intValue());
        assertEquals(1,((Number)stats.get("rainCount")).intValue()); assertEquals(50,((Number)stats.get("rainAverage")).intValue());
        assertEquals(6,controller.records(null).getData().size());
        login(UUID.randomUUID().toString()); assertEquals(0,((Number)controller.statistics().getData().get("allCount")).intValue());
    }
    @Test void futureSnapshotIsNeverCreated() {
        String owner=UUID.randomUUID().toString();
        Map<String,Object> r=new HashMap<>(); r.put("date",LocalDate.now(CommuteService.ZONE).plusDays(1).toString());
        new CommuteService(db,null,null).snapshot(owner,r);
        assertEquals(0,db.queryForObject("select count(*) from commute_record where user_id=?",Integer.class,owner));
    }
    @Test void scheduledRecordCreationUsesTodayAndDoesNotDuplicate() {
        LocalDateTime now=LocalDateTime.now(CommuteService.ZONE);
        Assumptions.assumeTrue(now.toLocalTime().isBefore(LocalTime.of(23,20)));
        String user="admin-wangshifu"; login(user);
        db.update("delete from commute_record where user_id=? and commute_date=? and direction='OUTBOUND'",user,now.toLocalDate());
        CommuteSettings settings=new CommuteSettings(); settings.setArrivalTime(now.plusMinutes(30).toLocalTime().withSecond(0).withNano(0));
        settings.setNoticeTime(now.minusHours(2).toLocalTime().withSecond(0).withNano(0)); settings.setNoticeDay(1);
        settings.setBufferMinutes(10); settings.setFallbackMinutes(45); settings.setWorkDays("1,2,3,4,5,6,7");
        settings.setOriginName("双流"); settings.setOriginLat(30.57); settings.setOriginLon(103.92);
        settings.setDestinationName("武侯"); settings.setDestinationLat(30.63); settings.setDestinationLon(104.05);
        assertEquals(200,controller.saveSettings(settings).getStatus());
        CommuteService service=new CommuteService(db,null,null); service.createTodayRecords(); service.createTodayRecords();
        assertEquals(1,db.queryForObject("select count(*) from commute_record where user_id=? and commute_date=? and direction='OUTBOUND'",Integer.class,user,now.toLocalDate()));
    }
    @Test void separateRainAveragesExcludeLegacyAndUnknownRecords() {
        String owner=UUID.randomUUID().toString();login(owner);LocalDate date=LocalDate.now(CommuteService.ZONE);
        CommuteSettings s=new CommuteSettings();s.setArrivalTime(LocalTime.of(9,0));s.setNoticeTime(LocalTime.of(7,0));s.setBufferMinutes(10);s.setFallbackMinutes(45);s.setWorkDays("1,2,3,4,5,6,7");s.setOriginName("双流");s.setOriginLat(30.57);s.setOriginLon(103.92);s.setDestinationName("武侯");s.setDestinationLat(30.63);s.setDestinationLon(104.05);
        assertEquals(200,controller.saveSettings(s).getStatus());
        for(int i=1;i<=6;i++) db.update("insert into commute_record(id,user_id,commute_date,direction,duration_minutes,actual_rain,include_stats,create_time,update_time) values(?,?,?,'OUTBOUND',?,?,1,now(),now())",UUID.randomUUID().toString(),owner,date.minusWeeks(i),i<=2?40:i<=4?60:300,i<=2?"DRY":i<=4?"RAIN":i==5?"UNKNOWN":null);
        Map<String,Object> result=controller.recommendation().getData();
        CommuteRecommendation.Result dry=(CommuteRecommendation.Result)result.get("dry"),rain=(CommuteRecommendation.Result)result.get("rain");
        assertEquals(40,dry.minutes);assertEquals(60,rain.minutes);assertEquals(2,dry.sampleCount);assertEquals(2,rain.sampleCount);
    }
    @Test void settingsRecordsAndWeatherFallbackArePrivate(){
        String owner=UUID.randomUUID().toString(); login(owner);
        CommuteSettings s=new CommuteSettings(); s.setArrivalTime(LocalTime.of(9,0)); s.setNoticeTime(LocalTime.of(7,0));
        s.setBufferMinutes(10); s.setFallbackMinutes(45); s.setWorkDays("1,2,3,4,5,6,7");
        s.setOriginName("双流"); s.setOriginLat(30.57); s.setOriginLon(103.92);
        s.setDestinationName("武侯"); s.setDestinationLat(30.63); s.setDestinationLon(104.05); s.setEmailEnabled(false);s.setSiteEnabled(true);s.setPushEnabled(true);
        assertEquals(200,controller.saveSettings(s).getStatus());
        assertNotNull(controller.settings().getData());
        assertTrue(Boolean.TRUE.equals(controller.settings().getData().get("siteEnabled")) || "1".equals(String.valueOf(controller.settings().getData().get("siteEnabled"))));
        Map<String,Object> recommendation=controller.recommendation().getData();
        assertEquals(45,recommendation.get("minutes")); assertEquals("UNKNOWN",recommendation.get("weatherGroup"));
        CommuteRecordInput input=new CommuteRecordInput(); LocalDate date=LocalDate.now(CommuteService.ZONE);
        input.setCommuteDate(date); input.setDirection("OUTBOUND"); input.setDepartureTime(date.atTime(8,0)); input.setArrivalTime(date.atTime(8,40)); input.setIncludeStats(true);input.setActualRain("RAIN");
        assertEquals(200,controller.saveRecord(input).getStatus());
        List<Map<String,Object>> records=controller.records(null).getData(); assertEquals(1,records.size());
        assertEquals("RAIN",records.get(0).get("actualRain"));
        String id=records.get(0).get("id").toString();
        input.setId(id); input.setDirection("RETURN");
        assertEquals(200,controller.saveRecord(input).getStatus());
        assertEquals(1,controller.records(null).getData().size());
        assertEquals("RETURN",controller.records(null).getData().get(0).get("direction"));
        login(UUID.randomUUID().toString()); assertNull(controller.settings().getData()); assertTrue(controller.records(null).getData().isEmpty());
        assertEquals(404,controller.saveRecord(input).getStatus());
        controller.deleteRecord(Collections.singletonMap("id",id));
        assertEquals(1,db.queryForObject("select count(*) from commute_record where id=?",Integer.class,id));
        login(owner); controller.deleteRecord(Collections.singletonMap("id",id)); assertTrue(controller.records(null).getData().isEmpty());
        s.setNoticeTime(LocalTime.of(21,0));s.setNoticeDay(0);
        assertEquals(400,controller.saveSettings(s).getStatus());
        s.setNoticeDay(1);assertEquals(200,controller.saveSettings(s).getStatus());
        assertEquals(date.plusDays(1).toString(),controller.recommendation().getData().get("date"));
        controller.pauseToday();
        assertEquals(1,db.queryForObject("select count(*) from commute_notice_log where user_id=? and notice_date=? and notify_type='all'",Integer.class,owner,date.plusDays(1)));
    }
}
