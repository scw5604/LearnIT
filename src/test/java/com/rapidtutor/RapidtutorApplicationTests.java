package com.rapidtutor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "rapidtutor.demo-users.enabled=false")
class RapidtutorApplicationTests {

	@Test
	void contextLoads() {
	}

}
