package com.osc.monitor.resource;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResourceTypeConverter implements AttributeConverter<ResourceType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ResourceType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ResourceType convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : ResourceType.of(dbData);
    }
}
