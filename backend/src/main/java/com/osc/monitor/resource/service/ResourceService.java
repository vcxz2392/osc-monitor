package com.osc.monitor.resource.service;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osc.monitor.resource.controller.dto.ChildrenResponse;
import com.osc.monitor.resource.controller.dto.ResourceResponse;
import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.repository.ResourceRepository;
import com.osc.monitor.resource.repository.entity.ResourceEntity;
import com.osc.monitor.revision.repository.RevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final RevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public ChildrenResponse roots() {
        long rev = revisionRepository.current();
        return new ChildrenResponse(toResponses(resourceRepository.findRoots()), null, rev);
    }

    public ChildrenResponse children(long parentId, String encodedCursor, int size) {
        long rev = revisionRepository.current();
        var found = resourceRepository.findChildren(parentId, Cursor.decode(encodedCursor), size + 1);

        boolean hasMore = found.size() > size;
        var page = hasMore ? found.subList(0, size) : found;
        var nextCursor = hasMore ? Cursor.of(page.getLast()).encode() : null;

        return new ChildrenResponse(toResponses(page), nextCursor, rev);
    }

    private List<ResourceResponse> toResponses(List<ResourceEntity> entities) {
        return entities.stream()
                .map(entity -> ResourceResponse.of(entity, readMetrics(entity.getMetricsJson())))
                .toList();
    }

    private JsonNode readMetrics(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            // 조용히 삼키면 화면에는 "지표 없음"으로 보여 데이터 손상이 숨는다.
            log.warn("지표 JSON 파싱 실패, 지표 없이 응답한다: {}", json, e);
            return null;
        }
    }
}
