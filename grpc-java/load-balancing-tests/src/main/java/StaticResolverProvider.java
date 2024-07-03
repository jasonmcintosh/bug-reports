import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

// Borrowed from https://github.com/grpc/grpc-java/blob/master/netty/src/main/java/io/grpc/netty/UdsNameResolver.java
// and https://stackoverflow.com/questions/73859400/how-to-configure-client-side-load-balancing-with-grpc-java
public class StaticResolverProvider extends NameResolverProvider {
  private static final String SCHEME = "static";

  @Override
  protected boolean isAvailable() {
    return true;
  }

  @Override
  protected int priority() {
    return 0;
  }

  @Override
  public NameResolver newNameResolver(URI uri, NameResolver.Args args) {
    System.err.println("Testing new STatic resolver provider setup!");
    if (SCHEME.equals(uri.getScheme())) {
      return new StaticResolver(uri);
    }
    return null;
  }

  @Override
  public String getDefaultScheme() {
    return SCHEME;
  }

  private static class StaticResolver extends NameResolver {
    String[] hostsAndPorts;
    private NameResolver.Listener2 listener;

    public StaticResolver(URI uri) {
      hostsAndPorts = StringUtils.substringAfter(uri.getPath(), "/").split(",");
      System.err.println("Hosts/ports are " + StringUtils.join(hostsAndPorts, ",") + ", length" + hostsAndPorts.length);
    }

    @Override
    public String getServiceAuthority() {
      return "";
    }

    @Override
    public void shutdown() {}

    @Override
    public void start(Listener2 listener) {
      Preconditions.checkState(this.listener == null, "already started");
      this.listener = checkNotNull(listener, "listener");
      setupAddresses();
    }
    @Override
    public void refresh() {}

    private void setupAddresses() {
      ResolutionResult.Builder resolutionResultBuilder = ResolutionResult.newBuilder();
      List<EquivalentAddressGroup> servers = new ArrayList<>();
      Arrays.stream(hostsAndPorts).forEach(each -> {
        String[] hostAndPort = each.split(":");
        servers.add(
            new EquivalentAddressGroup(new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1]))));
      });
      resolutionResultBuilder.setAddresses(Collections.unmodifiableList(servers));
      listener.onResult(resolutionResultBuilder.build());
    }
  }
}

