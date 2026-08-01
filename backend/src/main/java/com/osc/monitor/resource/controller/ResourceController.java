package com.osc.monitor.resource.controller;

import java.util.List;

import com.osc.monitor.resource.controller.dto.ChildrenResponse;
import com.osc.monitor.resource.controller.dto.ResourceResponse;
import com.osc.monitor.resource.service.ResourceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 조회 전용 API. 쓰기는 데이터 생성기에서만 일어난다. */
@Validated
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/roots")
    public ChildrenResponse roots() {
        return resourceService.roots();
    }

    @GetMapping("/{id}/children")
    public ChildrenResponse children(@PathVariable long id,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return resourceService.children(id, cursor, size);
    }

    @GetMapping("/{id}/ancestors")
    public List<ResourceResponse> ancestors(@PathVariable long id) {
        return resourceService.ancestors(id);
    }
}
