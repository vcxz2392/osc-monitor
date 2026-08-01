package com.osc.monitor.resource.repository.entity;

import com.osc.monitor.resource.domain.ResourceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResourceTypeConverter implements AttributeConverter<ResourceType, Byte> {

    @Override
    public Byte convertToDatabaseColumn(ResourceType attribute) {
        return attribute == null ? null : (byte) attribute.code();
    }

    @Override
    public ResourceType convertToEntityAttribute(Byte dbData) {
        return dbData == null ? null : ResourceType.of(dbData);
    }
}
