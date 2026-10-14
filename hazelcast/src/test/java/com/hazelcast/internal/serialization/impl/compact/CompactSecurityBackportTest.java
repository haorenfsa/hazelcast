/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.serialization.impl.compact;

import com.hazelcast.config.SerializationConfig;
import com.hazelcast.internal.nio.Packet;
import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.serialization.impl.HeapData;
import com.hazelcast.nio.serialization.HazelcastSerializationException;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class CompactSecurityBackportTest {
    private static InternalSerializationService service(SerializationConfig config) {
        Map<Long, Schema> schemas = new HashMap<>();
        return new DefaultSerializationServiceBuilder().setConfig(config).setSchemaService(new SchemaService() {
            @Override
            public Schema get(long id) {
                return schemas.get(id);
            }

            @Override
            public void put(Schema schema) {
                schemas.put(schema.getSchemaId(), schema);
            }

            @Override
            public void putLocal(Schema schema) {
                put(schema);
            }
        }).build();
    }

    @Test
    public void remoteInternalTypesStayGenericIncludingCachedReads() {
        InternalSerializationService service = service(new SerializationConfig());
        try {
            String[] types = {HeapData.class.getName(), Packet.class.getName(),
                    "com.hazelcast.internal.serialization.impl.UnsafeObjectDataInput", "java.lang.String"};
            for (String type : types) {
                GenericRecord record = GenericRecordBuilder.compact(type).setInt32("marker", 42).build();
                for (int attempt = 0; attempt < 2; attempt++) {
                    Object restored = service.toObject(service.toData(record));
                    assertTrue(type, restored instanceof GenericRecord);
                    assertEquals(42, ((GenericRecord) restored).getInt32("marker"));
                }
            }
        } finally {
            service.dispose();
        }
    }

    @Test
    public void nestedInternalTypeStaysGeneric() {
        InternalSerializationService service = service(new SerializationConfig());
        try {
            GenericRecord inner = GenericRecordBuilder.compact(HeapData.class.getName()).build();
            GenericRecord outer = GenericRecordBuilder.compact("unknown.Envelope")
                    .setGenericRecord("value", inner).build();
            GenericRecord restored = service.toObject(service.toData(outer));
            assertTrue(restored.getGenericRecord("value") instanceof GenericRecord);
        } finally {
            service.dispose();
        }
    }

    @Test
    public void declarativeReflectionCannotBypassBlocklist() {
        for (Class<?> type : new Class<?>[]{HeapData.class, Packet.class, String.class}) {
            SerializationConfig config = new SerializationConfig();
            config.getCompactSerializationConfig().addClass(type);
            assertThrows(HazelcastSerializationException.class, () -> service(config));
        }
    }

    @Test
    public void ordinaryDtoAndRecordStillRoundTrip() {
        InternalSerializationService service = service(new SerializationConfig());
        try {
            Dto restored = service.toObject(service.toData(new Dto(42)));
            assertEquals(42, restored.value);
            ValueRecord record = new ValueRecord(24);
            assertEquals(record, service.toObject(service.toData(record)));
        } finally {
            service.dispose();
        }
    }

    @Test
    public void explicitlyRegisteredDtoStillRoundTrips() {
        SerializationConfig config = new SerializationConfig();
        config.getCompactSerializationConfig().addClass(Dto.class);
        InternalSerializationService service = service(config);
        try {
            Dto restored = service.toObject(service.toData(new Dto(42)));
            assertEquals(42, restored.value);
        } finally {
            service.dispose();
        }
    }

    @Test
    public void trustedExplicitSerializerStillOverridesDefault() {
        SerializationConfig config = new SerializationConfig();
        config.getCompactSerializationConfig().addSerializer(new CompactSerializer<HeapData>() {
            @Override
            public HeapData read(CompactReader reader) {
                return new HeapData(new byte[0]);
            }

            @Override
            public void write(CompactWriter writer, HeapData object) {
                writer.writeInt32("marker", 1);
            }

            @Override
            public String getTypeName() {
                return "trusted-explicit-heap-data";
            }

            @Override
            public Class<HeapData> getCompactClass() {
                return HeapData.class;
            }
        });
        InternalSerializationService service = service(config);
        try {
            GenericRecord record = GenericRecordBuilder.compact("trusted-explicit-heap-data")
                    .setInt32("marker", 1).build();
            Object restored = service.toObject(service.toData(record));
            assertTrue(restored instanceof HeapData);
        } finally {
            service.dispose();
        }
    }

    public static class Dto {
        public int value;

        public Dto(int value) {
            this.value = value;
        }
    }

    public record ValueRecord(int value) {
    }
}
