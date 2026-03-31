package eu.ecmx.doxis.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(args = {"--config-file=src\\test\\resources\\config.properties", "--agent=test_agent", "--run"})
class ApplicationTests {
	@Test
	void contextLoads() {
	}
}
