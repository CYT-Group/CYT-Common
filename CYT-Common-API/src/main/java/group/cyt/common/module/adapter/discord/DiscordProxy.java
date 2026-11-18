package group.cyt.common.module.adapter.discord;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

/** Discord REST and Gateway share an optional, explicitly configured egress. */
public final class DiscordProxy {
  private DiscordProxy() {}

  public static Proxy from(GatewaySettings settings) {
    String value = settings.get("DISCORD_PROXY").strip();
    if (value.isEmpty()) return null;
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme();
      if (!("http".equals(scheme) || "socks5".equals(scheme))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
        throw new IllegalArgumentException();
      int port = uri.getPort() == -1 ? ("http".equals(scheme) ? 80 : 1080) : uri.getPort();
      if (port < 1 || port > 65535) throw new IllegalArgumentException();
      return new Proxy(
          "http".equals(scheme) ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
          new InetSocketAddress(uri.getHost(), port));
    } catch (IllegalArgumentException e) {
      throw GatewayException.config("DISCORD_PROXY 必须是有效的 HTTP 或 SOCKS5 代理地址");
    }
  }
}
