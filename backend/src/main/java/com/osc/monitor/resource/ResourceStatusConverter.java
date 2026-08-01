package com.osc.monitor.resource;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResourceStatusConverter implements AttributeConverter<ResourceStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ResourceStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ResourceStatus convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : ResourceStatus.of(dbData);
    }
}
