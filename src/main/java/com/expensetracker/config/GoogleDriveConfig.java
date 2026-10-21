package com.expensetracker.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Builds the Google Drive client from a service-account key. The key may be
 * supplied inline ({@code app.drive.credentials-json}) or as a file path
 * ({@code app.drive.credentials-path}). Only created when a Drive file id is set.
 */
@Configuration
@ConditionalOnNonBlankProperty("app.drive.file-id")
public class GoogleDriveConfig {

    @Bean
    public Drive googleDrive(AppProperties props) throws IOException, GeneralSecurityException {
        AppProperties.Drive cfg = props.getDrive();
        GoogleCredentials credentials;
        try (InputStream keyStream = openKey(cfg)) {
            credentials = GoogleCredentials.fromStream(keyStream)
                    .createScoped(List.of(DriveScopes.DRIVE));
        }
        HttpRequestInitializer initializer = new HttpCredentialsAdapter(credentials);
        return new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                initializer)
                .setApplicationName("expense-tracker")
                .build();
    }

    private InputStream openKey(AppProperties.Drive cfg) throws IOException {
        if (cfg.getCredentialsJson() != null && !cfg.getCredentialsJson().isBlank()) {
            return new ByteArrayInputStream(cfg.getCredentialsJson().getBytes(StandardCharsets.UTF_8));
        }
        if (cfg.getCredentialsPath() != null && !cfg.getCredentialsPath().isBlank()) {
            return Files.newInputStream(Path.of(cfg.getCredentialsPath()));
        }
        throw new IllegalStateException(
                "Google Drive is configured (app.drive.file-id) but no service-account key was "
                        + "provided (set app.drive.credentials-json or app.drive.credentials-path).");
    }
}
