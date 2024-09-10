## WORK IN PROGRESS

Example tests to try to handle a situation like:

```
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
	at io.grpc.stub.ClientCalls.toStatusRuntimeException(ClientCalls.java:268)
	at io.grpc.stub.ClientCalls.getUnchecked(ClientCalls.java:249)
	at io.grpc.stub.ClientCalls.blockingUnaryCall(ClientCalls.java:167)
...
	at net.jodah.failsafe.FailsafeExecutor.call(FailsafeExecutor.java:379)
	at net.jodah.failsafe.FailsafeExecutor.get(FailsafeExecutor.java:70)
.....

Caused by: io.grpc.netty.shaded.io.netty.channel.AbstractChannel$AnnotatedConnectException: finishConnect(..) failed: Connection refused: internal-service-headless/10.36.29.215:12011
Caused by: java.net.ConnectException: finishConnect(..) failed: Connection refused
	at io.grpc.netty.shaded.io.netty.channel.unix.Errors.newConnectException0(Errors.java:166)
	at io.grpc.netty.shaded.io.netty.channel.unix.Errors.handleConnectErrno(Errors.java:131)
	at io.grpc.netty.shaded.io.netty.channel.unix.Socket.finishConnect(Socket.java:359)
	at io.grpc.netty.shaded.io.netty.channel.epoll.AbstractEpollChannel$AbstractEpollUnsafe.doFinishConnect(AbstractEpollChannel.java:710)
	at io.grpc.netty.shaded.io.netty.channel.epoll.AbstractEpollChannel$AbstractEpollUnsafe.finishConnect(AbstractEpollChannel.java:687)
	at io.grpc.netty.shaded.io.netty.channel.epoll.AbstractEpollChannel$AbstractEpollUnsafe.epollOutReady(AbstractEpollChannel.java:567)
	at io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoop.processReady(EpollEventLoop.java:499)
	at io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoop.run(EpollEventLoop.java:407)
	at io.grpc.netty.shaded.io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at io.grpc.netty.shaded.io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at io.grpc.netty.shaded.io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:833)

```

Current thinking is this is NOT tied to GRPC failures, but documenting tests here on how to test GRPC server & Load balancing config.
