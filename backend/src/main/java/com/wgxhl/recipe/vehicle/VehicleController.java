package com.wgxhl.recipe.vehicle;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.ledger.entity.LedgerEntry;
import com.wgxhl.recipe.ledger.service.LedgerService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vehicle")
public class VehicleController {
    private final VehicleMapper vehicles;
    private final VehicleFuelMapper fuel;
    private final VehicleExpenseMapper expenses;
    private final LedgerService ledger;
    private final VehicleExpenseTypes expenseTypes;
    public VehicleController(VehicleMapper vehicles, VehicleFuelMapper fuel, VehicleExpenseMapper expenses, LedgerService ledger, VehicleExpenseTypes expenseTypes) {
        this.vehicles = vehicles; this.fuel = fuel; this.expenses = expenses; this.ledger = ledger; this.expenseTypes = expenseTypes;
    }

    @PostMapping("/list")
    public ApiResponse<List<Vehicle>> list() {
        return ApiResponse.success(vehicles.selectList(new LambdaQueryWrapper<Vehicle>().orderByAsc(Vehicle::getCreateTime)));
    }

    @PostMapping("/save")
    public ApiResponse<Vehicle> saveVehicle(@RequestBody Vehicle input) {
        if (!text(input.getName(), 80) || !("CAR".equals(input.getVehicleType()) || "MOTORCYCLE".equals(input.getVehicleType())) || tooLong(input.getModel(), 120)) return ApiResponse.fail("请填写车辆名称和正确的车辆类型");
        String imagePath = input.getImagePath();
        if (StringUtils.hasText(imagePath) && !imagePath.matches("vehicle/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:jpg|png|webp)")) return ApiResponse.fail("请选择已上传的车辆图片");
        Vehicle existing = StringUtils.hasText(input.getId()) ? vehicles.selectById(input.getId()) : null;
        if (StringUtils.hasText(input.getId()) && existing == null) return ApiResponse.fail("车辆不存在");
        if (existing == null) { existing = new Vehicle(); existing.setId(IdUtil.simpleUUID()); existing.setCreatedBy(UserContext.requireUserId()); existing.setCreateTime(LocalDateTime.now()); }
        existing.setName(input.getName().trim()); existing.setVehicleType(input.getVehicleType()); existing.setModel(input.getModel()); existing.setOwnedSince(input.getOwnedSince()); existing.setUpdateTime(LocalDateTime.now());
        if (imagePath != null) existing.setImagePath(StringUtils.hasText(imagePath) ? imagePath : null);
        if (StringUtils.hasText(input.getId())) vehicles.updateById(existing); else vehicles.insert(existing);
        return ApiResponse.success(existing);
    }

    @PostMapping("/fuel/list")
    public ApiResponse<List<VehicleFuelRecord>> fuelList(@RequestBody IdDTO request) {
        if (vehicles.selectById(request.getId()) == null) return ApiResponse.fail("车辆不存在");
        return ApiResponse.success(fuel.selectList(new LambdaQueryWrapper<VehicleFuelRecord>().eq(VehicleFuelRecord::getVehicleId, request.getId()).orderByDesc(VehicleFuelRecord::getOccurredAt).orderByDesc(VehicleFuelRecord::getId)));
    }

    @PostMapping("/expense/list")
    public ApiResponse<List<VehicleExpenseRecord>> expenseList(@RequestBody IdDTO request) {
        if (vehicles.selectById(request.getId()) == null) return ApiResponse.fail("车辆不存在");
        return ApiResponse.success(expenses.selectList(new LambdaQueryWrapper<VehicleExpenseRecord>().eq(VehicleExpenseRecord::getVehicleId, request.getId()).orderByDesc(VehicleExpenseRecord::getOccurredAt).orderByDesc(VehicleExpenseRecord::getId)));
    }

    @PostMapping("/fuel/save")
    @Transactional
    public ApiResponse<VehicleFuelRecord> saveFuel(@RequestBody VehicleFuelRecord input) {
        if (!StringUtils.hasText(input.getVehicleId()) || lockVehicle(input.getVehicleId()) == null) return ApiResponse.fail("车辆不存在");
        if (!validFuel(input)) return ApiResponse.fail("请检查加油记录的时间、里程、油量和金额");
        VehicleFuelRecord old = StringUtils.hasText(input.getId()) ? fuel.selectById(input.getId()) : null;
        if (StringUtils.hasText(input.getId()) && (old == null || !old.getVehicleId().equals(input.getVehicleId()))) return ApiResponse.fail("记录不存在");
        if (old != null && Boolean.TRUE.equals(input.getIncludeLedger())) return ApiResponse.fail("已有记录请直接在家庭账本中记账");
        LedgerEntry bill = Boolean.TRUE.equals(input.getIncludeLedger()) ? prepareBill(input.getVehicleId(), "加油", input.getPaidAmount(), input.getOccurredAt(), input.getPayerId(), "VEHICLE_FUEL") : null;
        if (bill != null) { String error=ledger.validate(bill); if (error!=null) return ApiResponse.fail(error); }
        input.setImportFingerprint(old == null ? null : old.getImportFingerprint());
        input.setCreatedBy(old == null ? UserContext.requireUserId() : old.getCreatedBy()); input.setCreateTime(old == null ? LocalDateTime.now() : old.getCreateTime()); input.setUpdateTime(LocalDateTime.now());
        if (old == null) { input.setId(IdUtil.simpleUUID()); fuel.insert(input); } else fuel.updateById(input);
        if (bill != null) { bill.setSourceId(input.getId()); ledger.insertPrepared(bill); }
        return ApiResponse.success(input);
    }

    @PostMapping("/expense/save")
    @Transactional
    public ApiResponse<VehicleExpenseRecord> saveExpense(@RequestBody VehicleExpenseRecord input) {
        if (!StringUtils.hasText(input.getVehicleId()) || lockVehicle(input.getVehicleId()) == null) return ApiResponse.fail("车辆不存在");
        if (StringUtils.hasText(input.getCategoryKey())) {
            VehicleExpenseTypes.Type type = expenseTypes.enabled(input.getCategoryKey());
            if (type == null) return ApiResponse.fail("费用类型已停用或不存在，请重新选择");
            input.setCategory(type.getName());
        }
        if (!validExpense(input)) return ApiResponse.fail("请检查费用时间、类型和金额");
        VehicleExpenseRecord old = StringUtils.hasText(input.getId()) ? expenses.selectById(input.getId()) : null;
        if (StringUtils.hasText(input.getId()) && (old == null || !old.getVehicleId().equals(input.getVehicleId()))) return ApiResponse.fail("记录不存在");
        if (old != null && Boolean.TRUE.equals(input.getIncludeLedger())) return ApiResponse.fail("已有记录请直接在家庭账本中记账");
        LedgerEntry bill = Boolean.TRUE.equals(input.getIncludeLedger()) ? prepareBill(input.getVehicleId(), input.getCategory(), input.getAmount(), input.getOccurredAt(), input.getPayerId(), "VEHICLE_EXPENSE") : null;
        if (bill != null) { String error=ledger.validate(bill); if (error!=null) return ApiResponse.fail(error); }
        input.setImportFingerprint(old == null ? null : old.getImportFingerprint()); input.setCreatedBy(old == null ? UserContext.requireUserId() : old.getCreatedBy()); input.setCreateTime(old == null ? LocalDateTime.now() : old.getCreateTime()); input.setUpdateTime(LocalDateTime.now());
        if (old == null) { input.setId(IdUtil.simpleUUID()); expenses.insert(input); } else expenses.updateById(input);
        if (bill != null) { bill.setSourceId(input.getId()); ledger.insertPrepared(bill); }
        return ApiResponse.success(input);
    }

    @PostMapping("/fuel/delete") public ApiResponse<Void> deleteFuel(@RequestBody IdDTO dto) { return fuel.deleteById(dto.getId()) == 1 ? ApiResponse.success(null) : ApiResponse.fail("记录不存在"); }
    @PostMapping("/expense/delete") public ApiResponse<Void> deleteExpense(@RequestBody IdDTO dto) { return expenses.deleteById(dto.getId()) == 1 ? ApiResponse.success(null) : ApiResponse.fail("记录不存在"); }

    @PostMapping("/delete")
    @Transactional
    public ApiResponse<Void> deleteVehicle(@RequestBody IdDTO dto) {
        if (!StringUtils.hasText(dto.getId()) || lockVehicle(dto.getId()) == null) return ApiResponse.fail("车辆不存在");
        if (fuel.selectCount(new LambdaQueryWrapper<VehicleFuelRecord>().eq(VehicleFuelRecord::getVehicleId,dto.getId())) > 0 || expenses.selectCount(new LambdaQueryWrapper<VehicleExpenseRecord>().eq(VehicleExpenseRecord::getVehicleId,dto.getId())) > 0) return ApiResponse.fail("车辆仍有加油或费用记录，请先处理记录");
        vehicles.deleteById(dto.getId()); return ApiResponse.success(null);
    }

    @PostMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestBody IdDTO dto) {
        if (vehicles.selectById(dto.getId()) == null) return ApiResponse.fail("车辆不存在");
        List<VehicleFuelRecord> entries = fuel.selectList(new LambdaQueryWrapper<VehicleFuelRecord>().eq(VehicleFuelRecord::getVehicleId, dto.getId()).orderByAsc(VehicleFuelRecord::getOccurredAt).orderByAsc(VehicleFuelRecord::getId));
        List<FuelConsumptionCalculator.Reading> readings = new ArrayList<>();
        Map<Integer, List<FuelConsumptionCalculator.Reading>> readingsByYear = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (VehicleFuelRecord r : entries) {
            FuelConsumptionCalculator.Reading reading = new FuelConsumptionCalculator.Reading(r.getMileage(), r.getLiters(),
                    Boolean.TRUE.equals(r.getFullTank()), Boolean.TRUE.equals(r.getWarningLight()), Boolean.TRUE.equals(r.getMissedPrevious()));
            readings.add(reading);
            readingsByYear.computeIfAbsent(r.getOccurredAt().getYear(), year -> new ArrayList<>()).add(reading);
            total = total.add(r.getPaidAmount());
        }
        FuelConsumptionCalculator.Result consumption = FuelConsumptionCalculator.calculate(readings);
        Map<Integer, BigDecimal> fuelYearAverages = new LinkedHashMap<>();
        readingsByYear.forEach((year, yearlyReadings) -> fuelYearAverages.put(year,
                readingsByYear.size() == 1 ? consumption.getAverage() : FuelConsumptionCalculator.calculate(yearlyReadings).getAverage()));
        List<Map<String,Object>> trend = new ArrayList<>(); BigDecimal latest = null;
        for (int i=0;i<entries.size();i++) {
            FuelConsumptionCalculator.Estimate estimate=consumption.getEstimates().get(i);
            if (estimate.getValue()!=null) { Map<String,Object> point=new LinkedHashMap<>(); point.put("recordId",entries.get(i).getId()); point.put("date",entries.get(i).getOccurredAt().toLocalDate()); point.put("value",estimate.getValue()); point.put("method",estimate.getMethod()); trend.add(point); latest=estimate.getValue(); }
        }
        List<VehicleExpenseRecord> costs=expenses.selectList(new LambdaQueryWrapper<VehicleExpenseRecord>().eq(VehicleExpenseRecord::getVehicleId,dto.getId()));
        BigDecimal expenseTotal=BigDecimal.ZERO; Map<String,BigDecimal> categories=new LinkedHashMap<>();
        for (VehicleExpenseRecord cost:costs) { expenseTotal=expenseTotal.add(cost.getAmount()); categories.put(cost.getCategory(),categories.getOrDefault(cost.getCategory(),BigDecimal.ZERO).add(cost.getAmount())); }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("average", consumption.getAverage()); result.put("averageMethod", consumption.getAverageMethod()); result.put("latest",latest); result.put("trend",trend); result.put("fuelTotal", total); result.put("fuelCount", entries.size()); result.put("expenseTotal",expenseTotal); result.put("expenseCategories",categories);
        result.put("fuelYearAverages", fuelYearAverages);
        return ApiResponse.success(result);
    }

    private static boolean text(String s, int max) { return StringUtils.hasText(s) && s.trim().length() <= max; }
    private Vehicle lockVehicle(String id) { return vehicles.selectOne(new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getId,id).last("FOR UPDATE")); }
    private LedgerEntry prepareBill(String vehicleId, String description, BigDecimal amount, LocalDateTime occurredAt, String payerId, String source) {
        Vehicle vehicle = vehicles.selectById(vehicleId);
        LedgerEntry entry = new LedgerEntry(); entry.setId(IdUtil.simpleUUID()); entry.setEntryType("EXPENSE"); entry.setCategory("交通"); entry.setDescription(vehicle.getName() + " · " + description); entry.setAmount(amount); entry.setOccurredOn(occurredAt.toLocalDate()); entry.setPayerId(payerId); entry.setSourceType(source); entry.setSourceCycle("once"); entry.setCreatedBy(UserContext.requireUserId()); entry.setCreateTime(LocalDateTime.now()); entry.setUpdateTime(entry.getCreateTime()); return entry;
    }
    static boolean validFuel(VehicleFuelRecord input) { return input.getOccurredAt() != null && number(input.getMileage(), false, 10, 2) && number(input.getLiters(), true, 9, 3) && number(input.getPaidAmount(), false, 10, 2) && optionalNumber(input.getUnitPrice(), 8, 4) && optionalNumber(input.getDisplayedAmount(), 10, 2) && input.getFullTank() != null && input.getWarningLight() != null && input.getMissedPrevious() != null && !tooLong(input.getFuelGrade(), 32) && !tooLong(input.getStation(), 160) && !tooLong(input.getRemark(), 1000); }
    static boolean validExpense(VehicleExpenseRecord input) { return input.getOccurredAt() != null && text(input.getCategory(), 80) && number(input.getAmount(), true, 10, 2) && !tooLong(input.getRemark(), 1000); }
    private static boolean tooLong(String s, int max) { return s != null && s.length() > max; }
    private static boolean number(BigDecimal n, boolean positive, int integerDigits, int decimals) { return n != null && (positive ? n.signum() > 0 : n.signum() >= 0) && n.scale() <= decimals && n.precision() - n.scale() <= integerDigits; }
    private static boolean optionalNumber(BigDecimal n, int integerDigits, int decimals) { return n == null || number(n, false, integerDigits, decimals); }
}
