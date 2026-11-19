package com.wgxhl.recipe.ledger;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.ledger.dto.CompleteWithLedgerRequest;
import com.wgxhl.recipe.ledger.dto.LedgerPageQuery;
import com.wgxhl.recipe.ledger.entity.LedgerEntry;
import com.wgxhl.recipe.ledger.service.LedgerService;
import com.wgxhl.recipe.todo.service.TodoService;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.expense.service.SpecialExpenseItemService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
@Transactional
class LedgerIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired LedgerService ledger;
    @Autowired TodoService todos;
    @Autowired SpecialExpenseItemService expenses;
    String household, actorId;
    AppUser actor;

    @BeforeEach void setup() {
        household = UUID.randomUUID().toString(); actorId = UUID.randomUUID().toString();
        db.update("insert into household_member (id, household_id, user_id, member_role, status) values (?, ?, ?, 'member', 'normal')", UUID.randomUUID().toString(), household, actorId);
        actor = new AppUser(); actor.setId(actorId); actor.setUsername("ledger-test");
        TenantContext.set(household); UserContext.set(actor, "member");
    }
    @AfterEach void cleanup() { TenantContext.clear(); UserContext.clear(); }

    @Test void manualEntriesAreTenantScopedAndRejectOtherHouseholdPayers() {
        LedgerEntry entry = new LedgerEntry(); entry.setEntryType("EXPENSE"); entry.setCategory("购物");
        entry.setDescription("test"); entry.setAmount(new BigDecimal("19.90")); entry.setOccurredOn(LocalDate.now()); entry.setPayerId(actorId);
        assertEquals(200, ledger.createManual(entry).getStatus());
        LedgerPageQuery query = new LedgerPageQuery(); query.setMonth(LocalDate.now().toString().substring(0, 7));
        assertEquals(1, ledger.page(query).getData().getTotal());
        entry.setPayerId(UUID.randomUUID().toString()); assertNotEquals(200, ledger.createManual(entry).getStatus());
        TenantContext.set(UUID.randomUUID().toString());
        assertEquals(0, ledger.page(query).getData().getTotal());
    }

    @Test void recurringCompletionRecordsOneCycleAndRejectsStaleRetries() {
        String todo = UUID.randomUUID().toString(); LocalDateTime due = LocalDateTime.now().minusDays(2).withNano(0);
        db.update("insert into todo (id, household_id, title, category, create_user_id, owner_id, due_time, repeat_type, status) values (?, ?, 'renewal', 'OTHER', ?, ?, ?, 'MONTHLY', 'TODO')", todo, household, actorId, actorId, due);
        db.update("insert into todo_ledger_setting (todo_id, household_id, enabled, category, description, estimated_amount, create_time, update_time) values (?, ?, 1, '订阅', 'renewal', 100, now(), now())", todo, household);
        CompleteWithLedgerRequest request = new CompleteWithLedgerRequest(); request.setId(todo); request.setExpectedDueTime(due); request.setPayerId(actorId); request.setActualAmount(new BigDecimal("98.00"));
        assertEquals(200, todos.completeWithLedger(request, actor).getStatus());
        assertNotEquals(200, todos.completeWithLedger(request, actor).getStatus());
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where household_id=? and source_id=?", Integer.class, household, todo));
        assertEquals(0, new BigDecimal("98.00").compareTo(db.queryForObject("select amount from household_ledger_entry where household_id=? and source_id=?", BigDecimal.class, household, todo)));
        assertTrue(db.queryForObject("select due_time from todo where id=?", LocalDateTime.class, todo).isAfter(due));
        LedgerPageQuery history = new LedgerPageQuery(); history.setAllMonths(true); history.setSourceTodoId(todo);
        assertEquals(1, ledger.page(history).getData().getTotal());
        history.setSourceTodoId(UUID.randomUUID().toString());
        assertEquals(0, ledger.page(history).getData().getTotal());
    }

    @Test void expenseAndTodoCannotProduceTwoBillsForSamePurchase() {
        String project = UUID.randomUUID().toString();
        db.update("insert into special_expense_project (id, household_id, project_name, create_user_id) values (?, ?, 'project', ?)", project, household, actorId);
        SpecialExpenseItem item = new SpecialExpenseItem(); item.setProjectId(project); item.setItemName("purchase"); item.setPayerId(actorId); item.setOwnerId(actorId); item.setActualAmount(new BigDecimal("20.00")); item.setSyncToLedger(true);
        assertEquals(200, expenses.create(item, actor).getStatus());
        String todo = UUID.randomUUID().toString(); LocalDateTime due = LocalDateTime.now().minusDays(2).withNano(0);
        db.update("insert into todo (id, household_id, title, category, create_user_id, owner_id, due_time, repeat_type, status, related_type, related_id) values (?, ?, 'purchase', 'GROCERY', ?, ?, ?, 'NONE', 'TODO', 'SPECIAL_EXPENSE_ITEM', ?)", todo, household, actorId, actorId, due, item.getId());
        db.update("insert into todo_ledger_setting (todo_id, household_id, enabled, category, description, create_time, update_time) values (?, ?, 1, '购物', 'purchase', now(), now())", todo, household);
        CompleteWithLedgerRequest request = new CompleteWithLedgerRequest(); request.setId(todo); request.setExpectedDueTime(due); request.setPayerId(actorId); request.setActualAmount(new BigDecimal("20.00"));
        assertNotEquals(200, todos.completeWithLedger(request, actor).getStatus());
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where household_id=? and related_expense_id=?", Integer.class, household, item.getId()));
        assertEquals("TODO", db.queryForObject("select status from todo where id=?", String.class, todo));
    }

    @Test void invalidPayerDoesNotCompleteTodo() {
        String todo = UUID.randomUUID().toString(); LocalDateTime due = LocalDateTime.now().minusDays(2).withNano(0);
        db.update("insert into todo (id, household_id, title, category, create_user_id, owner_id, due_time, repeat_type, status) values (?, ?, 'test', 'OTHER', ?, ?, ?, 'MONTHLY', 'TODO')", todo, household, actorId, actorId, due);
        db.update("insert into todo_ledger_setting (todo_id, household_id, enabled, category, description, create_time, update_time) values (?, ?, 1, '订阅', 'test', now(), now())", todo, household);
        CompleteWithLedgerRequest request = new CompleteWithLedgerRequest(); request.setId(todo); request.setExpectedDueTime(due); request.setPayerId(UUID.randomUUID().toString()); request.setActualAmount(new BigDecimal("9.00"));
        assertNotEquals(200, todos.completeWithLedger(request, actor).getStatus());
        assertEquals(due, db.queryForObject("select due_time from todo where id=?", LocalDateTime.class, todo));
        assertEquals(0, db.queryForObject("select count(*) from household_ledger_entry where household_id=? and source_id=?", Integer.class, household, todo));
    }

    @Test void deletingLinkedBillKeepsTodoAndPreventsSameCycleBeingRecordedAgain() {
        String todo = UUID.randomUUID().toString(); LocalDateTime due = LocalDateTime.now().minusDays(2).withNano(0);
        db.update("insert into todo (id, household_id, title, category, create_user_id, owner_id, due_time, repeat_type, status) values (?, ?, 'test', 'OTHER', ?, ?, ?, 'NONE', 'TODO')", todo, household, actorId, actorId, due);
        db.update("insert into todo_ledger_setting (todo_id, household_id, enabled, category, description, create_time, update_time) values (?, ?, 1, '订阅', 'test', now(), now())", todo, household);
        CompleteWithLedgerRequest request = new CompleteWithLedgerRequest(); request.setId(todo); request.setExpectedDueTime(due); request.setPayerId(actorId); request.setActualAmount(new BigDecimal("9.00"));
        assertEquals(200, todos.completeWithLedger(request, actor).getStatus());
        String entry = db.queryForObject("select id from household_ledger_entry where household_id=? and source_id=?", String.class, household, todo);
        assertEquals(200, ledger.delete(entry).getStatus());
        LedgerPageQuery query = new LedgerPageQuery(); query.setMonth(LocalDate.now().toString().substring(0, 7));
        assertEquals(0, ledger.page(query).getData().getTotal());
        assertEquals(1, db.queryForObject("select count(*) from todo where id=?", Integer.class, todo));
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where id=? and deleted=1", Integer.class, entry));
        assertNotEquals(200, todos.completeWithLedger(request, actor).getStatus());
    }

    @Test void deletedExpenseBillIsRestoredOnlyWhenRequested() {
        String project = UUID.randomUUID().toString();
        db.update("insert into special_expense_project (id, household_id, project_name, create_user_id) values (?, ?, 'project', ?)", project, household, actorId);
        SpecialExpenseItem item = new SpecialExpenseItem(); item.setProjectId(project); item.setItemName("purchase"); item.setPayerId(actorId); item.setOwnerId(actorId); item.setActualAmount(new BigDecimal("20.00")); item.setSyncToLedger(true);
        assertEquals(200, expenses.create(item, actor).getStatus());
        String entry = db.queryForObject("select id from household_ledger_entry where household_id=? and related_expense_id=?", String.class, household, item.getId());
        assertEquals(200, ledger.delete(entry).getStatus());
        item.setSyncToLedger(false); item.setActualAmount(new BigDecimal("25.00"));
        assertEquals(200, expenses.update(item, actor).getStatus());
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where id=? and deleted=1", Integer.class, entry));
        item.setSyncToLedger(true);
        assertEquals(200, expenses.update(item, actor).getStatus());
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where id=? and deleted=0 and amount=25", Integer.class, entry));
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where household_id=? and related_expense_id=?", Integer.class, household, item.getId()));
    }

    @Test void ordinaryMemberCannotDeleteAnotherMembersBill() {
        LedgerEntry entry = new LedgerEntry(); entry.setEntryType("EXPENSE"); entry.setCategory("购物"); entry.setDescription("test"); entry.setAmount(new BigDecimal("5.00")); entry.setOccurredOn(LocalDate.now()); entry.setPayerId(actorId);
        String id = ledger.createManual(entry).getData().getId();
        AppUser other = new AppUser(); other.setId(UUID.randomUUID().toString()); other.setUsername("other");
        UserContext.set(other, "member");
        assertEquals(403, ledger.delete(id).getStatus());
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where id=?", Integer.class, id));
        UserContext.set(other, "admin");
        assertEquals(200, ledger.delete(id).getStatus());
    }

    @Test void householdCleanupNeverDeletesOtherHouseholdData() {
        LedgerEntry entry = new LedgerEntry(); entry.setEntryType("EXPENSE"); entry.setCategory("购物"); entry.setDescription("test"); entry.setAmount(new BigDecimal("5.00")); entry.setOccurredOn(LocalDate.now()); entry.setPayerId(actorId);
        String id = ledger.createManual(entry).getData().getId();
        String otherHousehold = UUID.randomUUID().toString(), otherEntry = UUID.randomUUID().toString();
        db.update("insert into household_ledger_entry (id, household_id, entry_type, category, description, amount, occurred_on, payer_id, created_by, create_time, update_time) values (?, ?, 'EXPENSE', 'test', 'other', 5, curdate(), ?, ?, now(), now())", otherEntry, otherHousehold, actorId, actorId);
        ledger.deleteHouseholdData();
        assertEquals(0, db.queryForObject("select count(*) from household_ledger_entry where id=?", Integer.class, id));
        assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where id=?", Integer.class, otherEntry));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousCompletionWritesOnlyOneBill() throws Exception {
        String todo = UUID.randomUUID().toString(); LocalDateTime due = LocalDateTime.now().minusDays(2).withNano(0);
        db.update("insert into todo (id, household_id, title, category, create_user_id, owner_id, due_time, repeat_type, status) values (?, ?, 'test', 'OTHER', ?, ?, ?, 'MONTHLY', 'TODO')", todo, household, actorId, actorId, due);
        db.update("insert into todo_ledger_setting (todo_id, household_id, enabled, category, description, create_time, update_time) values (?, ?, 1, '订阅', 'test', now(), now())", todo, household);
        java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> task = () -> {
                TenantContext.set(household); UserContext.set(actor, "member");
                try {
                    CompleteWithLedgerRequest request = new CompleteWithLedgerRequest(); request.setId(todo); request.setExpectedDueTime(due); request.setPayerId(actorId); request.setActualAmount(new BigDecimal("9.00"));
                    start.await(5, TimeUnit.SECONDS);
                    return todos.completeWithLedger(request, actor).getStatus();
                } finally { TenantContext.clear(); UserContext.clear(); }
            };
            java.util.concurrent.Future<Integer> first = executor.submit(task), second = executor.submit(task); start.countDown();
            int success = (first.get(15, TimeUnit.SECONDS) == 200 ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) == 200 ? 1 : 0);
            assertEquals(1, success);
            assertEquals(1, db.queryForObject("select count(*) from household_ledger_entry where household_id=? and source_id=?", Integer.class, household, todo));
        } finally {
            executor.shutdownNow();
            db.update("delete from household_ledger_entry where household_id=? and source_id=?", household, todo);
            db.update("delete from todo_ledger_setting where household_id=? and todo_id=?", household, todo);
            db.update("delete from todo where household_id=? and id=?", household, todo);
            db.update("delete from household_member where household_id=? and user_id=?", household, actorId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTodosForSameExpenseNeverCompleteWithoutRecording() throws Exception {
        String firstTodo=UUID.randomUUID().toString(), secondTodo=UUID.randomUUID().toString(), expenseId=UUID.randomUUID().toString();
        LocalDateTime due=LocalDateTime.now().minusDays(2).withNano(0);
        for (String id : new String[]{firstTodo,secondTodo}) {
            db.update("insert into todo (id, household_id, title, category, create_user_id, owner_id, due_time, repeat_type, status, related_type, related_id) values (?, ?, 'test', 'OTHER', ?, ?, ?, 'NONE', 'TODO', 'SPECIAL_EXPENSE_ITEM', ?)", id, household, actorId, actorId, due, expenseId);
            db.update("insert into todo_ledger_setting (todo_id, household_id, enabled, category, description, create_time, update_time) values (?, ?, 1, '购物', 'test', now(), now())", id, household);
        }
        java.util.concurrent.ExecutorService executor=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            java.util.function.Function<String,java.util.concurrent.Callable<Integer>> task = id -> () -> {
                TenantContext.set(household); UserContext.set(actor, "member");
                try {
                    CompleteWithLedgerRequest request=new CompleteWithLedgerRequest(); request.setId(id); request.setExpectedDueTime(due); request.setPayerId(actorId); request.setActualAmount(new BigDecimal("9.00")); start.await(5,TimeUnit.SECONDS);
                    try { return todos.completeWithLedger(request,actor).getStatus(); } catch (org.springframework.dao.DuplicateKeyException ex) { return 409; }
                } finally { TenantContext.clear(); UserContext.clear(); }
            };
            java.util.concurrent.Future<Integer> a=executor.submit(task.apply(firstTodo)),b=executor.submit(task.apply(secondTodo)); start.countDown();
            assertEquals(1,(a.get(15,TimeUnit.SECONDS)==200?1:0)+(b.get(15,TimeUnit.SECONDS)==200?1:0));
            assertEquals(1,db.queryForObject("select count(*) from household_ledger_entry where household_id=? and related_expense_id=?",Integer.class,household,expenseId));
            assertEquals(1,db.queryForObject("select count(*) from todo where household_id=? and id in (?,?) and status='DONE'",Integer.class,household,firstTodo,secondTodo));
        } finally {
            executor.shutdownNow();
            db.update("delete from household_ledger_entry where household_id=? and related_expense_id=?",household,expenseId);
            db.update("delete from todo_ledger_setting where household_id=? and todo_id in (?,?)",household,firstTodo,secondTodo);
            db.update("delete from todo where household_id=? and id in (?,?)",household,firstTodo,secondTodo);
            db.update("delete from household_member where household_id=? and user_id=?",household,actorId);
        }
    }
}
