package com.osc.monitor.resource.service;

import java.util.List;
import java.util.Objects;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osc.monitor.resource.controller.dto.ChildrenResponse;
import com.osc.monitor.resource.controller.dto.ResourceResponse;
import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.domain.ResourcePath;
import com.osc.monitor.resource.repository.ResourceRepository;
import com.osc.monitor.resource.repository.entity.ResourceEntity;
import com.osc.monitor.revision.repository.RevisionRepository;
import com.osc.monitor.support.ApiException;
import com.osc.monitor.support.ErrorCode;
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

    /**
     * 검색 결과에서 그 위치까지 트리를 펼치기 위한 조상 목록.
     * 부모를 거슬러 올라가지 않고 경로를 파싱해 PK 조회 한 번으로 끝낸다.
     */
    public List<ResourceResponse> ancestors(long id) {
        var resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "없는 리소스입니다: " + id));

        var ancestorIds = ResourcePath.parseAncestorIds(resource.getPath());
        if (ancestorIds.isEmpty()) {
            return List.of();
        }

        // findAllById 는 순서를 보장하지 않아 경로에 적힌 순서(루트 → 부모)로 다시 세운다.
        var byId = resourceRepository.findAllById(ancestorIds).stream()
                .collect(toMap(ResourceEntity::getId, identity()));
        if (byId.size() != ancestorIds.size()) {
            // 경로에 있는데 행이 없다 = 트리가 깨진 것이다. 조용히 짧은 경로를 돌려주면 아무도 모른다.
            log.warn("경로의 조상 중 조회되지 않은 것이 있다. id={}, path={}", id, resource.getPath());
        }
        return toResponses(ancestorIds.stream().map(byId::get).filter(Objects::nonNull).toList());
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
