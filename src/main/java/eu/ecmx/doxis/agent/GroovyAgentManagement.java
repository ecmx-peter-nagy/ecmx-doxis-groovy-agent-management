package eu.ecmx.doxis.agent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ser.blueline.IDocumentServer;
import com.ser.blueline.ISession;
import com.ser.sedna.client.SEDNASession;
import com.ser.sedna.client.agentexecutionservice.AgentExecutionService;
import com.ser.sedna.client.agentexecutionservice.AgentExecutionServiceException;
import com.ser.sedna.client.exception.InvalidSessionException;
import com.ser.sedna.client.exception.ObjectStorageFailedException;
import com.ser.sedna.client.model.AgentDefinition;
import com.ser.sedna.client.model.AgentTechnologyType;
import com.ser.sedna.clientimpl.beans.SEDNASessionBean;
import com.ser.sedna.clientimpl.cache.SessionCacheMode;

import eu.ecmx.common.exception.EcmxException;
import eu.ecmx.common.util.DateUtils;
import eu.ecmx.dev.CommandLineParser;
import eu.ecmx.dev.NexusUpdater;
import eu.ecmx.doxis.DoxisConnector;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class GroovyAgentManagement {
    private static final Logger log = LogManager.getLogger(GroovyAgentManagement.class);
    
    private static final String CONFIG_FILE_PATHNAME = "config.properties";
    private static final String LOCAL_SCRIPTS_MAIN_DIR_PATHNAME = "src" + File.separator + "main" + File.separator + "groovy";
    private static final String LOCAL_SCRIPTS_TEST_DIR_PATHNAME = "src" + File.separator + "test" + File.separator + "groovy";
    private static final String FORMATTER_ARTIFACT_ID = "ecmx-groovy-code-formatter";
    
    private Properties configProperties = new Properties();
    
    private DoxisConnector doxisConnector;
    
    private IDocumentServer ds;
    private ISession s;
    private SEDNASession ss;
    
    private Scanner scanner;
    private String nextCommand;
    
    private String agentDefName;
    private String localAgentScriptPathname;
    private String localAgentLibPathname;
    private String remoteAgentDefId;
    
    private boolean localFormatterChecked = false;
    private String localFormatterPathname;
    
    private List<File> tempFileToDelete = new ArrayList<>();
    private List<URL> agentDependencies = new ArrayList<>();
    
    public void startRunMode(CommandLineParser parser) {
        String agent = parser.getOptionArg("agent");
        
        loadConfiguration(parser.getOptionArg("config-file"));
        
        connectToDoxis();
        
        chooseAgentDefinitionCommand(agent);
        
        runLocalAgentScriptCommand();
        
        exitCommand();
    }
    
    public void startDownloadAllMode(CommandLineParser parser) {
        loadConfiguration(parser.getOptionArg("config-file"));
        
        connectToDoxis();
        
        downloadRemoteAgentScriptsToLocalCommand();
        
        exitCommand();
    }
    
    public void startPromptMode() {
        
        scanner = new Scanner(System.in);
        
        // Enter leütésére \r\n kerül a sor végére (Windows, Eclipse)
        // => \r-t beolvassa, \n-t, mint delimitert átugorja
        // => minden Enter leütésre lefut a Groovy-szkript
        scanner.useDelimiter("\n");
        
        loadConfiguration(CONFIG_FILE_PATHNAME);
        
        connectToDoxis();
        
        boolean exit = false;
        
        do {
            readNextCommand();
            
            switch (nextCommand) {
                
                case "choose":
                    chooseAgentDefinitionCommand();
                    break;
                case "run":
                    runLocalAgentScriptCommand();
                    break;
                case "format":
                    formatLocalAgentScriptCommand();
                    break;
                
                case "compare":
                    compareLocalAndRemoteAgentScriptCommand();
                    break;
                case "download":
                    downloadRemoteAgentScriptToLocalCommand();
                    break;
                case "upload":
                    uploadLocalAgentScriptToRemoteCommand();
                    break;
                
                case "compare-all":
                    compareLocalAndRemoteAgentScriptsCommand();
                    break;
                case "download-all":
                    downloadRemoteAgentScriptsToLocalCommand();
                    break;
                
                case "refresh":
                    refreshCacheCommand();
                    break;
                case "cache-off":
                    turnCacheOffCommand();
                    break;
                case "cache-on":
                    turnCacheOnCommand();
                    break;
                
                case "exit":
                    exitCommand();
                    exit = true;
                    break;
                default:
                    System.out.println("Unknown command");
                    break;
            }
            
        } while (!exit);
        
        scanner.close();
        
        System.exit(0);
    }
    
    private void loadConfiguration(String configFilePathname) {
        
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(new FileInputStream(configFilePathname), StandardCharsets.UTF_8))) {
            configProperties.load(rd);
            log.info("konfiguracios fajl betoltve ({})", configFilePathname);
        } catch (FileNotFoundException e) {
            log.error("konfiguracios fajl hianyzik ({})", configFilePathname);
            throw new EcmxException("konfiguracios fajl hianyzik");
        } catch (IOException e) {
            log.error("konfiguracios fajl betoltese hibaba utkozott {})", configFilePathname);
            throw new EcmxException("konfiguracios fajl betoltese hibaba utkozott (" + configFilePathname + ")");
        }
    }
    
    private void connectToDoxis() {
        
        try {
            doxisConnector = new DoxisConnector(configProperties);
            s = doxisConnector.getSession();
            ds = doxisConnector.getDocumentServer();
            ss = ((com.ser.sedna.client.bluelineimpl.system.Session) s).getSednaSession();
            
            log.info("Doxis-kapcsolat felepitese sikeres");
            
        } catch (EcmxException e) {
            log.error(e.getMessage());
            System.exit(1);
        }
    }
    
    private void disconnnectFromDoxis() {
        doxisConnector.disconnectFromDoxis();
    }
    
    private void readNextCommand() {
        System.out.print("next command" + (agentDefName != null ? " (" + agentDefName + ")" : "") + ": ");
        nextCommand = scanner.nextLine().trim();
    }
    
    private void chooseAgentDefinitionCommand() {
        String fileName = "";
        
        do {
            System.out.print("agent name: ");
            fileName = scanner.nextLine().trim();
            
        } while (!chooseAgentDefinitionCommand(fileName));
    }
    
    /**
     * Név alapján kiválaszt egy agent-definíciót (a névben a .groovy suffix lehet, de nem szükséges).
     * 
     * <ul>
     * <li>Ellenőrzi, hogy a megadott névvel .groovy kiterjesztésű fájl a projekt <code>src/main/groovy</code> könyvtárában van-e (lokális verzió).</li>
     * <li>Ellenőrzi, hogy a megadott névvel (.groovy suffix nélkül) a Doxisban létezik-e agent-definíció (távoli verzió).</li>
     * <li>Ha a lokális és távoli szkript közül csak az egyik létezik, akkor erről üzenetben tájékoztat a program.</li>
     * <li>Ha a lokális verzió nem létezik, de a távoli igen, akkor a program felajánlja a lokális verzió létrehozását a távoli szkripttel és formázza azt (lásd
     * <code>format</code>).</li>
     * </ul>
     */
    private boolean chooseAgentDefinitionCommand(String fileName) {
        agentDefName = null;
        localAgentScriptPathname = null;
        remoteAgentDefId = null;
        
        agentDefName = fileName.endsWith(".groovy") ? fileName.substring(0, fileName.indexOf(".groovy")) : fileName;
        
        if (agentDefName.equals("")) {
            System.out.println("Local agent with name " + agentDefName + " not found");
            return false;
        }
        
        localAgentScriptPathname = LOCAL_SCRIPTS_MAIN_DIR_PATHNAME + File.separator + agentDefName + ".groovy";
        localAgentLibPathname = LOCAL_SCRIPTS_MAIN_DIR_PATHNAME + File.separator + agentDefName;
        
        boolean localAgentFound = Files.exists(Paths.get(localAgentScriptPathname));
        
        if (!localAgentFound) {
            localAgentScriptPathname = LOCAL_SCRIPTS_TEST_DIR_PATHNAME + File.separator + agentDefName + ".groovy";
            localAgentLibPathname = LOCAL_SCRIPTS_TEST_DIR_PATHNAME + File.separator + agentDefName;
            localAgentFound = Files.exists(Paths.get(localAgentScriptPathname));
        }
        
        getRemoteAgentDefinitionId();
        
        if (!localAgentFound && remoteAgentDefId != null) {
            System.out.print("Local script is not found but remote exits, do you want to create the local version from the remote? [y/n] ");
            
            String answear = scanner.nextLine().trim();
            
            if (answear.equalsIgnoreCase("n")) {
                agentDefName = null;
                System.out.println("Local agent with name " + agentDefName + " not found");
                return false;
            }
            
            try {
                writeLocalAgentScript(localAgentScriptPathname, getRemoteAgentDefinitionScript());
                System.out.println("Local script is successfully created");
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return false;
            }
            formatPath(localAgentScriptPathname);
        }
        
        if (!localAgentFound && remoteAgentDefId == null) {
            agentDefName = null;
            System.out.println("Agent is not found, neither local nor remote");
            return true;
        }
        
        File localAgentLib = new File(localAgentLibPathname);
        if (localAgentLib.isDirectory()) {
            for (File jar : localAgentLib.listFiles()) {
                if (jar.getName().endsWith(".jar")) {
                    try {
                        agentDependencies.add(jar.toURI().toURL());
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Futtatja a lokális Groovy-szkriptet. Amennyiben az <code>src/main/groovy</code> könyvtárában létezik az
     * agent-definíció nevével egyező alkönyvtár, akkor a benne lévő összes JAR-fájlt a class pathre teszi.
     */
    private void runLocalAgentScriptCommand() {
        
        if (agentDefName == null) {
            System.out.println("Agent is not chosen");
            return;
        }
        
        try {
            ClassLoader parent = getClass().getClassLoader();
            
            try (ChildFirstUrlClassLoader loader = new ChildFirstUrlClassLoader(agentDependencies.toArray(new URL[0]), parent)) {
                Thread currentThread = Thread.currentThread();
                ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
                
                try {
                    currentThread.setContextClassLoader(loader);
                    
                    Binding sharedData = new Binding();
                    GroovyShell shell = new GroovyShell(loader, sharedData);
                    
                    sharedData.setProperty("doxis4Session", s);
                    sharedData.setProperty("documentServer", ds);
                    sharedData.setProperty("sednaSession", ss);
                    
                    shell.evaluate(new File(localAgentScriptPathname));
                } finally {
                    currentThread.setContextClassLoader(previousContextClassLoader);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }
    }
    
    /**
     * Formázza a lokális Groovy-szkriptet az ecmx-groovy-code-formatter segítségével.
     */
    private void formatLocalAgentScriptCommand() {
        
        if (agentDefName == null) {
            System.out.println("Agent is not chosen");
            return;
        }
        
        formatPath(localAgentScriptPathname);
    }
    
    /**
     * Összehasonlítja a lokális és távoli szkriptet a <code>diff.tool</code> konfigurációs paraméterben megadott külső program segítségével.
     * 
     * Az összehasonlításhoz letölti a távoli szkriptet a <code>java.io.tmpdir</code>-be. Formázást nem végez a szkripteken.
     * A program (ecmx-doxis-groovy-agent-management) leállításakor a távoli szkriptfájlt törli.
     * 
     * <code>diff.tool</code> egy parancssorból meghívható program. A parancssori paraméterek között a <code>$LOCAL</code>-t automatikusan
     * lecseréli a lokális Groovy-fájl elérési útvonalára, a <code>$REMOTE</code>-ot automatikusan lecseréli a távoli szkript
     * <code>java.io.tmpdir</code>-be helyezett másolatának útvonalára.
     */
    private void compareLocalAndRemoteAgentScriptCommand() {
        
        if (remoteAgentDefId == null) {
            System.out.println("Remote agent with name " + agentDefName + " not found");
            return;
        }
        
        compareLocalAndRemoteAgentScript();
    }
    
    /**
     * Ha létezik az agent a Doxisban, akkor letölti az <code>src/main/groovy</code> könyvtárba, ha már létezik a lokális verzió,
     * akkor megerősítés után felülírja azt.
     * 
     * Formázást végez a szkripten (lásd <code>format</code>).
     */
    private void downloadRemoteAgentScriptToLocalCommand() {
        
        if (remoteAgentDefId == null) {
            System.out.println("Remote agent with name " + agentDefName + " not found");
            return;
        }
        
        // choose parancs után vagyunk vagyis itt már biztos létezik local verzió
        System.out.print("Do you want to overwrite the local script with the remote version? [y/n] ");
        String answear = scanner.nextLine().trim();
        
        if (answear.equalsIgnoreCase("y")) {
            try {
                writeLocalAgentScript(localAgentScriptPathname, getRemoteAgentDefinitionScript());
                System.out.println("Local script is successfully overwritten");
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return;
            }
            formatPath(localAgentScriptPathname);
        } else {
            System.out.println("Overwrite is denied");
        }
    }
    
    /**
     * Ha létezik az agent a Doxisban, akkor formázást végez a lokális szkripten (lásd <code>format</code>), megmutatja a különbséget a
     * lokális és a távoli szkript között (lásd <code>compare</code>) majd megerősítés után a lokálissal a távoli szkriptet felülírja.
     * 
     * Biztonsági mentést nem készít a távoli szkriptről!
     */
    private void uploadLocalAgentScriptToRemoteCommand() {
        
        if (remoteAgentDefId == null) {
            System.out.println("Remote agent with name " + agentDefName + " not found");
            return;
        }
        
        formatPath(localAgentScriptPathname);
        
        compareLocalAndRemoteAgentScript();
        
        System.out.print("Do you want to overwrite the remote script with the local version? [y/n] ");
        String answear = scanner.nextLine().trim();
        
        if (answear.equalsIgnoreCase("y")) {
            
            AgentExecutionService agentExSrv = ss.getAgentExecutionService();
            
            try {
                AgentDefinition remoteAgentDef = agentExSrv.findAgentDefinition(remoteAgentDefId);
                remoteAgentDef.setScript(getLocalAgentDefinitionScript());
                remoteAgentDef.save();
            } catch (AgentExecutionServiceException | ObjectStorageFailedException | InvalidSessionException e) {
                log.error(e);
            }
        } else {
            System.out.println("Overwrite is denied");
        }
    }
    
    /**
     * Összehasonlítja az összes lokális és távoli szkriptet a <code>diff.tool</code> konfigurációs paraméterben megadott külső program segítségével.
     * 
     * Az összehasonlításhoz letölti az összes távoli szkriptet a <code>java.io.tmpdir</code>-be. A lokális fájlokat a <code>src/main/groovy</code> könyvtárban keresi.
     * Formázást nem végez a szkripteken. A program (ecmx-doxis-groovy-agent-management) leállításakor a távoli szkriptfájlt törli.
     * 
     * <code>diff.tool</code> egy parancssorból meghívható program. A parancssori paraméterek között a <code>$LOCAL</code>-t automatikusan
     * lecseréli a lokális Groovy-fájl elérési útvonalára, a <code>$REMOTE</code>-ot automatikusan lecseréli a távoli szkript
     * <code>java.io.tmpdir</code>-be helyezett másolatának útvonalára.
     */
    private void compareLocalAndRemoteAgentScriptsCommand() {
        AgentExecutionService agentExSrv = ss.getAgentExecutionService();
        
        String timestamp = DateUtils.convertToString(new Date(), "yyyyMMddHHmmssSSS");
        String remoteScriptsTempDirPathname = System.getProperty("java.io.tmpdir") + File.separator + timestamp;
        
        try {
            Set<String> remoteAgentDefIds = agentExSrv.findAllAgentDefinitionIds();
            Iterator<String> i = remoteAgentDefIds.iterator();
            
            Files.createDirectory(Paths.get(remoteScriptsTempDirPathname));
            
            while (i.hasNext()) {
                AgentDefinition agentDef = agentExSrv.findAgentDefinition(i.next());
                
                // csak script agentekkel foglalkozunk (vannak Doxisba "épített" Java agentek is: DmnAgent, AuthenticationAgent)
                if (agentDef.getTechnologyType() != AgentTechnologyType.SCRIPT) {
                    continue;
                }
                
                if (agentDef.getName().equalsIgnoreCase("test_")) {
                    continue;
                }
                
                String remoteAgentScript = agentDef.getScript();
                
                // vannak Doxisba "épített" script agentek (pl. AGENT_DEFINITION_ROOT) melyeket skippelünk
                if (remoteAgentScript == null) {
                    continue;
                }
                
                Path remoteAgentScriptPath = Paths.get(remoteScriptsTempDirPathname + File.separator + agentDef.getName() + ".groovy");
                
                try (BufferedWriter out = Files.newBufferedWriter(remoteAgentScriptPath, StandardCharsets.UTF_8)) {
                    out.write(remoteAgentScript);
                    out.flush();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    return;
                }
            }
            
            tempFileToDelete.add(new File(remoteScriptsTempDirPathname));
            
        } catch (AgentExecutionServiceException | IOException e) {
            System.err.println(e.getMessage());
            return;
        }
        
        comparePaths(LOCAL_SCRIPTS_MAIN_DIR_PATHNAME, remoteScriptsTempDirPathname);
    }
    
    /**
     * Egyesével letölti Doxisból az összes távoli szkriptet, letöltés után ellenőrzi az <code>src/main/groovy</code> könyvtárban létezik-e
     * már lokális verzió. Ha igen, akkor megerősítés után felülírja azt. Ha nem, akkor egyszerűen létrehozza az új lokális szkriptet.
     * 
     * Formázást végez az összes lokális szkripten (lásd <code>format</code>) azon is aminek a felülírását nem engedélyezte a user.
     */
    private void downloadRemoteAgentScriptsToLocalCommand() {
        AgentExecutionService agentExSrv = ss.getAgentExecutionService();
        
        try {
            Set<String> remoteAgentDefIds = agentExSrv.findAllAgentDefinitionIds();
            Iterator<String> i = remoteAgentDefIds.iterator();
            
            while (i.hasNext()) {
                AgentDefinition agentDef = agentExSrv.findAgentDefinition(i.next());
                
                // csak script agentekkel foglalkozunk (vannak Doxisba "épített" Java agentek is: DmnAgent, AuthenticationAgent)
                if (agentDef.getTechnologyType() != AgentTechnologyType.SCRIPT) {
                    continue;
                }
                
                if (agentDef.getName().equalsIgnoreCase("test_")) {
                    continue;
                }
                
                String remoteAgentScript = agentDef.getScript();
                
                // vannak Doxisba "épített" script agentek (pl. AGENT_DEFINITION_ROOT) melyeket skippelünk
                if (remoteAgentScript == null) {
                    continue;
                }
                
                String localAgentScriptPathname = LOCAL_SCRIPTS_MAIN_DIR_PATHNAME + File.separator + agentDef.getName() + ".groovy";
                
                if (Files.exists(Paths.get(localAgentScriptPathname))) {
                    System.out.print("Local agent already exists (" + agentDef.getName() + ") do you want to overwrite the local script with the remote version? [y/n] ");
                    String answear = scanner.nextLine().trim();
                    
                    if (answear.equalsIgnoreCase("y")) {
                        
                        try {
                            writeLocalAgentScript(localAgentScriptPathname, remoteAgentScript);
                            System.out.println("Local script is successfully overwritten");
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            return;
                        }
                    }
                } else {
                    try {
                        writeLocalAgentScript(localAgentScriptPathname, remoteAgentScript);
                        System.out.println("Local script is successfully created");
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                        return;
                    }
                }
            }
            
            formatPath(LOCAL_SCRIPTS_MAIN_DIR_PATHNAME);
            
        } catch (AgentExecutionServiceException e) {
            System.err.println(e.getMessage());
            return;
        }
    }
    
    private void turnCacheOffCommand() {
        // nincs mindenre hatással pl. virtual global value list nem frissül
        ((SEDNASessionBean) ss).setSessionCacheMode(SessionCacheMode.SESSION_CACHE_OFF);
    }
    
    private void turnCacheOnCommand() {
        //default - kikapcs-bekapcs (OFF-READWRITE) nem frissít
        ((SEDNASessionBean) ss).setSessionCacheMode(SessionCacheMode.SESSION_CACHE_READWRITE);
    }
    
    private void refreshCacheCommand() {
        //s.refreshServerSessionCache(); //csak CSB API 8.1.0-tól
    }
    
    /**
     * Doxisból kijelentkezik ill. a program indulását követően <code>java.io.tmpdir</code>-ben létrehozott ideiglenes fájlokat és könyvtárakat törli.
     */
    private void exitCommand() {
        
        disconnnectFromDoxis();
        
        for (File file : tempFileToDelete) {
            if (file.isDirectory()) {
                Arrays.asList(file.listFiles()).stream().forEach(File::delete);
            }
            file.delete();
        }
    }
    
    private String getLocalAgentDefinitionScript() {
        String localAgentDefScript = "";
        
        try {
            localAgentDefScript = new String(Files.readAllBytes(Paths.get(localAgentScriptPathname)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error(e);
        }
        
        return localAgentDefScript;
    }
    
    private void getRemoteAgentDefinitionId() {
        AgentExecutionService agentExSrv = ss.getAgentExecutionService();
        
        try {
            List<AgentDefinition> agentDefs = agentExSrv.findAllAgentDefinitions();
            for (AgentDefinition agentDef : agentDefs) {
                if (agentDef.getName().equals(agentDefName)) {
                    
                    // csak script agentekkel foglalkozunk (vannak Doxisba "épített" Java agentek is: DmnAgent, AuthenticationAgent)
                    if (agentDef.getTechnologyType() != AgentTechnologyType.SCRIPT) {
                        continue;
                    }
                    
                    if (agentDef.getName().equalsIgnoreCase("test_")) {
                        continue;
                    }
                    
                    String remoteAgentScript = agentDef.getScript();
                    
                    // vannak Doxisba "épített" script agentek (pl. AGENT_DEFINITION_ROOT) melyeket skippelünk
                    if (remoteAgentScript == null) {
                        continue;
                    }
                    
                    remoteAgentDefId = agentDef.getId();
                    return;
                }
            }
        } catch (AgentExecutionServiceException e) {
            log.error(e);
        }
        
        System.out.println("Remote agent with name " + agentDefName + " not found");
    }
    
    private String getRemoteAgentDefinitionScript() {
        AgentExecutionService agentExSrv = ss.getAgentExecutionService();
        
        String remoteAgentScript = "";
        
        try {
            AgentDefinition remoteAgentDef = agentExSrv.findAgentDefinition(remoteAgentDefId);
            remoteAgentScript = remoteAgentDef.getScript();
        } catch (AgentExecutionServiceException e) {
            log.error(e);
        }
        
        return remoteAgentScript;
    }
    
    private void formatPath(String pathname) {
        if (!localFormatterChecked) {
            checkAndUpdateFormatter();
        }
        
        if (localFormatterPathname == null) {
            log.warn("formatter nem elerheto");
            return;
        }
        
        List<String> commands = Arrays.asList("java", "-jar", localFormatterPathname, "--path=" + pathname);
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        
        try {
            Process process = processBuilder.start();
            String stout = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                stout = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            } catch (IOException e) {
                log.error(e);
            }
            
            System.out.println(stout);
        } catch (IOException e1) {
            log.error(e1);
        }
    }
    
    /**
     * Formázáshoz használt ecmx-groovy-code-formattert nexus.ecmx.eu-ról a program indulását követően
     * egy alkalommal megkísérli frissíteni. A <code>user.home</code> system property által leírt könyvtár
     * <code>.ecmx</code> alkönyvtárában keresi a formattert, ha frissítés/letöltés nem lehetséges, akkor
     * ide kell helyezni a formatter JAR-fájlját
     */
    private void checkAndUpdateFormatter() {
        if (!localFormatterChecked) {
            try {
                NexusUpdater updater = new NexusUpdater(FORMATTER_ARTIFACT_ID);
                
                updater.updateLocalArtifact();
                
                if (!updater.isLocalArtifactUpToDate()) {
                    log.warn("formatter frissitese sikertelen");
                }
                
                localFormatterPathname = updater.getLocalArtifactPath() != null ? updater.getLocalArtifactPath().toString() : null;
            } catch (Exception e) {
                log.warn("Formatter initialization failed: " + e.getMessage());
                localFormatterPathname = null;
            }
            localFormatterChecked = true;
        }
    }
    
    /**
     * Írja a lokális szkriptet az <code>src\main\groovy</code> könyvtárban
     */
    private void writeLocalAgentScript(String localAgentScriptPathname, String remoteAgentScript) throws IOException {
        
        try (BufferedWriter out = Files.newBufferedWriter(Paths.get(localAgentScriptPathname), StandardCharsets.UTF_8)) {
            out.write(remoteAgentScript);
            out.flush();
        }
    }
    
    private void compareLocalAndRemoteAgentScript() {
        String remoteAgentScript = getRemoteAgentDefinitionScript();
        
        Path remoteAgentScriptPath = Paths.get(System.getProperty("java.io.tmpdir"), agentDefName + ".groovy");
        
        try (BufferedWriter out = Files.newBufferedWriter(remoteAgentScriptPath, StandardCharsets.UTF_8)) {
            out.write(remoteAgentScript);
            out.flush();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
        
        String remoteAgentScriptPathname = remoteAgentScriptPath.toString();
        
        tempFileToDelete.add(new File(remoteAgentScriptPathname));
        
        comparePaths(localAgentScriptPathname, remoteAgentScriptPathname);
    }
    
    private void comparePaths(String localPathname, String remotePathname) {
        List<String> diffToolCommands = new ArrayList<>();
        Pattern regex = Pattern.compile("\"([^\"]*)\"|([^\\s]+)");
        
        String diffTool = configProperties.getProperty("diff.tool");
        
        if (diffTool == null || diffTool.trim().equals("")) {
            System.out.println("Differencing tool is undefined");
            return;
        }
        
        Matcher regexMatcher = regex.matcher(configProperties.getProperty("diff.tool"));
        
        while (regexMatcher.find()) {
            String arg = "";
            if (regexMatcher.group(1) != null) {
                arg = regexMatcher.group(1);
            } else {
                arg = regexMatcher.group(0);
                
                arg = arg.replace("$LOCAL", localPathname);
                arg = arg.replace("$REMOTE", remotePathname);
            }
            
            diffToolCommands.add(arg);
        }
        
        ProcessBuilder processBuilder = new ProcessBuilder(diffToolCommands);
        processBuilder.inheritIO();
        
        System.out.println("Running diff tool: " + diffToolCommands);

        try {
            processBuilder.start();
        } catch (Exception e) {
            System.err.println("Error running diff tool: " + e.getMessage());
            e.printStackTrace();
            return;
        }
    }
}
