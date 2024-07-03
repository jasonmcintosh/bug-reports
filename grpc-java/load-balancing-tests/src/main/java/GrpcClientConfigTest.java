/*
 * Copyright 2024 Harness Inc. All rights reserved.
 */

import static org.junit.Assert.*;

import com.google.gson.Gson;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This tests some basics around the GRPC config for clients INCLUDING
 * * Do retries WORK - this ACTUALLY starts up GRPC servers & sets up connections to them and makes requests!
 * * Does LOAD balancing work and route through the hosts.
 * * What about failure states - if a server is NOT "available" OR gets shutdown, does this HANDLE it
 *
 * TO NOTE:  This test uses the GRPC server to start processing/handle requests.  Other servers
 *  MAY NOT have the same behaviors!
 */

public class GrpcClientConfigTest  {
  static {
    NameResolverRegistry.getDefaultRegistry().register(new StaticResolverProvider());
    System.out.println("Starting test servers for GRPC Config verification");
  }

  private GrpcClientTestServer testServer1;
  private GrpcClientTestServer testServer2;

  private static class GrpcClientTestServer extends GrpcClientTestGrpc.GrpcClientTestImplBase {
    private final int serverNumber;
    boolean shouldFail = false;
    public GrpcClientTestServer(int serverNumber) {
      this.serverNumber = serverNumber;
    }

    @Override
    public void sayHello(GrpcClientRequest request, StreamObserver<GrpcClientResponse> responseObserver) {
      if (shouldFail) {
        responseObserver.onError(Status.UNAVAILABLE.withDescription("We are a failing server").asRuntimeException());
      }else {
        responseObserver.onNext(GrpcClientResponse.newBuilder()
                .setMessage("hello '" + request.getName() + "' from the server " + serverNumber + "!")
                .build());
        responseObserver.onCompleted();

      }
    }
  }

  private GrpcClientConfig config;
  Server server1;
  Server server2;
  @Before
  public void setup() throws Exception {
    testServer1 = new GrpcClientTestServer(1);
    testServer2 = new GrpcClientTestServer(2);
    server1 =
        Grpc.newServerBuilderForPort(9802, InsecureServerCredentials.create()).addService(testServer1).build().start();
    server2 =
        Grpc.newServerBuilderForPort(9803, InsecureServerCredentials.create()).addService(testServer2).build().start();
    config = new GrpcClientConfig("static:///127.0.0.1:9802,127.0.0.1:9803");
    Runtime.getRuntime().addShutdownHook(new Thread(this::stopServers));
  }
  @After
  public void stopServers() {
    // Use stderr here since the logger may have been reset by its JVM shutdown hook.
    System.err.println("*** shutting down gRPC server since JVM is shutting down");
    try {
      server1.shutdown().awaitTermination(30, TimeUnit.SECONDS);
      server2.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace(System.err);
    }
    System.err.println("*** server shut down");
  }
  GrpcClientRequest request = GrpcClientRequest.newBuilder().setName("I am a teapot!").build();

  @Test
  public void testLoadBalancerSet() {
    ManagedChannel channel = config.getChannelBuilder().build();
    GrpcClientTestGrpc.GrpcClientTestBlockingStub stub = GrpcClientTestGrpc.newBlockingStub(channel);
    List<String> responses = getSomeResponses(stub);
    // Test after - in theory EITHER host could get the request
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 1!"));
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 2!"));
  }

  @Test
  public void whenServerIsFailingWeShouldStillWork() {
    // We have two servers.. but set second to fail.  BOTH requests should hit server 1 then.
    testServer2.shouldFail = true;
    ManagedChannel channel = config.getChannelBuilder().build();
    GrpcClientTestGrpc.GrpcClientTestBlockingStub stub = GrpcClientTestGrpc.newBlockingStub(channel);
    List<String> responses = getSomeResponses(stub);
    // Test after - in theory EITHER host could get the request as they're "equal" leveled
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 1!"));
    // ONLY should have server 1!  Fail if we find any from server 2!
    assertFalse(responses.contains("hello 'I am a teapot!' from the server 2!"));
  }

  @Test
  public void serverDownShoudlStillWork() throws InterruptedException {
    // Lets see... if server isn't available thing, lets include a bad host/port in here as well.
    config = new GrpcClientConfig("static:///127.0.0.1:9802,127.0.0.1:9805,127.0.0.1:9803");
    // We have two servers.. but set second to fail.  BOTH requests should hit server 1 then.
    ManagedChannel channel = config.getChannelBuilder().build();
    GrpcClientTestGrpc.GrpcClientTestBlockingStub stub = GrpcClientTestGrpc.newBlockingStub(channel);
    List<String> responses = getSomeResponses(stub);
    // Test after - in theory EITHER host could get the request
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 1!"));
    // ONLY should have server 1!

    server1.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    responses = getSomeResponses(stub);
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 2!"));
    assertFalse(responses.contains("hello 'I am a teapot!' from the server 1!"));
  }

  @Test
  public void testFailureWhenNoRetrySetAndServerShutDown() throws InterruptedException {
    // Lets see... if server isn't available thing, lets include a bad host/port in here as well.
    config = new GrpcClientConfig("static:///127.0.0.1:9802,127.0.0.1:9805,127.0.0.1:9803", GrpcClientConfig.DEFAULT_AUTHORITY,new Gson().fromJson( """
         {
          "loadBalancingConfig" : [{"round_robin" : {}}]
          }
        """, Map.class));
    // We have two servers.. but set second to fail.  BOTH requests should hit server 1 then.
    ManagedChannel channel = config.getChannelBuilder().build();
    GrpcClientTestGrpc.GrpcClientTestBlockingStub stub = GrpcClientTestGrpc.newBlockingStub(channel);
    List<String> responses = getSomeResponses(stub);
    // Test after - in theory EITHER host could get the request
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 1!"));
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 2!"));
    server1.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    // Should not have requests to server 1 since we shut it down
    responses = getSomeResponses(stub);
    assertTrue(responses.contains("hello 'I am a teapot!' from the server 2!"));
    assertFalse(responses.contains("hello 'I am a teapot!' from the server 1!"));
  }


  private List<String> getSomeResponses(GrpcClientTestGrpc.GrpcClientTestBlockingStub stub) {
    List<String> responses = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      responses.add(stub.sayHello(request).getMessage());
    }
    return responses;
  }
}

