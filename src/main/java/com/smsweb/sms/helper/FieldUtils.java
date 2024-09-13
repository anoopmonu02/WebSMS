package com.smsweb.sms.helper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class FieldUtils {
    public static Map<String, String> getFieldLabels(Class<?> clazz) {
        Map<String, String> fieldLabels = new HashMap<>();

        for (Field field : clazz.getDeclaredFields()) {
            // Exclude fields you don't want to show
            if (field.getName().equals("sensitiveInfo")) {
                continue;
            }

            // Check if the field has a DisplayLabel annotation
            DisplayLabel label = field.getAnnotation(DisplayLabel.class);
            if (label != null) {
                fieldLabels.put(field.getName(), label.value());
            } else {
                // If no label is provided, use the field name as default
                fieldLabels.put(field.getName(), field.getName());
            }
        }
        return fieldLabels;
    }
}
