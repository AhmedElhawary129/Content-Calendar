package dev.danvega.content_calendar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // use application-test.properties (H2 instead of Postgres)
class ContentCalendarApplicationTests {

	@Test
	void contextLoads() {
	}

}
