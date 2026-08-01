package com.osc.monitor.resource.controller;

import java.util.List;

import com.osc.monitor.resource.controller.dto.ChangesRequest;
import com.osc.monitor.resource.controller.dto.ChangesResponse;
import com.osc.monitor.resource.controller.dto.ChildrenResponse;
import com.osc.monitor.resource.controller.dto.ResourceResponse;
import com.osc.monitor.resource.controller.dto.SearchResponse;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.service.ResourceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 조회 전용 API. 쓰기는 데이터 생성기와 상태 시뮬레이터에서만 일어난다. */
@Validated
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/roots")
    public ChildrenResponse roots(@RequestParam(required = false) ResourceStatus status) {
        return resourceService.roots(status);
    }

    @GetMapping("/{id}/children")
    public ChildrenResponse children(@PathVariable long id,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) ResourceStatus status,
                                     @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return resourceService.children(id, cursor, status, size);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam @NotBlank String q,
                                 @RequestParam(required = false) ResourceType type,
                                 @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return resourceService.search(q, type, size);
    }

    /** 조회지만 POST 다. 펼친 부모가 100개를 넘으면 URL 길이 한계에 걸린다. */
    @PostMapping("/changes")
    public ChangesResponse changes(@Valid @RequestBody ChangesRequest request) {
        return resourceService.changes(request);
    }

    @GetMapping("/{id}/ancestors")
    public List<ResourceResponse> ancestors(@PathVariable long id) {
        return resourceService.ancestors(id);
    }
}
