package vn.doit.hr.leave;

import java.math.BigDecimal;

/** Một dòng sổ phép (số dương cộng, số âm trừ). */
public record Entry(Pool pool, Bucket bucket, BigDecimal amount) {}
