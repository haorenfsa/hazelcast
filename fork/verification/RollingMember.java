package forkverification;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback-only member used by verify-rolling.py. */
public class RollingMember {
    public static void main(String[] args) throws Exception {
        Config config = new Config();
        config.setClusterName(args[1]);
        config.getJetConfig().setEnabled(false);
        config.setProperty("hazelcast.logging.type", "none");
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.partition.count", "17");
        config.setProperty("hazelcast.operation.thread.count", "2");
        config.setProperty("hazelcast.operation.generic.thread.count", "2");
        config.setProperty("hazelcast.io.input.thread.count", "1");
        config.setProperty("hazelcast.io.output.thread.count", "1");
        config.getNetworkConfig().setPort(Integer.parseInt(args[0])).setPortAutoIncrement(false);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true)
                .addMember("127.0.0.1:15971").addMember("127.0.0.1:15972").addMember("127.0.0.1:15973");
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
        AtomicInteger received = new AtomicInteger();
        ITopic<String> topic = hz.getTopic("backport-notifications");
        topic.addMessageListener(message -> received.incrementAndGet());
        System.out.println("RESULT ready");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = in.readLine()) != null) {
                switch (line) {
                    case "size": System.out.println("RESULT " + hz.getCluster().getMembers().size()); break;
                    case "claim": System.out.println("RESULT " + hz.getMap("locks").putIfAbsent("key", "owner")); break;
                    case "read": System.out.println("RESULT " + hz.getMap("locks").get("key")); break;
                    case "publish": topic.publish("invalidate"); System.out.println("RESULT sent"); break;
                    case "received": System.out.println("RESULT " + received.get()); break;
                    case "quit": hz.shutdown(); System.out.println("RESULT stopped"); return;
                    default: throw new IllegalArgumentException(line);
                }
            }
        } finally {
            hz.shutdown();
        }
    }
}
