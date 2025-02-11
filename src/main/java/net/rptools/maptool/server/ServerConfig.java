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
package net.rptools.maptool.server;

import javax.annotation.Nonnull;
import net.rptools.maptool.client.ui.startserverdialog.StartServerDialogPreferences;

public class ServerConfig {
  public static final int DEFAULT_PORT = 51234;

  private final int port;
  private final String hostPlayerId;
  private final String gmPassword;
  private final String playerPassword;
  private final String serverName;
  private final String hostName;
  private final boolean useEasyConnect;
  @Nonnull private final StartServerDialogPreferences.Transport transport;

  public ServerConfig(
      String hostPlayerId,
      String gmPassword,
      String playerPassword,
      int port,
      String serverName,
      String hostName,
      boolean useEasyConnect,
      @Nonnull StartServerDialogPreferences.Transport transport) {
    this.hostPlayerId = hostPlayerId;
    this.gmPassword = gmPassword;
    this.playerPassword = playerPassword;
    this.port = port;
    this.serverName = serverName;
    this.hostName = hostName;
    this.useEasyConnect = useEasyConnect;
    this.transport = transport;
  }

  public String getHostPlayerId() {
    return hostPlayerId;
  }

  public boolean isServerRegistered() {
    return serverName != null && !serverName.isEmpty();
  }

  public String getServerName() {
    return serverName;
  }

  public int getPort() {
    return port;
  }

  public String getGmPassword() {
    return gmPassword;
  }

  public String getPlayerPassword() {
    return playerPassword;
  }

  public String getHostName() {
    return hostName;
  }

  public boolean getUseEasyConnect() {
    return useEasyConnect;
  }

  public sealed interface Transport {
    record SSLSocket(int port) implements Transport {}

    record Socket(int port) implements Transport {}

    record WebRTC(String serverName) implements Transport {}
  }

  @Nonnull
  public Transport getTransport() {
    return switch (transport) {
      case StartServerDialogPreferences.Transport.SSL_SOCKET -> new Transport.SSLSocket(port);
      case StartServerDialogPreferences.Transport.SOCKET -> new Transport.Socket(port);
      case StartServerDialogPreferences.Transport.WEB_RTC -> new Transport.WebRTC(serverName);
    };
  }
}
