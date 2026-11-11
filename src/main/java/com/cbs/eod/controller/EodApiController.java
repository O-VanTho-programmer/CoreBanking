package com.cbs.eod.controller;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.eod.dto.EodExecutionReport;
import com.cbs.eod.dto.EodStatusResponse;
import com.cbs.eod.dto.TrialBalanceReport;
import com.cbs.eod.service.EodJobLauncherService;
import com.cbs.eod.service.TrialBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/eod")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EodApiController {

    private final WorkingDateService workingDateService;
    private final EodJobLauncherService eodJobLauncherService;
    private final TrialBalanceService trialBalanceService;

    @GetMapping("/status")
    public ResponseEntity<EodStatusResponse> getEodStatus() {
        LocalDate current = workingDateService.getCurrentWorkingDate();
        LocalDate next = current.plusDays(1);
        boolean isCutoff = workingDateService.isSystemCutoff();
        return ResponseEntity.ok(new EodStatusResponse(current, next, isCutoff));
    }

    @PostMapping("/run")
    public ResponseEntity<EodExecutionReport> runEod() throws Exception {
        log.info("EOD Run triggered via REST API");
        EodExecutionReport report = eodJobLauncherService.runEodPipeline();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceReport> getTrialBalance(@RequestParam(required = false) String date) {
        LocalDate workingDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : workingDateService.getCurrentWorkingDate();

        TrialBalanceReport report = trialBalanceService.verifyTrialBalance(workingDate);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/cutoff")
    public ResponseEntity<EodStatusResponse> setCutoff(
            @RequestParam(name = "cutoff", required = false) Boolean cutoff,
            @RequestParam(name = "closed", required = false) Boolean closed) {
        boolean effectiveCutoff = cutoff != null ? cutoff : (closed != null ? closed : true);
        workingDateService.setCutoff(effectiveCutoff);
        return getEodStatus();
    }
}
