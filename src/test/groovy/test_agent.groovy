import com.ser.blueline.*

import de.ser.doxis4.agentserver.AgentExecutionResult
import de.ser.doxis4.agentserver.AgentServerReturnCodes

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import groovy.transform.Field

@Field Logger log = LogManager.getLogger("eu.ecmx.doxis.agent")
@Field ISession s = doxis4Session
@Field IDocumentServer ds = documentServer

try {
    log.info("------------------------------ agent job elindult ------------------------------")
    
    log.info("Hello, world")
    
    log.info("---------------------------- agent job befejezodott ----------------------------")
    
    return new AgentExecutionResult(AgentServerReturnCodes.RETURN_CODE_SUCCESS, null, false, null)
} catch (Exception e) {
    log.error("hiba: " + e.getMessage())
    
    log.error("---------------------------- agent job befejezodott ----------------------------")
    
    return new AgentExecutionResult(AgentServerReturnCodes.RETURN_CODE_ERROR, "Hiba: " + e.getMessage(), false, null)
}
