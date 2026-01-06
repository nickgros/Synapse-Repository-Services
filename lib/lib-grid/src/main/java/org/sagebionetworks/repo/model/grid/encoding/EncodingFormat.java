package org.sagebionetworks.repo.model.grid.encoding;

/**
 * Enumeration of supported encoding formats for JSON CRDT nodes.
 *
 * @see <a href="https://jsonjoy.com/specs/json-crdt/encoding">JSON Joy Encoding Specification</a>
 */
public enum EncodingFormat {
	/**
	 * Binary Structural Encoding - Compact binary format that preserves
	 * the full structural information of the CRDT.
	 */
	BINARY_STRUCTURAL,

	/**
	 * Indexed Encoding - Optimized binary format that uses indexed references
	 * to reduce redundancy when encoding node trees.
	 */
	INDEXED
}

