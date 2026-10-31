package com.rapidtutor.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.sql.SQLException;

/**
 * The second process still exits. This only replaces the H2 lock dump with one line.
 */
public class H2AlreadyRunningAnalyzer extends AbstractFailureAnalyzer<SQLException> {

    public static final String ALREADY_RUNNING =
            "LearnIT is already running. Stop the other copy before starting this one.";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
        SQLException current = cause;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase().contains("already in use")
                    && current.getClass().getName().contains("JdbcSQL")) {
                return new FailureAnalysis(ALREADY_RUNNING, ALREADY_RUNNING, current);
            }
            current = current.getNextException();
        }
        return null;
    }
}
