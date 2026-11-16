package com.wgxhl.recipe.commute;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/commute")
public class CommuteController {
    private final JdbcTemplate db;
    private final QWeatherClient weather;
    private final CommuteService service;
    private final java.util.Map<String,long[]> requestLimits = new java.util.HashMap<>();

    private synchronized boolean allowLookup() {
        long now=System.currentTimeMillis();
        requestLimits.entrySet().removeIf(e -> now-e.getValue()[0]>=60000);
        String id=UserContext.requireUserId();
        if(!requestLimits.containsKey(id) && requestLimits.size()>=2000) return false;
        long[] limit=requestLimits.computeIfAbsent(id,k -> new long[]{now,0});
        return ++limit[1]<=20;
    }

    public CommuteController(JdbcTemplate db, QWeatherClient weather, CommuteService service) {
        this.db = db; this.weather = weather; this.service = service;
    }

    @PostMapping("/recommendation")
    public ApiResponse<Map<String,Object>> recommendation() {
        Map<String,Object> result=service.recommend(UserContext.requireUserId(), service.targetDate(UserContext.requireUserId()));
        return ApiResponse.success(result);
    }

    @PostMapping("/pause-today")
    public ApiResponse<Void> pauseToday() {
        db.update("insert ignore into commute_notice_log(id,user_id,notice_date,notify_type,status,create_time,update_time) values(?,?,?,'all','SKIP',now(),now())",IdUtil.simpleUUID(),UserContext.requireUserId(),service.targetDate(UserContext.requireUserId()));
        return ApiResponse.success(null);
    }

    @PostMapping("/cities")
    public ApiResponse<JsonNode> cities(@RequestBody Map<String, String> request) {
        if(!allowLookup()) return ApiResponse.fail(429,"查询过于频繁，请稍后重试");
        try { return ApiResponse.success(weather.searchCity(request.get("keyword"))); }
        catch (IllegalArgumentException e) { return ApiResponse.fail(400, e.getMessage()); }
        catch (Exception e) { return ApiResponse.fail("地点搜索暂不可用，请稍后重试"); }
    }

    @PostMapping("/resolve-share")
    public ApiResponse<AmapShareParser.Location> resolveShare(@RequestBody Map<String, String> request) {
        if(!allowLookup()) return ApiResponse.fail(429,"查询过于频繁，请稍后重试");
        try { return ApiResponse.success(AmapShareParser.resolve(request.get("url"))); }
        catch (IllegalArgumentException e) { return ApiResponse.fail(400, e.getMessage()); }
        catch (Exception e) { return ApiResponse.fail("无法解析分享链接，请稍后重试"); }
    }

    @PostMapping("/settings")
    public ApiResponse<Map<String, Object>> settings() {
        List<Map<String, Object>> rows = db.queryForList("select arrival_time as arrivalTime, notice_time as noticeTime, buffer_minutes as bufferMinutes, fallback_minutes as fallbackMinutes, work_days as workDays, origin_name as originName, origin_lat as originLat, origin_lon as originLon, destination_name as destinationName, destination_lat as destinationLat, destination_lon as destinationLon, email_enabled as emailEnabled from commute_settings where user_id=?", UserContext.requireUserId());
        if(!rows.isEmpty()) rows.get(0).putAll(db.queryForMap("select site_enabled as siteEnabled,push_enabled as pushEnabled,notice_day as noticeDay from commute_settings where user_id=?",UserContext.requireUserId()));
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    @PostMapping("/settings/save")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Void> saveSettings(@RequestBody CommuteSettings s) {
        if (s.getArrivalTime() == null || s.getNoticeTime() == null || s.getBufferMinutes() == null
                || s.getBufferMinutes() < 0 || s.getBufferMinutes() > 180 || s.getFallbackMinutes() == null
                || s.getFallbackMinutes() < 1 || s.getFallbackMinutes() > 360 || s.getWorkDays() == null
                || !s.getWorkDays().matches("[1-7](,[1-7]){0,6}") || !validName(s.getOriginName())
                || !validName(s.getDestinationName()) || !validCoordinates(s.getOriginLat(), s.getOriginLon())
                || !validCoordinates(s.getDestinationLat(), s.getDestinationLon()))
            return ApiResponse.fail(400, "请检查到达时间、提醒时间和地点设置");
        if(s.getNoticeDay()==null || s.getNoticeDay()<0 || s.getNoticeDay()>1) return ApiResponse.fail(400,"请选择当天或头天提醒");
        java.time.LocalDateTime arrival=LocalDate.of(2026,1,2).atTime(s.getArrivalTime());
        java.time.LocalDateTime notice=arrival.toLocalDate().minusDays(s.getNoticeDay()).atTime(s.getNoticeTime());
        if(!notice.isBefore(arrival.minusMinutes(s.getFallbackMinutes()+s.getBufferMinutes())))
            return ApiResponse.fail(400,"提醒时间应早于预计出门时间");
        if (Boolean.TRUE.equals(s.getEmailEnabled()) && (!Boolean.TRUE.equals(UserContext.require().getEmailVerified())
                || UserContext.require().getEmail() == null || UserContext.require().getEmail().trim().isEmpty()))
            return ApiResponse.fail(400, "请先绑定并验证邮箱");
        db.update("insert into commute_settings(user_id,arrival_time,notice_time,buffer_minutes,fallback_minutes,work_days,origin_name,origin_lat,origin_lon,destination_name,destination_lat,destination_lon,email_enabled,create_time,update_time) values(?,?,?,?,?,?,?,?,?,?,?,?,?,now(),now()) on duplicate key update arrival_time=values(arrival_time),notice_time=values(notice_time),buffer_minutes=values(buffer_minutes),fallback_minutes=values(fallback_minutes),work_days=values(work_days),origin_name=values(origin_name),origin_lat=values(origin_lat),origin_lon=values(origin_lon),destination_name=values(destination_name),destination_lat=values(destination_lat),destination_lon=values(destination_lon),email_enabled=values(email_enabled),update_time=now()",
                UserContext.requireUserId(), s.getArrivalTime(), s.getNoticeTime(), s.getBufferMinutes(), s.getFallbackMinutes(), s.getWorkDays(), s.getOriginName().trim(), s.getOriginLat(), s.getOriginLon(), s.getDestinationName().trim(), s.getDestinationLat(), s.getDestinationLon(), Boolean.TRUE.equals(s.getEmailEnabled()));
        db.update("update commute_settings set site_enabled=?,push_enabled=?,notice_day=? where user_id=?",Boolean.TRUE.equals(s.getSiteEnabled()),Boolean.TRUE.equals(s.getPushEnabled()),s.getNoticeDay(),UserContext.requireUserId());
        return ApiResponse.success(null);
    }

    @PostMapping("/records")
    public ApiResponse<List<Map<String, Object>>> records(@RequestBody(required = false) Map<String, Object> request) {
        int page = 1;
        if (request != null && request.get("page") instanceof Number) page = ((Number) request.get("page")).intValue();
        page = Math.max(1, Math.min(page, 1000));
        return ApiResponse.success(db.queryForList("select id,commute_date as commuteDate,direction,departure_time as departureTime,arrival_time as arrivalTime,duration_minutes as durationMinutes,transport,remark,include_stats as includeStats,actual_rain as actualRain,weather_group as weatherGroup,weather_summary as weatherSummary from commute_record where user_id=? and commute_date<=? order by commute_date desc,direction asc limit 30 offset ?", UserContext.requireUserId(), LocalDate.now(CommuteService.ZONE), (page - 1) * 30));
    }

    @PostMapping("/statistics")
    public ApiResponse<Map<String,Object>> statistics() {
        LocalDate today = LocalDate.now(CommuteService.ZONE);
        return ApiResponse.success(db.queryForMap("select count(*) as allCount, avg(duration_minutes) as allAverage, count(case when actual_rain='RAIN' then 1 end) as rainCount, avg(case when actual_rain='RAIN' then duration_minutes end) as rainAverage, count(case when actual_rain='DRY' then 1 end) as dryCount, avg(case when actual_rain='DRY' then duration_minutes end) as dryAverage from commute_record where user_id=? and direction='OUTBOUND' and include_stats=1 and duration_minutes>0 and commute_date between ? and ?", UserContext.requireUserId(), today.minusDays(29), today));
    }

    @PostMapping("/records/save")
    public ApiResponse<Void> saveRecord(@RequestBody CommuteRecordInput input) {
        if (input.getCommuteDate() == null || input.getCommuteDate().isAfter(LocalDate.now(CommuteService.ZONE))
                || !("OUTBOUND".equals(input.getDirection()) || "RETURN".equals(input.getDirection()))
                || input.getDepartureTime() == null || input.getArrivalTime() == null
                || !input.getDepartureTime().toLocalDate().equals(input.getCommuteDate()))
            return ApiResponse.fail(400, "请检查通勤日期和时间");
        long seconds = Duration.between(input.getDepartureTime(), input.getArrivalTime()).getSeconds();
        if(input.getActualRain()!=null && !java.util.Arrays.asList("DRY","RAIN","UNKNOWN").contains(input.getActualRain())) return ApiResponse.fail(400,"请选择实际通勤天气");
        if (seconds <= 0 || seconds > 24 * 3600 || (input.getRemark() != null && input.getRemark().length() > 500)
                || (input.getTransport() != null && input.getTransport().length() > 30))
            return ApiResponse.fail(400, "请检查通勤时长或备注");
        if (input.getId() != null && !input.getId().trim().isEmpty()) {
            try {
                int changed=db.update("update commute_record set weather_group=if(commute_date=? and direction=?,weather_group,null),weather_summary=if(commute_date=? and direction=?,weather_summary,null),commute_date=?,direction=?,departure_time=?,arrival_time=?,duration_minutes=?,transport=?,remark=?,include_stats=?,actual_rain=coalesce(?,actual_rain),update_time=now() where id=? and user_id=?",
                        input.getCommuteDate(),input.getDirection(),input.getCommuteDate(),input.getDirection(),input.getCommuteDate(),input.getDirection(),input.getDepartureTime(),input.getArrivalTime(),(seconds+59)/60,input.getTransport(),input.getRemark(),!Boolean.FALSE.equals(input.getIncludeStats()),input.getActualRain(),input.getId(),UserContext.requireUserId());
                return changed==0 ? ApiResponse.fail(404,"记录不存在或无权修改") : ApiResponse.success(null);
            } catch(org.springframework.dao.DuplicateKeyException e) {
                return ApiResponse.fail(409,"该日期和方向已有记录，请编辑原记录");
            }
        }
        db.update("insert into commute_record(id,user_id,commute_date,direction,departure_time,arrival_time,duration_minutes,transport,remark,include_stats,actual_rain,create_time,update_time) values(?,?,?,?,?,?,?,?,?,?,?,now(),now()) on duplicate key update departure_time=values(departure_time),arrival_time=values(arrival_time),duration_minutes=values(duration_minutes),transport=values(transport),remark=values(remark),include_stats=values(include_stats),actual_rain=coalesce(values(actual_rain),actual_rain),update_time=now()",
                IdUtil.simpleUUID(), UserContext.requireUserId(), input.getCommuteDate(), input.getDirection(), input.getDepartureTime(), input.getArrivalTime(), (seconds + 59) / 60, input.getTransport(), input.getRemark(), !Boolean.FALSE.equals(input.getIncludeStats()),input.getActualRain());
        return ApiResponse.success(null);
    }

    @PostMapping("/records/delete")
    public ApiResponse<Void> deleteRecord(@RequestBody Map<String, String> input) {
        db.update("delete from commute_record where id=? and user_id=?", input.get("id"), UserContext.requireUserId());
        return ApiResponse.success(null);
    }

    private static boolean validName(String name) { return name != null && !name.trim().isEmpty() && name.length() <= 200; }
    private static boolean validCoordinates(Double lat, Double lon) { return lat != null && lon != null && Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180; }
}
