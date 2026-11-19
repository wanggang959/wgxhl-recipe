package com.wgxhl.recipe.vehicle;

import com.wgxhl.recipe.common.ApiResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VehicleImportControllerTest {
    @Test void previewsExportAndMarksDuplicateWithoutWriting() throws Exception {
        VehicleMapper vehicles = mock(VehicleMapper.class);
        VehicleFuelMapper fuels = mock(VehicleFuelMapper.class);
        VehicleExpenseMapper expenses = mock(VehicleExpenseMapper.class);
        VehicleController validator = mock(VehicleController.class);
        when(vehicles.selectById("v1")).thenReturn(new Vehicle());
        when(fuels.selectCount(any())).thenReturn(0L);
        VehicleImportController controller = new VehicleImportController(vehicles, fuels, expenses, validator);
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("车-油耗记录");
            String[] headers = {"日期时间", "总里程", "机显单价", "加油量", "机显金额", "实付金额", "油号", "加满", "亮灯", "漏记", "油耗", "加油站名称", "备注"};
            String[] values = {"2026-01-01 08:00:00", "1000", "8.20", "10.000", "82.00", "80.00", "92#", "是", "否", "否", "6.00", "", ""};
            sheet.createRow(0);
            for (int i=0;i<headers.length;i++) sheet.getRow(0).createCell(i).setCellValue(headers[i]);
            for (int j=1;j<=2;j++) for (int i=0;i<values.length;i++) { if (i==0) sheet.createRow(j); sheet.getRow(j).createCell(i).setCellValue(values[i]); }
            workbook.write(out); bytes=out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        ApiResponse<Map<String,Object>> response = controller.preview(file, "车-油耗记录", "v1");
        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)response.getData().get("rows");
        assertEquals(2, rows.size()); assertNull(rows.get(0).get("error")); assertEquals(false, rows.get(0).get("duplicate")); assertEquals(true, rows.get(1).get("duplicate"));
        verify(validator, never()).saveFuel(any());
    }
    @Test void rejectsUnknownVehicle() {
        VehicleMapper vehicles = mock(VehicleMapper.class);
        VehicleImportController controller = new VehicleImportController(vehicles, mock(VehicleFuelMapper.class), mock(VehicleExpenseMapper.class), mock(VehicleController.class));
        assertNotEquals(200, controller.preview(new MockMultipartFile("file", new byte[]{1}), "sheet", "other").getStatus());
    }
    @Test void optionallyChecksRealExportReadOnly() throws Exception {
        String path = System.getProperty("vehicle.sample.path");
        assumeTrue(path != null && !path.isEmpty());
        VehicleMapper vehicles = mock(VehicleMapper.class);
        VehicleFuelMapper fuels = mock(VehicleFuelMapper.class);
        VehicleExpenseMapper expenses = mock(VehicleExpenseMapper.class);
        VehicleController validator = mock(VehicleController.class);
        when(vehicles.selectById("v1")).thenReturn(new Vehicle());
        when(fuels.selectCount(any())).thenReturn(0L);
        when(expenses.selectCount(any())).thenReturn(0L);
        VehicleImportController controller = new VehicleImportController(vehicles, fuels, expenses, validator);
        MockMultipartFile file = new MockMultipartFile("file", "sample.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(Paths.get(path)));
        ApiResponse<Map<String,Object>> index = controller.preview(file, null, null);
        assertEquals(200, index.getStatus(), index.getMessage());
        @SuppressWarnings("unchecked") List<String> sheets = (List<String>) index.getData().get("sheets");
        for (String sheet : sheets) {
            ApiResponse<Map<String,Object>> preview = controller.preview(file, sheet, "v1");
            assertEquals(200, preview.getStatus(), preview.getMessage());
            @SuppressWarnings("unchecked") List<Map<String,Object>> rows = (List<Map<String,Object>>) preview.getData().get("rows");
            assertFalse(rows.isEmpty());
            long errors = rows.stream().filter(row -> row.get("error") != null).count();
            assertEquals(0, errors, sheet + " has invalid rows");
        }
        verify(validator, never()).saveFuel(any()); verify(validator, never()).saveExpense(any());
    }
    @Test void optionallyComparesExportedFuelValues() throws Exception {
        String path=System.getProperty("vehicle.sample.path"); assumeTrue(path!=null && !path.isEmpty());
        try (XSSFWorkbook book=new XSSFWorkbook(Files.newInputStream(Paths.get(path)))) {
            for (org.apache.poi.ss.usermodel.Sheet sheet:book) {
                if(!sheet.getSheetName().endsWith("油耗记录")) continue;
                java.util.List<org.apache.poi.ss.usermodel.Row> rows=new java.util.ArrayList<>();
                for(int i=1;i<=sheet.getLastRowNum();i++) if(sheet.getRow(i)!=null) rows.add(sheet.getRow(i));
                java.util.Collections.reverse(rows);
                java.util.List<FuelConsumptionCalculator.Reading> readings=new java.util.ArrayList<>();
                org.apache.poi.ss.usermodel.DataFormatter fmt=new org.apache.poi.ss.usermodel.DataFormatter();
                for(org.apache.poi.ss.usermodel.Row row:rows) readings.add(new FuelConsumptionCalculator.Reading(new java.math.BigDecimal(fmt.formatCellValue(row.getCell(1))),new java.math.BigDecimal(fmt.formatCellValue(row.getCell(3))),"是".equals(fmt.formatCellValue(row.getCell(7))),"是".equals(fmt.formatCellValue(row.getCell(8))),"是".equals(fmt.formatCellValue(row.getCell(9)))));
                FuelConsumptionCalculator.Result result=FuelConsumptionCalculator.calculate(readings); int comparable=0, matched=0;
                java.util.Map<String,Integer> mismatches=new java.util.TreeMap<>();
                for(int i=0;i<rows.size();i++) { String expected=fmt.formatCellValue(rows.get(i).getCell(10)).trim(); try { java.math.BigDecimal number=new java.math.BigDecimal(expected), actual=result.getEstimates().get(i).getValue(); if(actual!=null){comparable++; if(number.subtract(actual).abs().compareTo(new java.math.BigDecimal("0.02"))<=0)matched++; else mismatches.merge(result.getEstimates().get(i).getMethod(),1,Integer::sum);} } catch(NumberFormatException ignored) { } }
                System.out.println("Fuel export comparison: " + matched + "/" + comparable);
                System.out.println("Mismatch types: " + mismatches);
                assertTrue(comparable > 0); assertEquals(comparable, matched, "Exported fuel values differ");
            }
        }
    }
}
