package com.wgxhl.recipe.commute;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.wgxhl.recipe.mail.MailDeliveryService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.config.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.sql.Date;
import java.util.*;

@Service
public class CommuteService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger LOG = LoggerFactory.getLogger(CommuteService.class);
    private final JdbcTemplate db;
    private final QWeatherClient weather;
    private final MailDeliveryService mail;
    private final UserPushSubscriptionService push;
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    public CommuteService(JdbcTemplate db, QWeatherClient weather, MailDeliveryService mail) {
        this(db,weather,mail,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CommuteService(JdbcTemplate db,QWeatherClient weather,MailDeliveryService mail,UserPushSubscriptionService push) {
        this.db = db; this.weather = weather; this.mail = mail; this.push=push;
    }
    public Map<String, Object> recommend(String userId, LocalDate date) {
        List<Map<String, Object>> rows = db.queryForList("select * from commute_settings where user_id=?", userId);
        if (rows.isEmpty()) return Collections.singletonMap("configured", false);
        Map<String,Object> s = rows.get(0);
        LocalTime arrival = LocalTime.parse(s.get("arrival_time").toString());
        int fallback = ((Number)s.get("fallback_minutes")).intValue();
        int buffer = ((Number)s.get("buffer_minutes")).intValue();
        LocalDateTime target = date.atTime(arrival);
        List<CommuteRecommendation.Sample> samples = db.query("select commute_date,duration_minutes,actual_rain,include_stats from commute_record where user_id=? and direction='OUTBOUND' and commute_date>=? and commute_date<? and duration_minutes is not null order by commute_date desc limit 100",
                (rs,n) -> new CommuteRecommendation.Sample(rs.getDate(1).toLocalDate(),rs.getInt(2),rs.getString(3),rs.getBoolean(4)), userId, date.minusWeeks(8),date);
        CommuteRecommendation.Result dry = CommuteRecommendation.calculateForRain(date,arrival,buffer,fallback,"DRY",samples);
        CommuteRecommendation.Result rain = CommuteRecommendation.calculateForRain(date,arrival,buffer,fallback,"RAIN",samples);
        Forecast origin = forecast(s,"origin",target);
        Forecast destination = forecast(s,"destination",target);
        String group = origin.rain || destination.rain ? "RAIN" : origin.known && destination.known ? "DRY" : "UNKNOWN";
        CommuteRecommendation.Result result = dry;
        Map<String,Object> answer = new LinkedHashMap<>();
        answer.put("configured",true); answer.put("date",date.toString());
        answer.put("departure",result.departure.toString()); answer.put("arrival",target.toString());
        answer.put("minutes",result.minutes); answer.put("sampleCount",result.sampleCount); answer.put("source",result.source);
        answer.put("dry",dry); answer.put("rain",rain);
        answer.put("bufferMinutes",buffer); answer.put("weatherGroup",group);
        answer.put("weatherSummary",origin.text+"\n"+destination.text);
        List<String> mailStates=db.query("select status from commute_notice_log where user_id=? and notice_date=? and notify_type in ('email','all') order by notify_type limit 1",(rs,n)->rs.getString(1),userId,date);
        boolean enabled=Boolean.TRUE.equals(s.get("email_enabled")) || "1".equals(String.valueOf(s.get("email_enabled")));
        answer.put("mailStatus",mailStates.isEmpty() ? (enabled ? "PENDING" : "OFF") : mailStates.get(0));
        answer.put("channelStatuses",db.queryForList("select notify_type as channel,status,error_message as message from commute_notice_log where user_id=? and notice_date=?",userId,date));
        answer.put("workDay",Arrays.asList(s.get("work_days").toString().split(",")).contains(String.valueOf(date.getDayOfWeek().getValue())));
        return answer;
    }
    private Forecast forecast(Map<String,Object> s,String prefix,LocalDateTime time) {
        String name=s.get(prefix+"_name").toString();
        try {
            double lat=((Number)s.get(prefix+"_lat")).doubleValue(), lon=((Number)s.get(prefix+"_lon")).doubleValue();
            return parseDaily(cached(lat,lon),name,time.toLocalDate());
        } catch (Exception ignored) { /* Forecast failure must not prevent a commute recommendation. */ }
        return new Forecast(false,false,name+"：天气暂不可用");
    }
    static Forecast parseDaily(JsonNode data,String name,LocalDate date) {
            for (JsonNode h:data.path("days")) {
                LocalDate at=OffsetDateTime.parse(h.path("forecastStartTime").asText()).atZoneSameInstant(ZONE).toLocalDate();
                if (date.equals(at)) {
                    JsonNode daytime=h.path("daytime"), precipitation=daytime.path("precipitation");
                    int code=daytime.path("condition").path("code").asInt();
                    boolean rain=code>=300&&code<500 || precipitation.path("amount").path("value").asDouble()>0;
                    String probability=precipitation.path("probability").isNumber()?"，白天降水概率 "+Math.round(precipitation.path("probability").asDouble()*100)+"%":"";
                    String temperature=h.path("temperatureMin").path("value").isNumber() && h.path("temperatureMax").path("value").isNumber()?"，气温 "+Math.round(h.path("temperatureMin").path("value").asDouble())+"～"+Math.round(h.path("temperatureMax").path("value").asDouble())+"℃":"";
                    return new Forecast(true,rain,name+"："+daytime.path("condition").path("text").asText("暂无")+temperature+probability);
                }
            }
        return new Forecast(false,false,name+"：天气暂不可用");
    }
    private synchronized JsonNode cached(double lat,double lon) throws Exception {
        String key=String.format(Locale.ROOT,"%.2f,%.2f",lat,lon);
        Cached c=cache.get(key); long now=System.currentTimeMillis();
        if(c!=null && now-c.at<1800000) return c.data;
        JsonNode data=weather.daily(lat,lon);
        if(cache.size()>=256) cache.remove(cache.keySet().iterator().next());
        cache.put(key,new Cached(now,data)); return data;
    }
    public void snapshot(String userId,Map<String,Object> r) {
        db.update("insert into commute_record(id,user_id,commute_date,direction,weather_group,weather_summary,weather_forecast_time,create_time,update_time) values(?,?,?,'OUTBOUND',?,?,now(),now(),now()) on duplicate key update weather_group=if(weather_group is null,values(weather_group),weather_group),weather_summary=if(weather_summary is null,values(weather_summary),weather_summary)",IdUtil.simpleUUID(),userId,r.get("date"),r.get("weatherGroup"),r.get("weatherSummary"));
    }
    @Scheduled(cron="0 * * * * *",zone="Asia/Shanghai")
    public void notifyDaily() {
        LocalDateTime now=LocalDateTime.now(ZONE);
        try {
            List<Map<String,Object>> users=db.queryForList("select s.*,u.email,u.email_verified,(select m.household_id from household_member m join household h on h.id=m.household_id where m.user_id=s.user_id and m.status='normal' and h.status='normal' order by (m.household_id=u.active_household_id) desc,m.id limit 1) as household_id from commute_settings s join app_user u on u.id=s.user_id where (s.email_enabled=1 or s.site_enabled=1 or s.push_enabled=1) and u.status='normal' and s.notice_time<=? and (s.notice_day=1 or s.arrival_time>=?) and find_in_set(weekday(date_add(?,interval s.notice_day day))+1,s.work_days)>0 and not exists(select 1 from commute_notice_log l where l.user_id=s.user_id and l.notice_date=date_add(?,interval s.notice_day day) and (l.notify_type='all' or (l.notify_type='email' and s.site_enabled=0 and s.push_enabled=0))) and ((s.site_enabled=1 and not exists(select 1 from commute_notice_log x where x.user_id=s.user_id and x.notice_date=date_add(?,interval s.notice_day day) and x.notify_type='site')) or (s.email_enabled=1 and not exists(select 1 from commute_notice_log x where x.user_id=s.user_id and x.notice_date=date_add(?,interval s.notice_day day) and x.notify_type='email')) or (s.push_enabled=1 and not exists(select 1 from commute_notice_log x where x.user_id=s.user_id and x.notice_date=date_add(?,interval s.notice_day day) and x.notify_type='push'))) order by s.user_id limit 200", now.toLocalTime(),now.toLocalTime(),Date.valueOf(now.toLocalDate()),Date.valueOf(now.toLocalDate()),Date.valueOf(now.toLocalDate()),Date.valueOf(now.toLocalDate()),Date.valueOf(now.toLocalDate()));
            for(Map<String,Object> u:users) {
                LocalDate commuteDate=now.toLocalDate().plusDays(((Number)u.getOrDefault("notice_day",0)).intValue());
                if(!Arrays.asList(u.get("work_days").toString().split(",")).contains(String.valueOf(commuteDate.getDayOfWeek().getValue()))) continue;
                LocalTime notice=LocalTime.parse(u.get("notice_time").toString());
                if(now.toLocalTime().isBefore(notice) || (commuteDate.equals(now.toLocalDate()) && now.toLocalTime().isAfter(LocalTime.parse(u.get("arrival_time").toString())))) continue;
                String user=u.get("user_id").toString();
                List<String> completed=db.query("select notify_type from commute_notice_log where user_id=? and notice_date=?",(rs,n)->rs.getString(1),user,commuteDate);
                if(completed.contains("all")) continue;
                if((!flag(u,"site_enabled") || completed.contains("site")) && (!flag(u,"email_enabled") || completed.contains("email")) && (!flag(u,"push_enabled") || completed.contains("push"))) continue;
                Map<String,Object> result;
                try { result=recommend(user,commuteDate); snapshot(user,result); }
                catch(Exception e) { LOG.warn("Commute recommendation failed, type={}",e.getClass().getSimpleName()); continue; }
                CommuteRecommendation.Result dry=(CommuteRecommendation.Result)result.get("dry"), rain=(CommuteRecommendation.Result)result.get("rain");
                String detail="不下雨建议 "+dry.departure.toString().replace('T',' ')+"，下雨建议 "+rain.departure.toString().replace('T',' ')+" 前出门；已含预留 "+result.get("bufferMinutes")+" 分钟。"+result.get("weatherSummary");
                for(String channel:Arrays.asList("site","email","push")) {
                    if(!flag(u,channel+"_enabled") || completed.contains(channel)) continue;
                    String id=IdUtil.simpleUUID();
                    try { db.update("insert into commute_notice_log(id,user_id,notice_date,notify_type,status,create_time,update_time) values(?,?,?,?,'IN_FLIGHT',now(),now())",id,user,commuteDate,channel); }
                    catch(DuplicateKeyException e) { continue; }
                    String state="SUCCESS",message=null;
                    try {
                        String household=Objects.toString(u.get("household_id"),"");
                        if("email".equals(channel)) {
                            if(!flag(u,"email_verified") || u.get("email")==null || !mail.isReady(MailDeliveryService.Kind.COMMUTE)) { state="SKIP";message="邮箱未验证或通勤邮件模板未配置"; }
                            else mail.sendCommute(u.get("email").toString(),mailVariables(now.toLocalDate(),commuteDate,result,dry,rain),detail);
                        } else if(household.isEmpty()) { state="SKIP";message="暂无有效家庭"; }
                        else if("site".equals(channel)) {
                            db.update("insert into notification(id,household_id,user_id,title,content,notification_type,related_id,is_read,create_time,update_time) values(?,?,?,'通勤出门提醒',?,'COMMUTE',?,0,now(),now())",id,household,user,detail,commuteDate.toString());
                        } else {
                            int sent=TenantContext.callWith(household,()->push==null ? 0 : push.sendToUserIds(Collections.singletonList(user),"通勤出门提醒",detail,"/#/commute","commute-"+user+"-"+commuteDate));
                            if(sent==0) {state="SKIP";message="未有可用设备或推送未被接受";}
                        }
                    } catch(Exception e) { state="UNKNOWN";message="发送结果未确认，不自动重发";LOG.warn("Commute delivery uncertain, channel={}, type={}",channel,e.getClass().getSimpleName()); }
                    db.update("update commute_notice_log set status=?,error_message=?,update_time=now() where id=?",state,message,id);
                }
            }
        } catch(Exception e) { LOG.warn("Commute scan failed, type={}",e.getClass().getSimpleName()); }
    }
    private static boolean flag(Map<String,Object> row,String name) {return Boolean.TRUE.equals(row.get(name)) || "1".equals(String.valueOf(row.get(name)));}
    static Map<String,String> mailVariables(LocalDate sent,LocalDate commuteDate,Map<String,Object> result,CommuteRecommendation.Result dry,CommuteRecommendation.Result rain) {
        Map<String,String> variables=new LinkedHashMap<>();
        variables.put("sendDate",sent.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE",Locale.SIMPLIFIED_CHINESE)));
        variables.put("dayLabel",commuteDate.equals(sent)?"今天":commuteDate.equals(sent.plusDays(1))?"明天":commuteDate.toString());
        variables.put("dryDepartureTime",formatDeparture(dry.departure,commuteDate));
        variables.put("rainDepartureTime",formatDeparture(rain.departure,commuteDate));
        variables.put("dryBasis",basis(dry)); variables.put("rainBasis",basis(rain));
        variables.put("arrivalTime",LocalDateTime.parse(result.get("arrival").toString()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        variables.put("bufferMinutes",String.valueOf(result.get("bufferMinutes")));
        variables.put("weatherSummary",String.valueOf(result.get("weatherSummary")));
        return variables;
    }
    private static String formatDeparture(LocalDateTime time,LocalDate date) {
        return time.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+(time.toLocalDate().isBefore(date)?"（前一天）":"");
    }
    private static String basis(CommuteRecommendation.Result r) {
        return "FALLBACK".equals(r.source)?"记录不足，按预计 "+r.minutes+" 分钟":"预计通勤 "+r.minutes+" 分钟 · "+r.sampleCount+" 条记录";
    }
    public LocalDate targetDate(String userId) {
        List<Integer> offsets=db.query("select notice_day from commute_settings where user_id=?",(rs,n)->rs.getInt(1),userId);
        return LocalDate.now(ZONE).plusDays(offsets.isEmpty()?0:offsets.get(0));
    }
    private static final class Cached { final long at; final JsonNode data; Cached(long at,JsonNode data){this.at=at;this.data=data;} }
    static final class Forecast { final boolean known,rain; final String text; Forecast(boolean known,boolean rain,String text){this.known=known;this.rain=rain;this.text=text;} }
}
