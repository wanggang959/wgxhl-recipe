package com.wgxhl.recipe.vehicle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wgxhl.recipe.common.ApiResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/vehicle/import")
public class VehicleImportController {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final VehicleMapper vehicles;
    private final VehicleFuelMapper fuels;
    private final VehicleExpenseMapper expenses;
    private final VehicleController validator;

    public VehicleImportController(VehicleMapper vehicles, VehicleFuelMapper fuels, VehicleExpenseMapper expenses, VehicleController validator) {
        this.vehicles = vehicles; this.fuels = fuels; this.expenses = expenses; this.validator = validator;
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestParam("file") MultipartFile file, @RequestParam(value="sheet", required=false) String sheet, @RequestParam(value="vehicleId", required=false) String vehicleId) {
        try { return ApiResponse.success(parse(file, sheet, vehicleId)); }
        catch (Exception ex) { return ApiResponse.fail("导入预览失败：" + safeMessage(ex)); }
    }

    @Transactional
    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(@RequestParam("file") MultipartFile file, @RequestParam("sheet") String sheet, @RequestParam("vehicleId") String vehicleId, @RequestParam("digest") String digest) {
        try {
            Map<String,Object> preview = parse(file, sheet, vehicleId);
            if (!Objects.equals(digest, preview.get("digest"))) return ApiResponse.fail("文件与预览时不同，请重新预览");
            @SuppressWarnings("unchecked") List<Map<String,Object>> rows = (List<Map<String,Object>>) preview.get("rows");
            if (rows == null) return ApiResponse.fail("请选择工作表和车辆");
            for (Map<String,Object> row : rows) if (row.get("error") != null) return ApiResponse.fail("表格存在错误，请处理后重新预览");
            int count = 0;
            for (Map<String,Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("duplicate"))) continue;
                String fp = (String) row.get("fingerprint");
                if (row.get("record") instanceof VehicleFuelRecord) {
                    VehicleFuelRecord item = (VehicleFuelRecord)row.get("record");
                    ApiResponse<VehicleFuelRecord> saved = validator.saveFuel(item);
                    if (saved.getStatus() != 200) throw new IllegalStateException(saved.getMessage());
                    VehicleFuelRecord patch = new VehicleFuelRecord(); patch.setId(saved.getData().getId()); patch.setImportFingerprint(fp); fuels.updateById(patch);
                } else {
                    VehicleExpenseRecord item = (VehicleExpenseRecord)row.get("record");
                    ApiResponse<VehicleExpenseRecord> saved = validator.saveExpense(item);
                    if (saved.getStatus() != 200) throw new IllegalStateException(saved.getMessage());
                    VehicleExpenseRecord patch = new VehicleExpenseRecord(); patch.setId(saved.getData().getId()); patch.setImportFingerprint(fp); expenses.updateById(patch);
                }
                count++;
            }
            Map<String,Object> result = new LinkedHashMap<>(); result.put("imported", count); return ApiResponse.success(result);
        } catch (Exception ex) { throw new IllegalStateException("导入未完成，所有变更已回滚：" + safeMessage(ex), ex); }
    }

    private Map<String,Object> parse(MultipartFile file, String sheetName, String vehicleId) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw new IllegalArgumentException("请选择不超过 5 MB 的表格");
        byte[] bytes = file.getBytes();
        Map<String,Object> result = new LinkedHashMap<>(); result.put("digest", sha(bytes));
        ZipSecureFile.setMinInflateRatio(0.01);
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() > 40) throw new IllegalArgumentException("工作表过多");
            List<String> names = new ArrayList<>(); for (Sheet s : book) names.add(s.getSheetName()); result.put("sheets", names);
            if (sheetName == null || sheetName.trim().isEmpty()) return result;
            if (vehicleId == null || vehicles.selectById(vehicleId) == null) throw new IllegalArgumentException("请选择当前家庭的车辆");
            Sheet sheet = book.getSheet(sheetName); if (sheet == null || sheet.getLastRowNum() > 2000) throw new IllegalArgumentException("工作表不存在或记录超过两千条");
            Row head = sheet.getRow(0); if (head == null) throw new IllegalArgumentException("缺少表头");
            Map<String,Integer> columns = new HashMap<>(); for (Cell cell : head) columns.put(cell(cell), cell.getColumnIndex());
            boolean fuel = columns.containsKey("总里程") && columns.containsKey("加油量");
            boolean expense = columns.containsKey("费用类型名称") && columns.containsKey("金额");
            if (!columns.containsKey("日期时间") || (!fuel && !expense)) throw new IllegalArgumentException("不是支持的小熊油耗导出格式");
            String[] required = fuel ? new String[]{"总里程","机显单价","加油量","机显金额","实付金额","加满","亮灯","漏记"} : new String[]{"费用类型名称","金额"};
            for (String key : required) if (!columns.containsKey(key)) throw new IllegalArgumentException("缺少列：" + key);
            List<Map<String,Object>> rows = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); if (row == null) continue;
                boolean blank = true; for (Cell cell : row) if (!cell(cell).isEmpty()) { blank = false; break; } if (blank) continue;
                Map<String,Object> out = new LinkedHashMap<>(); out.put("line", i + 1);
                try {
                    LocalDateTime at = LocalDateTime.parse(value(row, columns, "日期时间"), DATE);
                    StringBuilder canonical = new StringBuilder(fuel ? "FUEL|" : "EXPENSE|");
                    List<String> keys = new ArrayList<>(columns.keySet()); Collections.sort(keys);
                    for (String key : keys) if (!"油耗".equals(key)) canonical.append(key.length()).append(':').append(key).append(value(row, columns, key).length()).append(':').append(value(row, columns, key));
                    String fingerprint = sha(canonical.toString().getBytes(StandardCharsets.UTF_8));
                    boolean duplicate = !seen.add(fingerprint);
                    if (fuel) {
                        VehicleFuelRecord item = new VehicleFuelRecord(); item.setVehicleId(vehicleId); item.setOccurredAt(at); item.setMileage(decimal(row,columns,"总里程")); item.setUnitPrice(decimal(row,columns,"机显单价")); item.setLiters(decimal(row,columns,"加油量")); item.setDisplayedAmount(decimal(row,columns,"机显金额")); item.setPaidAmount(decimal(row,columns,"实付金额")); item.setFuelGrade(value(row,columns,"油号")); item.setFullTank(flag(row,columns,"加满")); item.setWarningLight(flag(row,columns,"亮灯")); item.setMissedPrevious(flag(row,columns,"漏记")); item.setStation(value(row,columns,"加油站名称")); item.setRemark(value(row,columns,"备注"));
                        if (!VehicleController.validFuel(item)) throw new IllegalArgumentException("请检查里程、油量、金额和文本长度");
                        duplicate |= fuels.selectCount(new LambdaQueryWrapper<VehicleFuelRecord>().eq(VehicleFuelRecord::getVehicleId,vehicleId).eq(VehicleFuelRecord::getImportFingerprint,fingerprint)) > 0;
                        out.put("record", item);
                    } else {
                        VehicleExpenseRecord item = new VehicleExpenseRecord(); item.setVehicleId(vehicleId); item.setOccurredAt(at); item.setCategory(value(row,columns,"费用类型名称")); item.setAmount(decimal(row,columns,"金额")); item.setRemark(value(row,columns,"备注")); if (!VehicleController.validExpense(item)) throw new IllegalArgumentException("费用类型或金额不合法");
                        duplicate |= expenses.selectCount(new LambdaQueryWrapper<VehicleExpenseRecord>().eq(VehicleExpenseRecord::getVehicleId,vehicleId).eq(VehicleExpenseRecord::getImportFingerprint,fingerprint)) > 0;
                        out.put("record", item);
                    }
                    out.put("fingerprint", fingerprint); out.put("duplicate", duplicate);
                } catch (Exception ex) { out.put("error", safeMessage(ex)); }
                rows.add(out);
            }
            result.put("type", fuel ? "fuel" : "expense"); result.put("rows", rows); return result;
        }
    }
    private static String value(Row row, Map<String,Integer> columns, String key) { Integer i = columns.get(key); return i == null ? "" : cell(row.getCell(i)); }
    private static String cell(Cell c) { if (c == null) return ""; if (c.getCellType() == CellType.FORMULA || c.getCellType() == CellType.ERROR) throw new IllegalArgumentException("不支持公式或错误单元格"); return new DataFormatter(Locale.ROOT).formatCellValue(c).trim(); }
    private static BigDecimal decimal(Row r, Map<String,Integer> c, String k) { try { return new BigDecimal(value(r,c,k)); } catch (Exception ex) { throw new IllegalArgumentException(k + "不是有效数字"); } }
    private static boolean flag(Row r, Map<String,Integer> c, String k) { String v=value(r,c,k); if ("是".equals(v) || "1".equals(v)) return true; if ("否".equals(v) || "0".equals(v)) return false; throw new IllegalArgumentException(k + "只能是是或否"); }
    private static String sha(byte[] bytes) throws Exception { byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder s=new StringBuilder(); for(byte b:hash)s.append(String.format("%02x",b & 255)); return s.toString(); }
    private static String safeMessage(Exception ex) { return ex instanceof IllegalArgumentException || ex instanceof IllegalStateException ? String.valueOf(ex.getMessage()) : "文件格式或数据不正确"; }
}
