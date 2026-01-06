package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

public class Base36UtilsTest {

	// ==================== encodeBase36 Tests ====================

	@Test
	public void testEncodeBase36_Zero() {
		assertEquals("0", Base36Utils.encodeBase36(0));
	}

	@Test
	public void testEncodeBase36_SmallValues() {
		assertEquals("1", Base36Utils.encodeBase36(1));
		assertEquals("9", Base36Utils.encodeBase36(9));
		assertEquals("a", Base36Utils.encodeBase36(10));
		assertEquals("z", Base36Utils.encodeBase36(35));
	}

	@Test
	public void testEncodeBase36_36() {
		// 36 in base36 is "10"
		assertEquals("10", Base36Utils.encodeBase36(36));
	}

	@Test
	public void testEncodeBase36_LargeValues() {
		// 1000 = 27*36 + 28 = "rs"
		assertEquals("rs", Base36Utils.encodeBase36(1000));
		// 1000000
		assertEquals("lfls", Base36Utils.encodeBase36(1000000));
	}

	@Test
	public void testEncodeBase36_NegativeValue() {
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.encodeBase36(-1);
		});
	}

	// ==================== decodeBase36 Tests ====================

	@Test
	public void testDecodeBase36_Zero() {
		assertEquals(0, Base36Utils.decodeBase36("0"));
	}

	@Test
	public void testDecodeBase36_SmallValues() {
		assertEquals(1, Base36Utils.decodeBase36("1"));
		assertEquals(9, Base36Utils.decodeBase36("9"));
		assertEquals(10, Base36Utils.decodeBase36("a"));
		assertEquals(10, Base36Utils.decodeBase36("A")); // Case insensitive
		assertEquals(35, Base36Utils.decodeBase36("z"));
	}

	@Test
	public void testDecodeBase36_36() {
		assertEquals(36, Base36Utils.decodeBase36("10"));
	}

	@Test
	public void testDecodeBase36_LargeValues() {
		assertEquals(1000, Base36Utils.decodeBase36("rs"));
		assertEquals(1000000, Base36Utils.decodeBase36("lfls"));
	}

	@Test
	public void testDecodeBase36_EmptyString() {
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.decodeBase36("");
		});
	}

	@Test
	public void testDecodeBase36_InvalidCharacter() {
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.decodeBase36("abc!");
		});
	}

	@Test
	public void testDecodeBase36_Null() {
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.decodeBase36(null);
		});
	}

	// ==================== Round-Trip Tests ====================

	@Test
	public void testBase36RoundTrip() {
		long[] testValues = { 0, 1, 10, 35, 36, 100, 1000, 10000, 100000, 1000000, Long.MAX_VALUE / 1000 };
		for (long value : testValues) {
			String encoded = Base36Utils.encodeBase36(value);
			long decoded = Base36Utils.decodeBase36(encoded);
			assertEquals(value, decoded, "Round-trip failed for value: " + value);
		}
	}

	// ==================== encodeNodeKey Tests ====================

	@Test
	public void testEncodeNodeKey() {
		assertEquals("0_0", Base36Utils.encodeNodeKey(0, 0));
		assertEquals("0_1", Base36Utils.encodeNodeKey(0, 1));
		assertEquals("1_0", Base36Utils.encodeNodeKey(1, 0));
		assertEquals("a_z", Base36Utils.encodeNodeKey(10, 35));
		assertEquals("10_10", Base36Utils.encodeNodeKey(36, 36));
	}

	// ==================== decodeNodeKey Tests ====================

	@Test
	public void testDecodeNodeKey() {
		assertArrayEquals(new long[] { 0, 0 }, Base36Utils.decodeNodeKey("0_0"));
		assertArrayEquals(new long[] { 0, 1 }, Base36Utils.decodeNodeKey("0_1"));
		assertArrayEquals(new long[] { 1, 0 }, Base36Utils.decodeNodeKey("1_0"));
		assertArrayEquals(new long[] { 10, 35 }, Base36Utils.decodeNodeKey("a_z"));
		assertArrayEquals(new long[] { 36, 36 }, Base36Utils.decodeNodeKey("10_10"));
	}

	@Test
	public void testDecodeNodeKey_InvalidFormat() {
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.decodeNodeKey("abc");
		});
	}

	@Test
	public void testDecodeNodeKey_Null() {
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.decodeNodeKey(null);
		});
	}

	// ==================== encodeNodeKey with ClockTable Tests ====================

	@Test
	public void testEncodeNodeKeyWithClockTable() {
		List<LogicalTimestamp> clockTable = List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L)
		);

		LogicalTimestamp ts0 = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L);
		assertEquals("0_19", Base36Utils.encodeNodeKey(ts0, clockTable)); // session 0, seq 45 = "19" in base36

		LogicalTimestamp ts1 = new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(95L);
		assertEquals("1_2n", Base36Utils.encodeNodeKey(ts1, clockTable)); // session 1, seq 95 = "2n" in base36
	}

	@Test
	public void testEncodeNodeKeyWithClockTable_ReplicaNotFound() {
		List<LogicalTimestamp> clockTable = List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		);

		LogicalTimestamp ts = new LogicalTimestamp().setReplicaId(999L).setSequenceNumber(45L);
		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.encodeNodeKey(ts, clockTable);
		});
	}

	// ==================== decodeNodeKeyToTimestamp Tests ====================

	@Test
	public void testDecodeNodeKeyToTimestamp() {
		List<LogicalTimestamp> clockTable = List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L)
		);

		LogicalTimestamp result0 = Base36Utils.decodeNodeKeyToTimestamp("0_19", clockTable);
		assertEquals(100L, result0.getReplicaId());
		assertEquals(45L, result0.getSequenceNumber()); // "19" in base36 = 45

		LogicalTimestamp result1 = Base36Utils.decodeNodeKeyToTimestamp("1_2n", clockTable);
		assertEquals(200L, result1.getReplicaId());
		assertEquals(95L, result1.getSequenceNumber()); // "2n" in base36 = 95
	}

	@Test
	public void testDecodeNodeKeyToTimestamp_SessionOutOfBounds() {
		List<LogicalTimestamp> clockTable = List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		);

		assertThrows(IllegalArgumentException.class, () -> {
			Base36Utils.decodeNodeKeyToTimestamp("5_0", clockTable); // session 5 doesn't exist
		});
	}

	// ==================== Full Round-Trip with ClockTable ====================

	@Test
	public void testNodeKeyRoundTripWithClockTable() {
		List<LogicalTimestamp> clockTable = List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L),
			new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(150L)
		);

		// Test various timestamps
		LogicalTimestamp[] timestamps = {
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(0L),
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(99L),
			new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(1000L)
		};

		for (LogicalTimestamp original : timestamps) {
			String encoded = Base36Utils.encodeNodeKey(original, clockTable);
			LogicalTimestamp decoded = Base36Utils.decodeNodeKeyToTimestamp(encoded, clockTable);

			assertEquals(original.getReplicaId(), decoded.getReplicaId(),
				"ReplicaId mismatch for: " + original);
			assertEquals(original.getSequenceNumber(), decoded.getSequenceNumber(),
				"SequenceNumber mismatch for: " + original);
		}
	}
}
