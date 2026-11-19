package com.wgxhl.recipe.vehicle;

import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named="test.integration.enabled", matches="true")
@Transactional
class VehicleIntegrationTest {
    @Autowired VehicleController controller;
    @Autowired VehicleImportController importer;
    @Autowired VehicleExpenseTypes expenseTypes;
    @Autowired JdbcTemplate db;
    @AfterEach void cleanup() { TenantContext.clear(); UserContext.clear(); }
    @Test void yearlyFuelAveragesUseTheSameCalculatorWithoutCrossingYears() {
        TenantContext.set(UUID.randomUUID().toString());
        AppUser actor = new AppUser(); actor.setId(UUID.randomUUID().toString()); UserContext.set(actor, "member");
        Vehicle selected = new Vehicle(); selected.setName("Year test"); selected.setVehicleType("MOTORCYCLE");
        selected = controller.saveVehicle(selected).getData();
        for (int i = 0; i < 4; i++) {
            VehicleFuelRecord row = new VehicleFuelRecord();
            row.setVehicleId(selected.getId());
            row.setOccurredAt(LocalDateTime.of(2025 + i / 2, 1 + i % 2, 5, 12, 0));
            row.setMileage(BigDecimal.valueOf(1000 + i * 200));
            row.setLiters(BigDecimal.valueOf(i < 2 ? 10 : 6));
            row.setPaidAmount(new BigDecimal("40.00"));
            row.setFullTank(true); row.setWarningLight(false); row.setMissedPrevious(false);
            assertEquals(200, controller.saveFuel(row).getStatus());
        }
        IdDTO request = new IdDTO(); request.setId(selected.getId());
        Map<?,?> data = (Map<?,?>) controller.summary(request).getData().get("fuelYearAverages");
        assertEquals(new BigDecimal("5.00"), data.get(2025));
        assertEquals(new BigDecimal("3.00"), data.get(2026));
    }
    @Test void expenseTypesAreFamilyScopedAndDisablingDoesNotChangeHistory() {
        String home = UUID.randomUUID().toString();
        TenantContext.set(home);
        AppUser actor = new AppUser(); actor.setId(UUID.randomUUID().toString()); UserContext.set(actor, "member");
        assertEquals(5, expenseTypes.list().getData().size());
        assertNotEquals(200, expenseTypes.save(new VehicleExpenseTypes.Type(null, "维修保养", "#102030", true)).getStatus());
        List<VehicleExpenseTypes.Type> list = expenseTypes.save(new VehicleExpenseTypes.Type(null, "轮胎", "#102030", true)).getData();
        VehicleExpenseTypes.Type type = list.stream().filter(row -> "轮胎".equals(row.getName())).findFirst().orElseThrow(AssertionError::new);
        Vehicle v = new Vehicle(); v.setName("Type test"); v.setVehicleType("CAR");
        v = controller.saveVehicle(v).getData();
        VehicleExpenseRecord bill = new VehicleExpenseRecord();
        bill.setVehicleId(v.getId()); bill.setOccurredAt(LocalDateTime.now()); bill.setAmount(new BigDecimal("100.00"));
        bill.setCategoryKey(type.getKey()); bill.setCategory("Ignored client label");
        assertEquals("轮胎", controller.saveExpense(bill).getData().getCategory());
        type.setEnabled(false);
        assertEquals(200, expenseTypes.save(type).getStatus());
        bill.setId(null);
        assertNotEquals(200, controller.saveExpense(bill).getStatus());
        IdDTO id = new IdDTO(); id.setId(v.getId());
        assertEquals("轮胎", controller.expenseList(id).getData().get(0).getCategory());
        TenantContext.set(UUID.randomUUID().toString());
        assertEquals(5, expenseTypes.list().getData().size());
        assertNotEquals(200, expenseTypes.save(type).getStatus());
    }
    @Test void vehiclePhotoSurvivesOlderClientsAndCanBeReplacedAndCleared() {
        String home = UUID.randomUUID().toString();
        TenantContext.set(home);
        AppUser actor = new AppUser(); actor.setId(UUID.randomUUID().toString()); UserContext.set(actor, "member");
        String first = "vehicle/" + UUID.randomUUID() + ".jpg";
        String second = "vehicle/" + UUID.randomUUID() + ".webp";
        Vehicle v = new Vehicle(); v.setName("Photo test"); v.setVehicleType("CAR");
        v.setImagePath(first);
        Vehicle saved = controller.saveVehicle(v).getData();
        assertNotNull(saved);
        assertEquals(first, controller.list().getData().get(0).getImagePath());

        v.setId(saved.getId());
        v.setImagePath(null);
        v.setName("Renamed");
        assertEquals(first, controller.saveVehicle(v).getData().getImagePath());
        v.setImagePath("https://example.com/car.png");
        assertNotEquals(200, controller.saveVehicle(v).getStatus());
        v.setImagePath(second);
        assertEquals(second, controller.saveVehicle(v).getData().getImagePath());
        TenantContext.set(UUID.randomUUID().toString());
        assertTrue(controller.list().getData().isEmpty());
        assertNotEquals(200, controller.saveVehicle(v).getStatus());
        TenantContext.set(home);
        v.setImagePath("");
        assertEquals(200, controller.saveVehicle(v).getStatus());
        assertNull(controller.list().getData().get(0).getImagePath());
    }
    @Test void vehicleAndRecordsAreIsolatedByHousehold() {
        String home=UUID.randomUUID().toString();
        TenantContext.set(home); AppUser actor=new AppUser(); actor.setId(UUID.randomUUID().toString()); UserContext.set(actor, "member");
        Vehicle v=new Vehicle(); v.setName("test"); v.setVehicleType("CAR");
        v=controller.saveVehicle(v).getData(); assertNotNull(v);
        VehicleFuelRecord r=new VehicleFuelRecord(); r.setVehicleId(v.getId()); r.setOccurredAt(LocalDateTime.now()); r.setMileage(new BigDecimal("1000")); r.setLiters(new BigDecimal("10")); r.setPaidAmount(new BigDecimal("80")); r.setFullTank(true); r.setWarningLight(false); r.setMissedPrevious(false);
        assertEquals(200,controller.saveFuel(r).getStatus());
        IdDTO dto=new IdDTO(); dto.setId(v.getId()); assertEquals(1,controller.fuelList(dto).getData().size()); assertNotEquals(200,controller.deleteVehicle(dto).getStatus());
        TenantContext.set(UUID.randomUUID().toString());
        assertTrue(controller.list().getData().isEmpty());
        assertNotEquals(200,controller.fuelList(dto).getStatus());
        dto.setId(r.getId()); assertNotEquals(200,controller.deleteFuel(dto).getStatus());
        TenantContext.set(home); dto.setId(v.getId()); assertEquals(1,controller.fuelList(dto).getData().size());
    }
    @Test void importIsPreviewedAndRepeatedImportSkipsExistingRows() throws Exception {
        TenantContext.set(UUID.randomUUID().toString()); AppUser actor=new AppUser(); actor.setId(UUID.randomUUID().toString()); UserContext.set(actor, "member");
        Vehicle v=new Vehicle(); v.setName("test"); v.setVehicleType("CAR"); v=controller.saveVehicle(v).getData();
        byte[] bytes;
        try (XSSFWorkbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet s=book.createSheet("费用记录");
            String[] header={"日期时间","费用类型名称","金额","备注"}; String[] data={"2026-01-01 12:30:00","保养","15.00","test"};
            s.createRow(0); s.createRow(1); for(int i=0;i<header.length;i++){s.getRow(0).createCell(i).setCellValue(header[i]); s.getRow(1).createCell(i).setCellValue(data[i]);} book.write(out); bytes=out.toByteArray();
        }
        MockMultipartFile file=new MockMultipartFile("file","test.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);
        Map<String,Object> preview=importer.preview(file,"费用记录",v.getId()).getData();
        IdDTO id=new IdDTO(); id.setId(v.getId()); assertTrue(controller.expenseList(id).getData().isEmpty());
        assertEquals(1,importer.confirm(file,"费用记录",v.getId(),(String)preview.get("digest")).getData().get("imported"));
        assertEquals(1,controller.expenseList(id).getData().size());
        Map<String,Object> repeat=importer.preview(file,"费用记录",v.getId()).getData();
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)repeat.get("rows");
        assertEquals(true,rows.get(0).get("duplicate"));
        assertEquals(0,importer.confirm(file,"费用记录",v.getId(),(String)repeat.get("digest")).getData().get("imported"));
        assertEquals(1,controller.expenseList(id).getData().size());
    }
    @Test void ledgerIsCreatedOnlyWhenRequestedAndPayerBelongsToFamily() {
        String home=UUID.randomUUID().toString(), uid=UUID.randomUUID().toString();
        TenantContext.set(home); AppUser actor=new AppUser(); actor.setId(uid); UserContext.set(actor,"member");
        db.update("insert into household_member (id,household_id,user_id,member_role,status) values (?,?,?,'member','normal')",UUID.randomUUID().toString(),home,uid);
        Vehicle v=new Vehicle(); v.setName("test"); v.setVehicleType("CAR"); v=controller.saveVehicle(v).getData();
        VehicleExpenseRecord r=new VehicleExpenseRecord(); r.setVehicleId(v.getId()); r.setOccurredAt(LocalDateTime.now()); r.setCategory("保养"); r.setAmount(new BigDecimal("80.00")); r.setIncludeLedger(true); r.setPayerId(UUID.randomUUID().toString());
        assertNotEquals(200,controller.saveExpense(r).getStatus()); IdDTO id=new IdDTO(); id.setId(v.getId()); assertTrue(controller.expenseList(id).getData().isEmpty());
        r.setPayerId(uid); assertEquals(200,controller.saveExpense(r).getStatus());
        assertEquals(1,db.queryForObject("select count(*) from household_ledger_entry where household_id=? and source_type='VEHICLE_EXPENSE' and source_id=?",Integer.class,home,r.getId()));
        IdDTO recordId=new IdDTO(); recordId.setId(r.getId()); assertEquals(200,controller.deleteExpense(recordId).getStatus());
        assertEquals(1,db.queryForObject("select count(*) from household_ledger_entry where household_id=? and source_id=?",Integer.class,home,r.getId()));
    }
}
