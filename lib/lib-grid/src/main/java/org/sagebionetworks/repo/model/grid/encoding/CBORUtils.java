package org.sagebionetworks.repo.model.grid.encoding;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

public class CBORUtils {

    private static final CBORMapper CBOR_MAPPER = new CBORMapper();
    private static final CBORFactory CBOR_FACTORY = CBOR_MAPPER.getFactory();

    public static CBORMapper getCBORMapper() {
        return CBOR_MAPPER;
    }

    public static CBORFactory getCBORFactory() {
        return CBOR_FACTORY;
    }

}