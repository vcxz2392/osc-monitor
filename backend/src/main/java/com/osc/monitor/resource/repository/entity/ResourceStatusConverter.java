package com.osc.monitor.resource.repository.entity;

import com.osc.monitor.resource.domain.ResourceStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResourceStatusConverter implements AttributeConverter<ResourceStatus, Byte> {

    @Override
    public Byte convertToDatabaseColumn(ResourceStatus attribute) {
        return attribute == null ? null : (byte) attribute.code();
    }

    @Override
    public ResourceStatus convertToEntityAttribute(Byte dbData) {
        return dbData == null ? null : ResourceStatus.of(dbData);
    }
}
