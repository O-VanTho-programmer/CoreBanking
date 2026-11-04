package com.cbs.eod.config;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.eod.service.BalanceSnapshotService;
import com.cbs.eod.service.DataArchivalService;
import com.cbs.eod.service.InterestAccrualService;
import com.cbs.eod.service.TrialBalanceService;
import com.cbs.lending.service.LoanAgingService;
import com.cbs.lending.service.LoanRepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

/**
 * Spring Batch 5 Configuration for the End-of-Day (EOD) Batch Pipeline.
 *
 * Pipeline Sequence:
 * Step 1: CutoffStep        -> Locks daytime transaction channels (is_system_cutoff = 'Y')
 * Step 2: AccrualStep       -> Daily interest accrual calculation and GL balancing (DR 8011 / CR 4911)
 * Step 3: SnapshotStep      -> Freezes closing balance and turnover buckets (ACCOUNT_BALANCE_BUCKET)
 * Step 4: ArchivalStep      -> Migrates completed daytime transactions from Active to Done (MoveDataHis)
 * Step 5: TrialBalanceStep  -> Triple reconciliation of macro equilibrium and sub-ledger vs. GL 4211
 * Step 6: RolloverStep      -> Advances operational working date in BANK_CALENDAR and releases cut-off
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class EodBatchConfig {

    public static final String EOD_JOB_NAME = "eodJob";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WorkingDateService workingDateService;
    private final LoanRepaymentService loanRepaymentService;
    private final LoanAgingService loanAgingService;
    private final InterestAccrualService interestAccrualService;
    private final BalanceSnapshotService balanceSnapshotService;
    private final DataArchivalService dataArchivalService;
    private final TrialBalanceService trialBalanceService;

    @Bean
    public Job eodJob(Step cutoffStep,
                      Step loanRepaymentStep,
                      Step loanAgingStep,
                      Step accrualStep,
                      Step snapshotStep,
                      Step archivalStep,
                      Step trialBalanceStep,
                      Step rolloverStep) {
        return new JobBuilder(EOD_JOB_NAME, jobRepository)
                .start(cutoffStep)
                .next(loanRepaymentStep)
                .next(loanAgingStep)
                .next(accrualStep)
                .next(snapshotStep)
                .next(archivalStep)
                .next(trialBalanceStep)
                .next(rolloverStep)
                .build();
    }

    @Bean
    public Step cutoffStep() {
        return new StepBuilder("cutoffStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[EOD Step 1/8] Enabling Cut-off: Locking daytime transaction channels");
                    workingDateService.enableCutoff();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step loanRepaymentStep() {
        return new StepBuilder("loanRepaymentStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate workingDate = extractWorkingDate(chunkContext);
                    log.info("[EOD Step 2/8] Executing Automated Loan Repayment for date: {}", workingDate);
                    loanRepaymentService.executeAutoRepayment(workingDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step loanAgingStep() {
        return new StepBuilder("loanAgingStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate workingDate = extractWorkingDate(chunkContext);
                    log.info("[EOD Step 3/8] Executing Loan Aging, Classification & Provisioning for date: {}", workingDate);
                    loanAgingService.executeAgingAndProvisioning(workingDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step accrualStep() {
        return new StepBuilder("accrualStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate workingDate = extractWorkingDate(chunkContext);
                    log.info("[EOD Step 4/8] Executing Daily Savings and CASA Interest Accrual for date: {}", workingDate);
                    interestAccrualService.executeDailyAccrual(workingDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step snapshotStep() {
        return new StepBuilder("snapshotStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate workingDate = extractWorkingDate(chunkContext);
                    log.info("[EOD Step 5/8] Taking Daily Balance Snapshot Buckets for date: {}", workingDate);
                    balanceSnapshotService.takeDailySnapshot(workingDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step archivalStep() {
        return new StepBuilder("archivalStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate workingDate = extractWorkingDate(chunkContext);
                    log.info("[EOD Step 6/8] Archiving completed data to cold storage (MoveDataHis) for date: {}", workingDate);
                    dataArchivalService.archiveCompletedTransactions(workingDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step trialBalanceStep() {
        return new StepBuilder("trialBalanceStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate workingDate = extractWorkingDate(chunkContext);
                    log.info("[EOD Step 7/8] Verifying General Ledger Trial Balance for date: {}", workingDate);
                    trialBalanceService.verifyTrialBalance(workingDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step rolloverStep() {
        return new StepBuilder("rolloverStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[EOD Step 8/8] Rolling over operational date and releasing cut-off");
                    LocalDate nextDate = workingDateService.rolloverWorkingDate();
                    log.info("[EOD Pipeline Complete] New operational working date is: {}", nextDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    private LocalDate extractWorkingDate(ChunkContext chunkContext) {
        Object param = chunkContext.getStepContext().getJobParameters().get("workingDate");
        if (param instanceof String dateStr && !dateStr.isBlank()) {
            return LocalDate.parse(dateStr);
        }
        return workingDateService.getCurrentWorkingDate();
    }
}
