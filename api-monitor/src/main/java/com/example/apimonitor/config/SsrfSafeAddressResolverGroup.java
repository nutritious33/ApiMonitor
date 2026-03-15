package com.example.apimonitor.config;
 
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
 
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
 
/**
 * Netty {@link AddressResolverGroup} that validates every IP address returned
 * by DNS resolution against the same private-range blocklist used by
 * {@link HealthCheckService#validateUrl}, closing the DNS-rebinding TOCTOU
 * window between validateUrl() and the actual WebClient connection.
 *
 * <h3>Why this is necessary</h3>
 * {@code validateUrl()} resolves the hostname and checks that no returned IP
 * is private. However, WebClient resolves the hostname a second time (via
 * Netty's own DNS stack) when it opens the TCP connection. Between these two
 * resolutions the domain owner could update the DNS record to point at a
 * private address (DNS rebinding). This resolver ensures that the IP Netty
 * actually connects to is also validated, eliminating the gap.
 *
 * <h3>Blocking DNS note</h3>
 * {@link InetAddress#getAllByName} blocks the calling thread. This is
 * acceptable only for a low-throughput self-hosted application where concurrent
 * health checks are small and bounded. A high-throughput service should use
 * Reactor Netty's async DNS resolver and apply validation in a reactive pipeline.
 */
final class SsrfSafeAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
 
    static final SsrfSafeAddressResolverGroup INSTANCE = new SsrfSafeAddressResolverGroup();
 
    private SsrfSafeAddressResolverGroup() {}
 
    /**
     * Netty's abstract method signature uses {@link EventExecutor}, not
     * {@code EventLoop}. An {@code @Override} against the wrong type silently
     * creates an overload instead of an override, causing a compilation error.
     */
    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new SsrfSafeResolver(executor);
    }
 
    // ── Inner resolver ────────────────────────────────────────────────────────
 
    private static final class SsrfSafeResolver extends AbstractAddressResolver<InetSocketAddress> {
 
        SsrfSafeResolver(EventExecutor executor) {
            super(executor, InetSocketAddress.class);
        }
 
        @Override
        protected boolean doIsResolved(InetSocketAddress address) {
            return !address.isUnresolved();
        }
 
        @Override
        protected void doResolve(InetSocketAddress unresolvedAddress,
                                 Promise<InetSocketAddress> promise) {
            try {
                InetAddress[] resolved = resolveAndValidate(unresolvedAddress.getHostString());
                promise.setSuccess(new InetSocketAddress(resolved[0], unresolvedAddress.getPort()));
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }
 
        @Override
        protected void doResolveAll(InetSocketAddress unresolvedAddress,
                                    Promise<List<InetSocketAddress>> promise) {
            try {
                InetAddress[] resolved = resolveAndValidate(unresolvedAddress.getHostString());
                List<InetSocketAddress> result = new ArrayList<>(resolved.length);
                for (InetAddress addr : resolved) {
                    result.add(new InetSocketAddress(addr, unresolvedAddress.getPort()));
                }
                promise.setSuccess(result);
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }
 
        /**
         * Resolves {@code host} and validates every returned address against the
         * SSRF blocklist. Throws if any address is private, loopback, link-local,
         * or the all-zeros wildcard address.
         */
        private static InetAddress[] resolveAndValidate(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivate(addr)) {
                    throw new SecurityException(
                            "SSRF: resolved address " + addr.getHostAddress() +
                            " for host '" + host + "' is a private/internal address");
                }
            }
            return addresses;
        }
 
        /**
         * Returns {@code true} when the address falls into a range that must not
         * be reached by outbound health-check requests. Mirrors the InetAddress
         * checks in {@link HealthCheckService#validateUrl}.
         */
        private static boolean isPrivate(InetAddress addr) {
            return addr.isLoopbackAddress()    // 127.x.x.x / ::1
                || addr.isSiteLocalAddress()   // 10.x / 172.16–31.x / 192.168.x / fc00::/7
                || addr.isLinkLocalAddress()   // 169.254.x (cloud metadata) / fe80::/10
                || addr.isAnyLocalAddress();   // 0.0.0.0
        }
    }
}
 