package eu.ecmx.doxis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ser.blueline.IDocumentServer;
import com.ser.blueline.ISession;
import com.ser.blueline.sessionpool.IDx4SessionPool;
import com.ser.blueline.sessionpool.IDx4SessionPoolConfiguration;
import com.ser.blueline.sessionpool.InstantiationException;

import eu.ecmx.common.exception.EcmxException;
import eu.ecmx.common.util.ExceptionUtils;

public class DoxisConnector {
    private static final Logger log = LogManager.getLogger(DoxisConnector.class);
    
    private Properties configProperties = new Properties();
    
    private String csbHost;
    private int csbPort;
    private boolean csbSsl;
    private String orgName;
    private String username;
    private String password;
    private String role;
    
    private IDx4SessionPool sessionPool;
    
    private ISession session;
    private IDocumentServer documentServer;
    
    public DoxisConnector(String configFilePathname) throws EcmxException {
        loadConfiguration(configFilePathname);
        checkConfiguration();
        connectToDoxis();
    }
    
    public DoxisConnector(Properties configProps) throws EcmxException {
        this.configProperties = configProps;
        checkConfiguration();
        connectToDoxis();
    }
    
    private void loadConfiguration(String configFilePathname) {
        log.debug("loadConfiguration()");
        
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(new FileInputStream(configFilePathname), StandardCharsets.UTF_8))) {
            configProperties.load(rd);
            log.info("konfiguracios fajl betoltve");
        } catch (FileNotFoundException e) {
            throw new EcmxException("konfiguracios fajl hianyzik");
        } catch (IOException e) {
            throw new EcmxException("konfiguracios fajl betoltese hibaba utkozott");
        }
    }
    
    private void checkConfiguration() {
        log.debug("checkConfiguration()");
        
        csbHost = configProperties.getProperty("csb.host");
        csbPort = Integer.parseInt(configProperties.getProperty("csb.port"));
        csbSsl = Boolean.parseBoolean(configProperties.getProperty("csb.ssl"));
        orgName = configProperties.getProperty("org.shortname");
        username = configProperties.getProperty("org.username") != null ? configProperties.getProperty("org.username") : configProperties.getProperty("username");
        password = configProperties.getProperty("org.password") != null ? configProperties.getProperty("org.password") : configProperties.getProperty("password");
        role = configProperties.getProperty("org.role") != null ? configProperties.getProperty("org.role") : configProperties.getProperty("role");
        
        log.info("CSB host: {}; CSB port: {}; CSB SSL: {}", csbHost, csbPort, csbSsl);
        log.info("org shortname: {}; username: {}; role: {}", orgName, username, role);
    }
    
    private void connectToDoxis() {
        log.debug("connectToDoxis()");
        
        try {
            IDx4SessionPoolConfiguration.IBuilder cfg = IDx4SessionPoolConfiguration.builder();
            cfg.addCsbNode(csbHost, csbPort);
            cfg.setUseSSL(csbSsl);
            cfg.addSessionCredentials(orgName, username, password, role);
            sessionPool = IDx4SessionPool.create(cfg.build());
            
            session = sessionPool.getSession(orgName);
            documentServer = session.getDocumentServer();
        } catch (InstantiationException e) {
            log.error(ExceptionUtils.convertStackTracesToString(e));
            throw new EcmxException("Doxis-kapcsolat letesitese sikertelen");
        }
    }
    
    public void disconnectFromDoxis() {
        log.debug("disconnectFromDoxis()");
        
        sessionPool.releaseSession(session);
        sessionPool.close();
    }
    
    public ISession getSession() {
        return session;
    }
    
    public IDocumentServer getDocumentServer() {
        return documentServer;
    }
}
