package com.bytestorm.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.IOUtils;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;

abstract public class GoogleClientHelper {
    /**
     * Service credentials.
     */
    protected static class ServiceCredentialsData {
        public ServiceCredentialsData(byte[] pcks, String email) {
            this.pcks = pcks;
            this.email = email;
        }
        
        byte[] pcks;
        String email;
    }
        
    protected GoogleClientHelper() {
    }
        
    /**
     * 
     * @param scopes
     * @return
     * @throws IOException
     */
    final protected Credential authorize(Collection<String> scopes) throws IOException {
        synchronized (GoogleClientHelper.class) {
            if (null == httpTransport) {
                try {
                    httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                } catch (GeneralSecurityException e) {
                    throw new IOException("Unable to create http transport due to security exception", e);
                }
            }
        }
        ServiceCredentialsData serviceCredentialsData = getServiceCredentialsData();
        byte[] userCredentialsData; 
        if (null != serviceCredentialsData) {
            return authorizeService(httpTransport, serviceCredentialsData, scopes);
        }
        userCredentialsData = getUserCredentialsData();
        if (null != userCredentialsData) {
            return authorizeUser(httpTransport, userCredentialsData, scopes);
        }
        throw new RuntimeException("Application is not configured propertly - neither user nor service credtentials where provided");
    }    
        
    /**
     * Logs out of google account by removing stored authorization result (oauth2 token and such).
     */
    final protected void logout() {
        try {
            Preferences.systemNodeForPackage(getClass()).removeNode();
        } catch (BackingStoreException e) {
            throw new RuntimeException("Cannot logout user");
        }
    }
    
    final protected HttpTransport getHttpTransport() {
        synchronized (GoogleClientHelper.class) {
            if (null == httpTransport) {
                throw new IllegalStateException("Client is not authorized yet - call authorize() first");
            }
        }
        return httpTransport;
    }
    
    /**
     * Client secret (credentials) data. By default this application looks in resources for client_secret.json file
     * and load it if found.
     * @return client secret data (raw content of client secret json file as generated in google console) or 
     *  <code>null</code> if there is no default user credentials.
     */
    final protected byte[] getUserCredentialsData() {
        byte[] userCredentialsData = getUserCredentialsDataImpl();
        if (null == userCredentialsData) {
            userCredentialsData = loadStream(getClass().getResourceAsStream("/resources/client_secret.json"));           
        }
        return userCredentialsData;
    }
    
    final protected ServiceCredentialsData getServiceCredentialsData() {
        ServiceCredentialsData serviceCredentialsData = getServiceCredentialsDataImpl();
        if (null == serviceCredentialsData) {
            byte[] pcks = loadStream(getClass().getResourceAsStream("/resources/service_key.p12"));
            byte[] email = loadStream(getClass().getResourceAsStream("/resources/service_email"));
            if (null != pcks && null != email) {                
                try {
                    String emailStr = new String(email, "utf-8");
                    if (!emailStr.isEmpty()) {
                        serviceCredentialsData = new ServiceCredentialsData(pcks, emailStr);
                    }
                } catch (UnsupportedEncodingException e) {
                    // should not happens
                    e.printStackTrace();
                }                
            }                    
        }
        return serviceCredentialsData;
    }    
    
    final protected byte[] loadStream(InputStream in) {
        if (null != in) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {            
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            } catch (IOException ex) {
                try {
                    in.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    
    /**
     * 
     * @return
     */
    abstract protected byte[] getUserCredentialsDataImpl();
    
    /**
     * 
     * @return
     */
    abstract protected ServiceCredentialsData getServiceCredentialsDataImpl();    
    
    private Credential authorizeUser(HttpTransport http, byte[] creds, Collection<String> scopes) throws IOException, IllegalArgumentException {
        System.setProperty("org.mortbay.log.class", SimpleJettyLogger.class.getName());
        GoogleClientSecrets secret = null;
        try {
            secret = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(new ByteArrayInputStream(creds)));            
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to load client id file", ex);
        }
        if (null == secret) {
            throw new IllegalArgumentException("Unable to load client id file");
        }
        if (null == secret.getDetails().getClientId() || null == secret.getDetails().getClientSecret()) {
            throw new IllegalArgumentException("Client id file is not well formed.");
        }
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(http, JSON_FACTORY, secret, scopes)
                .setDataStoreFactory(new AbstractDataStoreFactory() {        
                    @Override
                    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
                        return new PreferencesDataStore<V>(this, id, GoogleClientHelper.this.getClass());
                    }
                })
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
    
    private Credential authorizeService(HttpTransport http, ServiceCredentialsData creds, Collection<String> scopes) throws IOException {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(creds.pcks), "notasecret".toCharArray());
            PrivateKey pk = (PrivateKey) ks.getKey("privatekey", "notasecret".toCharArray());
            return new GoogleCredential.Builder()
                    .setTransport(http)
                    .setJsonFactory(JSON_FACTORY)
                    .setServiceAccountId(creds.email)
                    .setServiceAccountPrivateKey(pk)
                    .setServiceAccountScopes(scopes)
                    .build();
        } catch (KeyStoreException e) {
            throw new Error("Cannot initialize PKCS12 key store");
        } catch (NoSuchAlgorithmException e) {
            throw new Error("Cannot load P12 key");
        } catch (CertificateException e) {
            throw new IllegalArgumentException("Invalid P12 key file");
        } catch (UnrecoverableKeyException e) {
            throw new IllegalArgumentException("Invalid P12 key file");
        }
    }
        
    private static class PreferencesDataStore<V extends Serializable> extends AbstractDataStore<V> {
        PreferencesDataStore(DataStoreFactory dataStore, String id, Class<?> clazz) {
            super(dataStore, id);
            prefs = Preferences.userNodeForPackage(clazz).node(id);
        }

        @Override
        public DataStore<V> clear() throws IOException {
            try {
                prefs.clear();
                return this;
            } catch (BackingStoreException e) {
                throw new IOException("Preferences exception", e);
            }            
        }

        @Override
        public DataStore<V> delete(String key) throws IOException {
            try {
                prefs.remove(key);
                prefs.sync();
                return this;
            } catch (BackingStoreException e) {
                throw new IOException("Preferences exception", e);
            }        
        }

        @Override
        public V get(String key) throws IOException {
            return IOUtils.deserialize(prefs.getByteArray(key, null));
        }

        @Override
        public Set<String> keySet() throws IOException {
            try {
                return new HashSet<String>(Arrays.asList(prefs.keys()));
            } catch (BackingStoreException e) {
                throw new IOException("Preferences exception", e);
            }             
        }

        @Override
        public DataStore<V> set(String key, V value) throws IOException {
            try {
                prefs.putByteArray(key, IOUtils.serialize(value));
                prefs.sync();
                return this;
            } catch (BackingStoreException e) {
                throw new IOException("Preferences exception", e);
            }            
        }

        @Override
        public Collection<V> values() throws IOException {
            try {
                ArrayList<V> retval = new ArrayList<>();
                for (String key : prefs.keys()) {
                    retval.add(IOUtils.deserialize(prefs.getByteArray(key, null)));
                }
                return retval;
            } catch (BackingStoreException e) {
                throw new IOException("Preferences exception", e);
            }                
        }
        
        final private Preferences prefs;
    }        
    
    private static HttpTransport httpTransport;
    
    private final static JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
}
