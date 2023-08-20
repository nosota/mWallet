package com.nosota.mwallet.config;

import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import com.nosota.mwallet.service.WalletSnapshotService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableBatchProcessing
public class SnapshotBatchConfig {

    @Autowired
    public JobBuilderFactory jobBuilderFactory;

    @Autowired
    public StepBuilderFactory stepBuilderFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WalletSnapshotService walletSnapshotService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Bean
    public Job captureSnapshotJob() {
        return jobBuilderFactory.get("captureSnapshotJob")
                .start(captureSnapshotStep())
                .build();
    }

    @Bean
    public Step captureSnapshotStep() {
        return stepBuilderFactory.get("captureSnapshotStep")
                .<Wallet, WalletBalance>chunk(100) // Define chunk size
                .reader(walletReader())
                .processor(walletProcessor())
                .writer(walletWriter())
                .build();
    }

    @Bean
    public ItemReader<Wallet> walletReader() {
        // Return a reader for the wallets. This can be a JpaPagingItemReader for reading from DB
        JpaPagingItemReader<Wallet> reader = new JpaPagingItemReader<>();

        reader.setEntityManagerFactory(entityManagerFactory);
        reader.setQueryString("SELECT w FROM Wallet w");
        reader.setPageSize(100);

        try {
            reader.afterPropertiesSet();
        } catch (Exception ex) {
            throw new RuntimeException("Initialization of JpaPagingItemReader failed", ex);
        }

        return reader;
    }

    @Bean
    public ItemProcessor<Wallet, WalletBalance> walletProcessor() {
        return wallet -> {
            return walletSnapshotService.captureSnapshotForWallet(wallet);
        };
    }

    @Bean
    public ItemWriter<WalletBalance> walletWriter() {
        // Return a writer that writes the WalletBalance to the database. This can be a JpaItemWriter
        JpaItemWriter<WalletBalance> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }
}
