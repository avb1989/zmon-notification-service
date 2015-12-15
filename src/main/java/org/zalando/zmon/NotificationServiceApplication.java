package org.zalando.zmon;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@RestController
@SpringBootApplication
public class NotificationServiceApplication {
    private static final String NOTIFICATION_DEVICES = "zmon:push:%s";
    private static final String NOTIFICATION_ALERTS = "zmon:alert:%d";

    @Value("${oauth.tokeninfo.url:}")
    String tokenInfoUrl;

    @Bean
    JedisPool getRedisPool(NotificationServiceConfig config) throws URISyntaxException {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        return new JedisPool(poolConfig, new URI(config.getRedisUri()));
    }

    @Bean
    TokenInfoService getTokenInfoService() {
        return new TokenInfoService(tokenInfoUrl);
    }

    // request payloads

    public static class DeviceRequestBody {
        public String registration_token;
    }

    public static class SubscriptionRequestBody {
        public int alert_id;
    }

    public static class PublishRequestBody {
        public int alert_id;
        public JsonNode data;
        public JsonNode notification;
    }

    @Autowired
    JedisPool jedisPool;

    @Autowired
    TokenInfoService tokenInfoService;

    // registering

    @RequestMapping(value = "/api/v1/device", method = RequestMethod.POST)
    public ResponseEntity<String> registerDevice(@RequestBody DeviceRequestBody body, @RequestHeader(value = "Authorization", required = false) String oauthHeader) {
        Optional<String> uid = tokenInfoService.lookupUid(oauthHeader);
        if (uid.isPresent()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String deviceToken = body.registration_token;
                String redisKey = String.format(NOTIFICATION_DEVICES, uid.get());
                jedis.sadd(redisKey, deviceToken);     // this redis set contains all the devices registered for a specific oauth uid
                return new ResponseEntity<>("", HttpStatus.OK);
            }

        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    @RequestMapping(value = "/api/v1/subscription", method = RequestMethod.POST)
    public ResponseEntity<String> registerSubscription(@RequestBody SubscriptionRequestBody body, @RequestHeader(value = "Authorization", required = false) String oauthHeader) {
        Optional<String> uid = tokenInfoService.lookupUid(oauthHeader);
        if (uid.isPresent()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String redisKey = String.format(NOTIFICATION_ALERTS, body.alert_id);
                jedis.sadd(redisKey, uid.get());     // this redis set contains all the users registered for a specific alert id
                return new ResponseEntity<>("", HttpStatus.OK);
            }
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }


    // publishing new alerts

    @RequestMapping(value = "/api/v1/publish", method = RequestMethod.POST)
    public void publishNotification(@RequestBody PublishRequestBody body) {
        try (Jedis jedis = jedisPool.getResource()) {
            String notificationKey = String.format(NOTIFICATION_ALERTS, body.alert_id);

            HashSet<String> devices = new HashSet<>();
            for (String uid : jedis.smembers(notificationKey)) {
                String deviceKey = String.format(NOTIFICATION_DEVICES, uid);
                devices.addAll(jedis.smembers(deviceKey));
            }

            // push to all devices
            // TODO
        }
    }


    // Unregistering

    @RequestMapping(value = "/api/v1/device", method = RequestMethod.DELETE)
    public ResponseEntity<String> unregisterDevice(@RequestBody DeviceRequestBody body, @RequestHeader(value = "Authorization", required = false) String oauthHeader) {
        Optional<String> uid = tokenInfoService.lookupUid(oauthHeader);
        if (uid.isPresent()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String deviceToken = body.registration_token;
                String redisKey = String.format("zmon:push:%s", uid.get());
                jedis.srem(redisKey, deviceToken); // remove device from user
                return new ResponseEntity<>("", HttpStatus.OK);
            }
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    @RequestMapping(value = "/api/v1/subscription", method = RequestMethod.DELETE)
    public ResponseEntity<String> unregisterSubscription(@RequestBody SubscriptionRequestBody body, @RequestHeader(value = "Authorization", required = false) String oauthHeader) {
        Optional<String> uid = tokenInfoService.lookupUid(oauthHeader);
        if (uid.isPresent()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String redisKey = String.format("zmon:alert:%d", body.alert_id);
                jedis.srem(redisKey, uid.get());
                return new ResponseEntity<>("", HttpStatus.OK);
            }
        }
        return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
    }


    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
