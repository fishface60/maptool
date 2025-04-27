/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.clientserver;

import java.awt.EventQueue;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import net.rptools.clientserver.simple.connection.Connection;
import net.rptools.clientserver.simple.connection.SSLConnection;
import net.rptools.clientserver.simple.connection.SocketConnection;
import net.rptools.clientserver.simple.connection.WebRTCConnection;
import net.rptools.clientserver.simple.server.NilServer;
import net.rptools.clientserver.simple.server.Server;
import net.rptools.clientserver.simple.server.SocketServer;
import net.rptools.clientserver.simple.server.WebRTCServer;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.RemoteServerConfig;
import net.rptools.maptool.server.ServerConfig;
import net.rptools.maptool.util.cipher.Keyring;
import net.rptools.maptool.util.cipher.PublicPrivateKeyStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectionFactory {
  @Nonnull private static final Logger log = LogManager.getLogger(ConnectionFactory.class);

  private static ConnectionFactory instance = new ConnectionFactory();

  public static ConnectionFactory getInstance() {
    return instance;
  }

  @Nonnull
  private Supplier<X509TrustManager> getX509TrustManagerSupplier() {
    return () -> {
      var algorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory defaultTrustManagerFactory;
      try {
        defaultTrustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        defaultTrustManagerFactory.init((KeyStore) null);
      } catch (NoSuchAlgorithmException | KeyStoreException e) {
        throw new AssertionError(
            "Default TrustManagerFactory construction should be infallible", e);
      }

      X509TrustManager defaultTrustManager = null;
      for (var trustManager : defaultTrustManagerFactory.getTrustManagers()) {
        if (trustManager instanceof X509TrustManager x509TrustManager) {
          defaultTrustManager = x509TrustManager;
          break;
        }
      }
      assert defaultTrustManager != null : "the default trust manager factory should include X509";

      var store = new PublicPrivateKeyStore();
      char[] applicationPassword = null;
      try {
        applicationPassword = store.getPassword();
      } catch (Keyring.UnavailableException e) {
        log.warn("OS Keyring service not supported, application trust store not available", e);
      } catch (Keyring.PasswordUnreadableException e) {
        log.warn("Truststore available but password unreadable", e);
      } catch (Keyring.PasswordUnwritableException e) {
        log.warn("Saving password to OS keyring failed, application trust store not available", e);
      }

      KeyStore applicationTrustStore = null;
      if (applicationPassword != null) {
        try {
          applicationTrustStore = store.getTrustStore(applicationPassword);
        } catch (PublicPrivateKeyStore.KeyStoreUnreadableException e) {
          log.warn("The trust store is unusable", e);
        }
      }

      TrustManagerFactory applicationTrustManagerFactory = null;
      if (applicationTrustStore != null) {
        try {
          applicationTrustManagerFactory = TrustManagerFactory.getInstance(algorithm);
          applicationTrustManagerFactory.init(applicationTrustStore);
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
          log.warn(
              "Trust store not usable as trust manager, application trust store not available", e);
          applicationTrustManagerFactory = null;
        }
      }

      X509TrustManager applicationTrustManager = null;
      for (var trustManager : applicationTrustManagerFactory.getTrustManagers()) {
        if (trustManager instanceof X509TrustManager x509TrustManager) {
          applicationTrustManager = x509TrustManager;
          break;
        }
      }
      assert applicationTrustManager != null
          : "the application trust manager factory should include X509";

      final var finalDefaultTM = defaultTrustManager;
      final var finalAppTM = applicationTrustManager;
      return new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return Stream.concat(
                  Arrays.stream(finalDefaultTM.getAcceptedIssuers()),
                  Arrays.stream(finalAppTM.getAcceptedIssuers()))
              .toArray(X509Certificate[]::new);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          try {
            finalAppTM.checkServerTrusted(chain, authType);
          } catch (CertificateException e) {
            finalDefaultTM.checkServerTrusted(chain, authType);
          }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          finalDefaultTM.checkClientTrusted(getAcceptedIssuers(), authType);
        }
      };
    };
  }

  @Nonnull
  public Connection createConnection(@Nonnull String id, @Nonnull RemoteServerConfig config) {
    return switch (config) {
      case RemoteServerConfig.SSLSocket(String hostName, int port) ->
          new SSLConnection(getX509TrustManagerSupplier(), id, hostName, port);
      case RemoteServerConfig.Socket(String hostName, int port) ->
          new SocketConnection(id, hostName, port);
      case RemoteServerConfig.WebRTC(String serverName) ->
          new WebRTCConnection(
              id,
              serverName,
              new WebRTCConnection.Listener() {
                @Override
                public void onLoginError() {
                  MapTool.showError("Handshake.msg.playerAlreadyConnected");
                }
              });
    };
  }

  @Nonnull
  public Server createServer(@Nullable ServerConfig config) {
    if (config == null) {
      return new NilServer();
    }

    if (!config.getUseWebRTC()) {
      return new SocketServer(config.getPort());
    }

    return new WebRTCServer(
        config.getServerName(),
        new WebRTCServer.Listener() {
          @Override
          public void onLoginError() {
            EventQueue.invokeLater(
                () -> {
                  MapTool.showError("ServerDialog.error.serverAlreadyExists");
                });
          }

          @Override
          public void onUnexpectedClose() {
            MapTool.stopServer();
          }
        });
  }
}
