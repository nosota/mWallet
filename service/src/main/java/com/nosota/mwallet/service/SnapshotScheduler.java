package com.nosota.mwallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SnapshotScheduler {
    private static Logger LOG = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job captureSnapshotJob;

    @Scheduled(cron = "0 0 0 * * ?")  // Schedule for every day at midnight
    public void runSnapshotJob() {
        try {
            jobLauncher.run(captureSnapshotJob, new JobParameters());
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
