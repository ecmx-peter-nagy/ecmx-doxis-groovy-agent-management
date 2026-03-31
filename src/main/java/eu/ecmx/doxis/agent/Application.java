package eu.ecmx.doxis.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import eu.ecmx.dev.CommandLineParser;
import eu.ecmx.dev.Option;
import eu.ecmx.dev.Usage;
import eu.ecmx.dev.UsagePattern;

@SpringBootApplication
public class Application implements CommandLineRunner {
    private static final Logger log = LogManager.getLogger(Application.class);
    
    private List<UsagePattern> usagePatterns = new ArrayList<>();
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        registerUsagePatterns();
        GroovyAgentManagement agentManagement = new GroovyAgentManagement();
        
        if (args.length == 0) {
            agentManagement.startPromptMode();
        } else {
            CommandLineParser parser = new CommandLineParser();
            parser.parse(args, new Usage("java -jar ecmx-doxis-groovy-management.jar", usagePatterns.toArray(new UsagePattern[0])));
        }
    }
    
    private void registerUsagePatterns() {
        Option agentOption = Option.builder("agent").argName("agent-file-name").build();
        Option runOption = Option.builder("run").build();
        Option downloadAllOption = Option.builder("download-all").build();
        
        GroovyAgentManagement agentManagement = new GroovyAgentManagement();
        
        usagePatterns.add(
            new UsagePattern(
                agentManagement::startRunMode,
                Option.builder("config-file").argName("properties-file-path").description("Path of the configuration file containing properties of the Doxis connection").build(),
                agentOption,
                runOption
            )
        );
        usagePatterns.add(
            new UsagePattern(
                agentManagement::startDownloadAllMode,
                Option.builder("config-file").argName("properties-file-path").description("Path of the configuration file containing properties of the Doxis connection").build(),
                downloadAllOption
            )
        );
    }
}
