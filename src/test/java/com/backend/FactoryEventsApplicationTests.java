package com.backend;

import com.backend.testutil.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

public class FactoryEventsApplicationTests {

	@TestConfiguration
	public static class TestClockConfig {

		@Bean
		public MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
		}

		@Bean
		@Primary
		public Clock testClock(MutableClock mutableClock) {
			return mutableClock;
		}
	}
}
