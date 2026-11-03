package com.cbs.eod.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.eod.dto.EodExecutionReport;
import com.cbs.eod.dto.TrialBalanceReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Service to launch and monitor the End-of-Day (EOD) Spring Batch Pipeline.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EodJobLauncherService {

    private final JobLauncher jobLauncher;
    private final Job eodJob;
    private final WorkingDateService workingDateService;
    private final TrialBalanceService trialBalanceService;

    /**
     * Programmatically runs the complete 6-step EOD Batch Pipeline for the current operational working date.
     *
     * @return EodExecutionReport detailing execution status, duration, and trial balance results
     * @throws Exception if batch launch or execution fails
     */
    public EodExecutionReport runEodPipeline() throws Exception {
        LocalDate currentDate = workingDateService.getCurrentWorkingDate();
        log.info("Launching Spring Batch EOD Pipeline for operational working date: {}", currentDate);

        long startTime = System.currentTimeMillis();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("workingDate", currentDate.toString())
                .addLong("runTimestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(eodJob, jobParameters);
        long durationMs = System.currentTimeMillis() - startTime;

        LocalDate newDate = workingDateService.getCurrentWorkingDate();
        TrialBalanceReport trialReport = null;

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            try {
                trialReport = trialBalanceService.verifyTrialBalance(currentDate);
            } catch (Exception e) {
                log.warn("Post-EOD trial balance verification report generation warning: {}", e.getMessage());
            }
        }

        log.info("Spring Batch EOD Pipeline finished with status: {} in {} ms. Date moved: {} -> {}",
                jobExecution.getStatus(), durationMs, currentDate, newDate);

        return EodExecutionReport.builder()
                .jobExecutionId(jobExecution.getId())
                .status(jobExecution.getStatus().name())
                .previousWorkingDate(currentDate)
                .newWorkingDate(newDate)
                .durationMs(durationMs)
                .trialBalanceReport(trialReport)
                .completedAt(OffsetDateTime.now())
                .build();
    }
}
