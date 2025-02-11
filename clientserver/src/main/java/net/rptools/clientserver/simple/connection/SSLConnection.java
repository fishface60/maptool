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
package net.rptools.clientserver.simple.connection;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.KeyExchangeAlgorithm;
import org.bouncycastle.tls.ServerOnlyTlsAuthentication;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCertificate;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;

public class SSLConnection extends AbstractConnection implements Connection {
  @Nonnull private static final Logger log = LogManager.getLogger(SSLConnection.class);

  @Nonnull private Supplier<X509TrustManager> x509TrustManagerSupplier;
  @Nonnull private final String id;
  @Nullable private SendThread send;
  @Nullable private ReceiveThread receive;
  @Nullable private Socket socket;
  @Nullable private TlsClientProtocol protocol;
  @Nullable private String hostName;
  private int port;

  private void setX509TrustManagerSupplier(
      @Nullable Supplier<X509TrustManager> x509TrustManagerSupplier) {
    if (x509TrustManagerSupplier == null) {
      x509TrustManagerSupplier =
          () -> {
            var algorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory trustManagerFactory;
            try {
              trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
              trustManagerFactory.init((KeyStore) null);
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
              throw new AssertionError(
                  "Default TrustManagerFactory construction should be infallible", e);
            }

            for (var trustManager : trustManagerFactory.getTrustManagers()) {
              if (trustManager instanceof X509TrustManager x509TrustManager) {
                return x509TrustManager;
              }
            }

            throw new AssertionError("the default trust manager factory should include X509");
          };
    }

    this.x509TrustManagerSupplier = x509TrustManagerSupplier;
  }

  /**
   * Create a Client SSL Connection to a given host and port that waits until open is called.
   *
   * @param trustManagerFactory Factory for managing whether a peer's certificates are trusted. If
   *     null uses the default certificate store.
   * @param id Identifier for logging purposes
   * @param hostName host to connect to
   * @param port positive non-zero port to connect to
   */
  public SSLConnection(
      @Nullable Supplier<X509TrustManager> x509TrustManagerSupplier,
      @Nonnull String id,
      @Nonnull String hostName,
      int port) {
    setX509TrustManagerSupplier(x509TrustManagerSupplier);
    this.id = id;
    this.hostName = hostName;
    this.port = port;
  }

  /**
   * Wrap an existing socket in a Client SSL Connection
   *
   * @param trustManagerFactory Factory for managing whether a peer's certificates are trusted. If
   *     null uses the default certificate store.
   * @param id Identifier for logging purposes
   * @param socket Socket to wrap
   * @throws IOException if an I/O error occurs when creating input/output streams from the socket,
   *     the socket is closed, the socket is not connected, or the socket input/output has been
   *     shutdown.
   */
  public SSLConnection(
      @Nullable Supplier<X509TrustManager> x509TrustManagerSupplier,
      @Nonnull String id,
      @Nonnull Socket socket)
      throws IOException {
    setX509TrustManagerSupplier(x509TrustManagerSupplier);
    this.id = id;
    initialize(socket);
  }

  @Override
  @Nonnull
  public String getId() {
    return id;
  }

  private void initialize(@Nonnull Socket socket) throws IOException {
    this.socket = socket;

    // Bouncy Castle cryptography primitives are used instead of the Java Cryptography API since its
    // TLS1.3 implementation uses a SHA256WITHRSAANDMGF1 verifier, and TLS 1.3 support is preferred
    // over strictly using JCA if we're already bringing in Bouncy Castle.
    //
    // jcaTlsCrypto can be used if SHA256WITHRSAANDMGF1 is implemented by the Java Cryptography API
    var bcTlsCrypto = new BcTlsCrypto();
    final var jcaTlsCrypto = new JcaTlsCryptoProvider().create(new SecureRandom());
    this.protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
    var client =
        new DefaultTlsClient(bcTlsCrypto) {
          public TlsAuthentication getAuthentication() throws IOException {
            return new ServerOnlyTlsAuthentication() {
              public void notifyServerCertificate(TlsServerCertificate serverCertificate)
                  throws IOException {
                Certificate certMsg;
                if (serverCertificate == null
                    || (certMsg = serverCertificate.getCertificate()) == null
                    || certMsg.isEmpty()) {
                  throw new TlsFatalAlert(AlertDescription.handshake_failure);
                }

                X509Certificate[] chain = new X509Certificate[certMsg.getLength()];
                for (int i = 0; i < chain.length; i++) {
                  chain[i] =
                      JcaTlsCertificate.convert(jcaTlsCrypto, certMsg.getCertificateAt(i))
                          .getX509Certificate();
                }

                var authType =
                    switch (context.getSecurityParametersHandshake().getKeyExchangeAlgorithm()) {
                      case KeyExchangeAlgorithm.DH_DSS -> "DH_DSS";
                      case KeyExchangeAlgorithm.DH_RSA -> "DH_RSA";
                      case KeyExchangeAlgorithm.DHE_DSS -> "DHE_DSS";
                      case KeyExchangeAlgorithm.DHE_RSA -> "DHE_RSA";
                      case KeyExchangeAlgorithm.ECDH_ECDSA -> "ECDH_ECDSA";
                      case KeyExchangeAlgorithm.ECDH_RSA -> "ECDH_RSA";
                      case KeyExchangeAlgorithm.ECDHE_ECDSA -> "ECDHE_ECDSA";
                      case KeyExchangeAlgorithm.ECDHE_RSA -> "ECDHE_RSA";
                      case KeyExchangeAlgorithm.NULL -> "UNKNOWN";
                      case KeyExchangeAlgorithm.RSA -> "KE:RSA";
                      case KeyExchangeAlgorithm.SRP_DSS -> "SRP_DSS";
                      case KeyExchangeAlgorithm.SRP_RSA -> "SRP_RSA";
                      default -> throw new IllegalArgumentException();
                    };

                // NOTE: JSSE Sockets check OSCP status here but MapTool won't use it
                // and implementations are moving away from it towards shorter cert validity.

                try {
                  var x509TrustManager = x509TrustManagerSupplier.get();
                  x509TrustManager.checkServerTrusted(chain, authType);
                } catch (CertificateException e) {
                  throw new IOException("Server certificate not trusted", e);
                }
              }
            };
          }
        };
    this.protocol.connect(client);

    this.send = new SendThread();
    this.receive = new ReceiveThread();

    this.send.start();
    this.receive.start();
  }

  /**
   * Start a connection by opening a socket with the previously defined host and port.
   *
   * @throws IOException if an I/O error occurs when creating input/output streams from the socket,
   *     the socket is closed, the socket is not connected, or the socket input/output has been
   *     shutdown.
   */
  @Override
  public void open() throws IOException {
    var socket = new Socket(hostName, port);
    initialize(socket);
  }

  @Override
  public void sendMessage(@Nullable Object channel, byte[] message) {
    addMessage(channel, message);
  }

  @Override
  protected void onClose() {
    receive.interrupt();
    send.interrupt();

    try {
      protocol.close();
    } catch (IOException e) {
      log.warn("Failed to close protocol", e);
    }

    try {
      socket.close();
    } catch (IOException e) {
      log.warn("Failed to close socket", e);
    }
  }

  @Override
  public boolean isAlive() {
    return !socket.isClosed();
  }

  @Override
  public String getError() {
    return null;
  }

  // /////////////////////////////////////////////////////////////////////////
  // send thread
  // /////////////////////////////////////////////////////////////////////////
  private class SendThread extends Thread {
    public SendThread() {
      setName("SSLConnection.SendThread");
    }

    @Override
    public void run() {
      try {
        final OutputStream out =
            new BufferedOutputStream(SSLConnection.this.protocol.getOutputStream());

        while (!SSLConnection.this.isClosed() && SSLConnection.this.isAlive()) {
          // Blocks for a time until a message is received.
          byte[] message = SSLConnection.this.nextMessage();
          if (message == null) {
            // No message available. Thread may also have been interrupted as part of stopping.
            continue;
          }

          try {
            SSLConnection.this.writeMessage(out, message);
          } catch (IOException e) {
            log.error("Error while writing message. Closing connection.", e);
            return;
          }
        }
      } finally {
        SSLConnection.this.close();
      }
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // receive thread
  // /////////////////////////////////////////////////////////////////////////
  private class ReceiveThread extends Thread {
    public ReceiveThread() {
      setName("SSLConnection.ReceiveThread");
    }

    @Override
    public void run() {
      try {
        final InputStream in = SSLConnection.this.protocol.getInputStream();

        while (!SSLConnection.this.isClosed() && SSLConnection.this.isAlive()) {
          try {
            byte[] message = SSLConnection.this.readMessage(in);
            SSLConnection.this.dispatchCompressedMessage(message);
          } catch (SocketTimeoutException e) {
            log.warn("Lost client {}", SSLConnection.this.getId(), e);
            return;
          } catch (IOException e) {
            log.error(e);
            return;
          } catch (Throwable t) {
            // don't let anything kill this thread via exception
            log.error("Unexpected error", t);
          }
        }
      } finally {
        SSLConnection.this.close();
        fireDisconnect();
      }
    }
  }
}
