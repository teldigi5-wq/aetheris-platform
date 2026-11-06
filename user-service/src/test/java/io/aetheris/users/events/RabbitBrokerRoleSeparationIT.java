package io.aetheris.users.events;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This suite is intentionally run ONLY against a dedicated, ephemeral
 * RabbitMQ container provisioned by a GitHub Actions review workflow.
 * It is excluded from normal surefire naming conventions.
 */
class RabbitBrokerRoleSeparationIT {
    private static final String EXCHANGE = "aetheris.events";
    private static final String QUEUE = "aetheris.audit.user-events";
    private static final String VHOST = "xerj_review";

    private static String mandatory(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing synthetic RabbitMQ CI fixture: " + name);
        }
        return value;
    }

    private static ConnectionFactory factory(String username, String password, String vhost) {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost("127.0.0.1");
        f.setPort(55672);
        f.setUsername(username);
        f.setPassword(password);
        f.setVirtualHost(vhost);
        f.setConnectionTimeout(5000);
        f.setHandshakeTimeout(5000);
        return f;
    }

    @Test
    void realBrokerAllowsOnlyPublisherWriteAndConsumerRead() throws Exception {
        String adminSecret = mandatory("XERJ_TEST_RABBIT_PASSWORD");
        String publisherSecret = mandatory("XERJ_BROKER_PUBLISH_PASSWORD");
        String consumerSecret = mandatory("XERJ_BROKER_CONSUME_PASSWORD");
        assertNotEquals(adminSecret, publisherSecret);
        assertNotEquals(adminSecret, consumerSecret);
        assertNotEquals(publisherSecret, consumerSecret);

        // Topology is owned by a dedicated provisioning identity.
        try (Connection admin = factory("xerj_ci", adminSecret, VHOST).newConnection();
             Channel ch = admin.createChannel()) {
            ch.exchangeDeclare(EXCHANGE, "topic", true);
            ch.queueDeclare(QUEUE, true, false, false, null);
            ch.queueBind(QUEUE, EXCHANGE, "user.*");
        }

        final byte[] synthetic = "synthetic-acl-probe-only".getBytes(StandardCharsets.UTF_8);
        try (Connection producer = factory("xerj_publisher", publisherSecret, VHOST).newConnection();
             Channel ch = producer.createChannel()) {
            ch.confirmSelect();
            ch.basicPublish(EXCHANGE, "user.created",
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(), synthetic);
            ch.waitForConfirmsOrDie(5000);
        }

        try (Connection consumer = factory("xerj_consumer", consumerSecret, VHOST).newConnection();
             Channel ch = consumer.createChannel()) {
            GetResponse delivery = ch.basicGet(QUEUE, true);
            assertNotNull(delivery, "Authorized consumer must read the published event");
            assertArrayEquals(synthetic, delivery.getBody());
        }

        // Each negative uses a fresh channel because RabbitMQ closes channels
        // after an authorization error. A failed positive above blocks the test.
        assertThrows(Exception.class, () -> {
            try (Connection producer = factory("xerj_publisher", publisherSecret, VHOST).newConnection();
                 Channel channel = producer.createChannel()) {
                channel.basicGet(QUEUE, true);
            }
        }, "Publisher must be denied all Audit queue reads");

        assertThrows(Exception.class, () -> {
            try (Connection consumer = factory("xerj_consumer", consumerSecret, VHOST).newConnection();
                 Channel channel = consumer.createChannel()) {
                channel.confirmSelect();
                channel.basicPublish(EXCHANGE, "user.created", null, synthetic);
                channel.waitForConfirmsOrDie(5000);
            }
        }, "Consumer must be denied writes to the User event exchange");

        for (String username : new String[]{"xerj_publisher", "xerj_consumer"}) {
            String password = "xerj_publisher".equals(username) ? publisherSecret : consumerSecret;
            assertThrows(Exception.class, () -> {
                try (Connection connection = factory(username, password, VHOST).newConnection();
                     Channel channel = connection.createChannel()) {
                    channel.queueDeclare("synthetic-illicit-topology", false, false, true, null);
                }
            }, username + " must not declare a queue");
            assertThrows(Exception.class, () ->
                    factory(username, password, "/").newConnection(),
                    username + " must have no access to the shared default vhost");
        }
        System.out.println("XERJ_REAL_RABBITMQ_SEPARATE_VHOST_ROLE_ACL_PASS");
    }
}
