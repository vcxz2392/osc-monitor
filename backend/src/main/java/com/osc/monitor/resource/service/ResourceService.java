package com.osc.monitor.resource.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osc.monitor.resource.controller.dto.ChangesRequest;
import com.osc.monitor.resource.controller.dto.ChangesResponse;
import com.osc.monitor.resource.controller.dto.ChildrenResponse;
import com.osc.monitor.resource.controller.dto.ResourceResponse;
import com.osc.monitor.resource.controller.dto.SearchResponse;
import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.domain.ResourcePath;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.repository.ResourceRepository;
import com.osc.monitor.resource.repository.entity.ResourceEntity;
import com.osc.monitor.revision.repository.RevisionRepository;
import com.osc.monitor.support.ApiException;
import com.osc.monitor.support.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceService {

    /** 한 응답에 담는 변경 상한. 넘으면 잘렸다고 알리고 클라이언트가 이어 받는다. */
    @Value("${app.changes.limit:500}")
    private int changesLimit;

    private final ResourceRepository resourceRepository;
    private final RevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public ChildrenResponse roots(ResourceStatus status) {
        long rev = revisionRepository.current();
        return new ChildrenResponse(toResponses(resourceRepository.findRoots(status)), null, rev);
    }

    public ChildrenResponse children(long parentId, String encodedCursor, ResourceStatus status, int size) {
        long rev = revisionRepository.current();
        var found = resourceRepository.findChildren(parentId, Cursor.decode(encodedCursor), status, size + 1);

        boolean hasMore = found.size() > size;
        var page = hasMore ? found.subList(0, size) : found;
        var nextCursor = hasMore ? Cursor.of(page.getLast()).encode() : null;

        return new ChildrenResponse(toResponses(page), nextCursor, rev);
    }

    /**
     * 화면에 이미 있는 것을 최신으로 유지하기 위한 증분. 아직 받지 않은 것은 조회가 담당한다.
     */
    public ChangesResponse changes(ChangesRequest request) {
        // 조회보다 먼저 읽는다. 뒤에 읽으면 그 사이 변경이 다음 요청에서 누락된다.
        var currentRev = revisionRepository.current();
        var found = resourceRepository.findChanges(request.since(), request.sinceId(),
                request.openParentIds(), request.includeRoots(), changesLimit + 1);

        var truncated = found.size() > changesLimit;
        var changed = truncated ? found.subList(0, changesLimit) : found;

        // 잘렸다면 마지막으로 실제 전달한 행을 커서로 준다.
        // 전역 리비전을 주면 그 사이 변경이 유실되고, 리비전만 주면 같은 리비전을 공유하는 나머지가 유실된다.
        var maxRev = truncated ? changed.getLast().getRev() : currentRev;
        var maxId = truncated ? changed.getLast().getId() : null;

        return new ChangesResponse(maxRev, maxId, toResponses(changed), truncated);
    }

    /**
     * 이름으로 찾는다. 결과가 계층 어디에 있는지는 <b>고르기 전에</b> 보여야 하므로
     * 조상 이름을 함께 내려보낸다.
     */
    public SearchResponse search(String name, ResourceType type, int size) {
        // 브라우저 입력에는 앞뒤 공백이 흔히 섞인다. @NotBlank 는 공백뿐인 값만 막는다.
        var found = resourceRepository.search(name.trim(), type, size + 1);
        var truncated = found.size() > size;
        var page = truncated ? found.subList(0, size) : found;

        // 결과 50건의 조상은 대부분 겹친다. 항목마다 경로 문자열을 붙이면 같은 값을 수십 번 실어 보내게 된다.
        Set<Long> ancestorIds = page.stream()
                .flatMap(entity -> ResourcePath.parseAncestorIds(entity.getPath()).stream())
                .collect(Collectors.toSet());
        Map<Long, String> ancestorNames = ancestorIds.isEmpty()
                ? Map.of()
                : resourceRepository.findAllById(ancestorIds).stream()
                        .collect(toMap(ResourceEntity::getId, ResourceEntity::getName));

        return new SearchResponse(toResponses(page), truncated, ancestorNames);
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
