package com.bytestorm.isp;

import com.bytestorm.utils.GoogleClientHelper;
import com.bytestorm.utils.Log;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GCSReports extends GoogleClientHelper implements ReportsProvider {

    public static final String KEEP_REPORTS = "gcs.reports.keep";
    public static final String FORCE_AUTHORIZATION = "gcs.force.auth";

    public GCSReports(Configuration cfg, Date date) throws IOException, IllegalArgumentException {
        config = cfg;
        if (null == cfg.getProperty("gcs.reports.bucket")) {
            if (DEFAULT_BUCKET.equals("<PLAY_BUCKET>")) {
                throw new IllegalArgumentException("GCS reports bucket id missing, and DEFAULT_BUCKET field is set");
            } else {
                cfg.put("gcs.reports.bucket", DEFAULT_BUCKET);
            }
        }
        if (cfg.getBoolean(FORCE_AUTHORIZATION, false)) {
            logout();
        }
        Credential credential = authorize(SCOPES);
        Log.v("Client authorized");
        // storage client
        this.bucket = cfg.getProperty("gcs.reports.bucket");
        this.date = new DateTime(date);
        Storage client = new Storage.Builder(getHttpTransport(), JSON_FACTORY, credential)
                .setApplicationName(APP_NAME)
                .build();
        downloadAll(client, "earnings/earnings_" + DATE_FORMAT.format(this.date.toDate()), earningReports);
        if (earningReports.isEmpty()) {
            throw new IOException("Cannot find earnings report for specified date");
        }
        downloadAll(client, "sales/salesreport_" + DATE_FORMAT.format(this.date.toDate()), salesReports);
        downloadAll(client, "sales/salesreport_" + DATE_FORMAT.format(this.date.plusMonths(1).toDate()), salesReports);
        if (cfg.getBoolean(KEEP_REPORTS, false)) {
            Log.v("Saving downloaded CSV files");
            // resolve collisions
            HashMap<String, ArrayList<File>> collisions = new HashMap<>();
            for (Map.Entry<File, String> entry : mapping.entrySet()) {
                ArrayList<File> filesWithName = collisions.get(entry.getValue());
                if (null == filesWithName) {
                    filesWithName = new ArrayList<>();
                    collisions.put(entry.getValue(), filesWithName);
                }
                filesWithName.add(entry.getKey());
            }
            for (Map.Entry<String, ArrayList<File>> entry : collisions.entrySet()) {
                if (entry.getValue().size() > 1) {
                    // colliding filename found
                    String name = FilenameUtils.getBaseName(entry.getKey());
                    String ext = FilenameUtils.getExtension(entry.getKey());
                    int n = 1;
                    for (File f : entry.getValue()) {
                        mapping.put(f, name + " (" + (n++) + ")" + ext);
                    }
                }
            }
            for (Map.Entry<File, String> entry : mapping.entrySet()) {
                final File src = entry.getKey();
                final File dst = new File(entry.getValue());
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Override
    public File[] getEarningsReportsFiles() throws IOException, IllegalArgumentException {
        return earningReports.toArray(new File[earningReports.size()]);
    }

    @Override
    public File[] getSalesReportsFiles() throws IOException, IllegalArgumentException {
        return salesReports.toArray(new File[salesReports.size()]);
    }

    @Override
    public Date getDate() {
        return date.toDate();
    }

    @Override
    protected byte[] getUserCredentialsDataImpl() {
        if (null != config.getProperty("gcs.client.secret.json.path")) {
            try {
                return IOUtils.toByteArray(new File(config.getProperty("gcs.client.secret.json.path")).toURI());
            } catch (Throwable e) {
                Log.v("Cannot load client secret from " + config.getProperty("gcs.client.secret.json.path") + " " + e);
            }
        }
        return null;
    }

    @Override
    protected ServiceCredentialsData getServiceCredentialsDataImpl() {
        if (null != config.getProperty("gcs.service.pk12.path") && null != config.getProperty("gcs.service.email")) {
            try {
                return new ServiceCredentialsData(IOUtils.toByteArray(new File(config.getProperty("gcs.service.cert.pk12.path")).toURI()),
                        config.getProperty("gcs.service.email"));
            } catch (Throwable e) {
                Log.v("Cannot load PC12 from " + config.getProperty("gcs.service.cert.pk12.path") + " " + e);
            }
        }
        return null;
    }

    private void downloadAll(Storage client, String prefix, ArrayList<File> out) throws IOException {
        Storage.Objects.List list = client.objects().list(bucket);
        list.setPrefix(prefix);
        Objects earningsReportsObjects = list.execute();
        for (StorageObject obj : earningsReportsObjects.getItems()) {
            out.add(downloadAndUnpack(client, obj));
        }
    }

    private File downloadAndUnpack(Storage client, StorageObject file) throws IOException {
        Log.v("Downloading storage file " + file.getName());
        String fileName = file.getName();
        Storage.Objects.Get getObject = client.objects().get(bucket, fileName);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        getObject.getMediaHttpDownloader().setDirectDownloadEnabled(true);
        getObject.executeMediaAndDownloadTo(data);
        File out = File.createTempFile("tmp", ".csv");
        String origName = ZipUtils.unpack(new ByteArrayInputStream(data.toByteArray()), out);
        mapping.put(out, origName);
        return out;
    }

    private Configuration config;
    private String bucket;
    private DateTime date;
    private ArrayList<File> earningReports = new ArrayList<>();
    private ArrayList<File> salesReports = new ArrayList<>();
    private HashMap<File, String> mapping = new HashMap<>();

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMM");

    private static final String DEFAULT_BUCKET = "<PLAY_BUCKET>";

    private static final String APP_NAME = "Bytestorm-ISP/1.0";
    private static final Collection<String> SCOPES = Collections.singleton(StorageScopes.DEVSTORAGE_READ_ONLY);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
}
