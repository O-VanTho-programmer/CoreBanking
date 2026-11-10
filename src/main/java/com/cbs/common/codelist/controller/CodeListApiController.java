package com.cbs.common.codelist.controller;

import com.cbs.common.codelist.dto.CodeListValueDto;
import com.cbs.common.codelist.repository.CodeListValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/codelists")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CodeListApiController {

    private final CodeListValueRepository codeListValueRepository;

    @GetMapping("/{codeGroup}")
    public ResponseEntity<List<CodeListValueDto>> getCodeListValues(@PathVariable String codeGroup) {
        log.debug("Fetching active codelist values for group: {}", codeGroup);
        List<CodeListValueDto> values = codeListValueRepository.findActiveEntitiesByCodeGroup(codeGroup).stream()
                .map(entity -> new CodeListValueDto(
                        entity.getId().getCodeGroup(),
                        entity.getId().getCodeValue(),
                        entity.getDisplayNameVi(),
                        entity.getDisplayNameEn(),
                        entity.getSortOrder()
                ))
                .toList();
        return ResponseEntity.ok(values);
    }
}
