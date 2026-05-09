// CheckAccess.java
package com.smsweb.sms.config.permission;

import com.smsweb.sms.models.permission.AccessType;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckAccess {
    String screen();
    AccessType type();
}