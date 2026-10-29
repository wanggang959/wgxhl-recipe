package com.wgxhl.recipe.common.dto;

import org.junit.jupiter.api.Test;
import com.wgxhl.recipe.todo.dto.TodoPageDTO;
import com.wgxhl.recipe.notification.dto.NotificationPageDTO;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PageDTOTest {
    @Test
    void normalizesUntrustedPagingValues() {
        PageDTO page = new PageDTO();
        assertEquals(1L, page.getCurrent());
        assertEquals(10L, page.getSize());
        page.setCurrent(null); page.setSize(null);
        assertEquals(1L, page.getCurrent()); assertEquals(10L, page.getSize());
        page.setCurrent(-1L); page.setSize(0L);
        assertEquals(1L, page.getCurrent()); assertEquals(1L, page.getSize());
        page.setCurrent(Long.MAX_VALUE); page.setSize(100000L);
        assertEquals(10_000L, page.getCurrent()); assertEquals(50L, page.getSize());
    }

    @Test
    void independentPageDtosAreBoundedToo() {
        TodoPageDTO todo = new TodoPageDTO();
        todo.setCurrent(-2); todo.setSize(100000);
        assertEquals(1, todo.getCurrent()); assertEquals(50, todo.getSize());
        NotificationPageDTO notices = new NotificationPageDTO();
        notices.setCurrent(Long.MAX_VALUE); notices.setSize(-1);
        assertEquals(10_000, notices.getCurrent()); assertEquals(1, notices.getSize());
    }
}
