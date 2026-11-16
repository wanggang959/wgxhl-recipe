package com.wgxhl.recipe.commute;

import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CommuteControllerTest {
    @AfterEach void clear() { UserContext.clear(); }
    @Test void deletesOnlyCurrentUsersRecord() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        CommuteController controller=new CommuteController(db,mock(QWeatherClient.class),mock(CommuteService.class));
        AppUser user=new AppUser(); user.setId("user-a"); UserContext.set(user);
        controller.deleteRecord(Collections.singletonMap("id","record-owned-by-user-b"));
        verify(db).update("delete from commute_record where id=? and user_id=?","record-owned-by-user-b","user-a");
    }
    @Test void invalidRecordDoesNotWriteDatabase() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        CommuteController controller=new CommuteController(db,mock(QWeatherClient.class),mock(CommuteService.class));
        assertEquals(400,controller.saveRecord(new CommuteRecordInput()).getStatus());
        verifyNoInteractions(db);
    }
}
