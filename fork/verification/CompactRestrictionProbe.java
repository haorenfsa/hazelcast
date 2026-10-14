package forkverification;

import com.hazelcast.config.SerializationConfig;
import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.serialization.impl.compact.Schema;
import com.hazelcast.internal.serialization.impl.compact.SchemaService;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import java.util.HashMap;
import java.util.Map;

/** Safe regression probe: materialization only; never calls methods on reconstructed internal objects. */
public class CompactRestrictionProbe {
    public static void main(String[] args) {
        Map<Long, Schema> schemas = new HashMap<>();
        InternalSerializationService service = new DefaultSerializationServiceBuilder()
                .setConfig(new SerializationConfig()).setSchemaService(new SchemaService() {
                    public Schema get(long id) { return schemas.get(id); }
                    public void put(Schema s) { schemas.put(s.getSchemaId(), s); }
                    public void putLocal(Schema s) { put(s); }
                }).build();
        String[] types = {"com.hazelcast.internal.serialization.impl.HeapData",
                "com.hazelcast.internal.nio.Packet",
                "com.hazelcast.internal.serialization.impl.UnsafeObjectDataInput"};
        int materialized = 0;
        for (String type : types) {
            GenericRecord record = GenericRecordBuilder.compact(type).build();
            Object result;
            try {
                result = service.toObject(service.toData(record));
            } catch (RuntimeException e) {
                if (args.length > 0 && args[0].equals("vulnerable")
                        && type.endsWith("UnsafeObjectDataInput")) {
                    System.out.println(type + " -> rejected later by field serializer validation");
                    continue;
                }
                throw e;
            }
            boolean safe = result instanceof GenericRecord;
            System.out.println(type + " -> " + result.getClass().getName() + " safe=" + safe);
            if (!safe) { materialized++; }
        }
        service.dispose();
        if (args.length > 0 && args[0].equals("safe") && materialized != 0) {
            throw new AssertionError("Internal classes materialized: " + materialized);
        }
        if (args.length > 0 && args[0].equals("vulnerable") && materialized != 2) {
            throw new AssertionError("Expected original materialization behavior");
        }
    }
}
