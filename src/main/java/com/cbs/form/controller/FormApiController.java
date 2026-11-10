package com.cbs.form.controller;

import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepDispatcher;
import com.cbs.form.domain.FormEntity;
import com.cbs.form.dto.FormEvaluateRequest;
import com.cbs.form.dto.FormEvaluateResponse;
import com.cbs.form.dto.FormShortcutResponse;
import com.cbs.form.dto.FormSummaryResponse;
import com.cbs.form.repository.FormRepository;
import com.cbs.form.repository.FormShortcutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FormApiController {

    private final FormRepository formRepository;
    private final FormShortcutRepository formShortcutRepository;
    private final FormStepDispatcher formStepDispatcher;

    @GetMapping("/shortcuts")
    public ResponseEntity<List<FormShortcutResponse>> listShortcuts(@RequestParam(required = false) String module) {
        var shortcuts = (module != null && !module.isBlank())
                ? formShortcutRepository.findByModuleAndIsActiveOrderByDisplayOrderAsc(module, "Y")
                : formShortcutRepository.findByIsActiveOrderByDisplayOrderAsc("Y");

        return ResponseEntity.ok(shortcuts.stream().map(FormShortcutResponse::fromEntity).toList());
    }

    @GetMapping("/shortcuts/search")
    public ResponseEntity<List<FormShortcutResponse>> searchShortcuts(@RequestParam(defaultValue = "") String q) {
        var results = formShortcutRepository.searchShortcuts(q.trim());
        return ResponseEntity.ok(results.stream().map(FormShortcutResponse::fromEntity).toList());
    }

    @GetMapping
    public ResponseEntity<List<FormSummaryResponse>> listForms(@RequestParam(required = false) String module) {
        List<FormEntity> forms = (module != null && !module.isBlank())
                ? formRepository.findByModuleAndIsActiveOrderByFormCodeAsc(module, "Y")
                : formRepository.findByIsActiveOrderByFormCodeAsc("Y");

        List<FormSummaryResponse> responses = forms.stream()
                .map(f -> new FormSummaryResponse(f.getFormCode(), f.getTrxCode(), f.getModule(), f.getFormTitle(), f.getVersion()))
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping(value = "/{formCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFormSchema(@PathVariable String formCode) {
        return formRepository.findByFormCodeAndIsActive(formCode, "Y")
                .map(f -> ResponseEntity.ok(f.getSchemaPayload()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/evaluate")
    public ResponseEntity<FormEvaluateResponse> evaluateForm(@RequestBody FormEvaluateRequest request) {
        log.info("Evaluating form [formCode='{}', triggerField='{}', steps={}]",
                request.formCode(), request.triggerField(), request.stepCodes());

        FormEvaluationContext context = formStepDispatcher.dispatch(
                request.formCode(),
                request.triggerField(),
                request.stepCodes(),
                request.formData());

        FormEvaluateResponse response = new FormEvaluateResponse(
                context.getUpdates(),
                context.getErrors(),
                context.getDisabledFields(),
                context.getHiddenFields(),
                context.getFieldOptions());

        return ResponseEntity.ok(response);
    }
}
