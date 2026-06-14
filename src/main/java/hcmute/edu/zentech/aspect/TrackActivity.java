package hcmute.edu.zentech.aspect;

import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackActivity {
    ActivityAction action();
    ActivityAction failureAction() default ActivityAction.ACCESS_DENIED;
    ActivityArea area() default ActivityArea.SYSTEM;
    ActivitySeverity severity() default ActivitySeverity.INFO;
    String module() default "";
    String targetType() default "";
    String summary() default "";
    boolean logOnFailure() default false;
}
