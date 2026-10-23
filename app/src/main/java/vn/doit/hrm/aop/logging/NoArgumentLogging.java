package vn.doit.hrm.aop.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@link LoggingAspect} không ghi giá trị tham số của lớp/hàm này (vd. tọa độ, IP của lần tự chấm công). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface NoArgumentLogging {}
