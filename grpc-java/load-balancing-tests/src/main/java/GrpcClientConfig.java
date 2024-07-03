/*
 * Copyright 2021 Harness Inc. All rights reserved.
 */

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NonFinal
@Slf4j
public class GrpcClientConfig {
  public static final String DEFAULT_AUTHORITY = "default-authority.example.com";
  public static final String DEFAULT_TARGET = "default-target.example.com";

  // Note that the "loadBalancingPolicy" is deprecated. See this doc for more examples of this config
  // https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/internal/ServiceConfigUtil.java#L271
  //
  // See this for the method config for retry:
  // https://github.com/grpc/grpc-java/blob/master/core/src/test/resources/io/grpc/internal/test_retry_service_config.json
  //
  // This sets up the DEFAULT method config for transparent retries when a downstream service is unavailable (aka
  // disconnected state).
  //
  public static final Map<String, ?> DEFAULT_SERVICE_CONFIG = new Gson().fromJson("""
          {
    "loadBalancingConfig" : [{"round_robin" : {}}],
    "methodConfig" : [{
      "name" : [{}],
      "waitForReady" : true,
      "retryPolicy" : {
        "maxAttempts" : 5,
        "initialBackoff" : "0.1s",
        "maxBackoff" : "1s",
        "backoffMultiplier" : 2,
        "retryableStatusCodes" : ["UNAVAILABLE", "UNKNOWN" ]
      }
    }]
          }
          """, Map.class);

  String target;
  String authority;
  Map<String, ?> serviceConfig;

  // default constructor
  public GrpcClientConfig() {
    this(DEFAULT_TARGET, DEFAULT_AUTHORITY);
  }

  // default constructor
  public GrpcClientConfig(String target) {
    this(target, DEFAULT_AUTHORITY);
  }

  public GrpcClientConfig(String target, String authority) {
    this(target, authority, DEFAULT_SERVICE_CONFIG);
  }
  public GrpcClientConfig(String target, String authority, String serviceConfig) {
    this(target, authority, new Gson().fromJson(serviceConfig, Map.class));
  }
  public GrpcClientConfig(String target, String authority, Map<String, ?> serviceConfig) {
    this.target = target;
    this.authority = authority;
    this.serviceConfig = serviceConfig;
  }


  public ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> getChannelBuilder() {
    return NettyChannelBuilder.forTarget(getTarget())
        .overrideAuthority(computeAuthority(getAuthority()))
        .usePlaintext()
        .defaultServiceConfig(serviceConfig)
        .maxInboundMessageSize(GRPC_MAXIMUM_MESSAGE_SIZE);
  }


  private static String computeAuthority(final String authority) {
    if (!isValidAuthority(authority)) {
      log.info("Authority in config {} is invalid. Using default value {}", authority, DEFAULT_AUTHORITY);
      return DEFAULT_AUTHORITY;
    } else {
      log.info("Using non-versioned authority {}", authority);
      return authority;
    }
  }

  private static boolean isValidAuthority(final String authority) {
    try {
      GrpcUtil.checkAuthority(authority);
      return true;
    } catch (Exception e) {
      log.error("Exception occurred when checking for valid authority", e);
      return false;
    }
  }
}

