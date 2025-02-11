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
package net.rptools.maptool.client;

import java.net.InetAddress;
import javax.annotation.Nonnull;
import net.tsc.servicediscovery.AnnouncementListener;
import net.tsc.servicediscovery.ServiceFinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstraction over net.tsc.servicediscovery.ServiceFinder to hide MapTool-specific implementation
 * details.
 */
public class MapToolServiceFinder {
  private static final Logger log = LogManager.getLogger(MapToolServiceFinder.class);

  public interface MapToolAnnouncementListener extends AnnouncementListener {
    public void serviceAnnouncement(@Nonnull String id, @Nonnull RemoteServerConfig config);

    default void serviceAnnouncement(
        @Nonnull String type, @Nonnull InetAddress address, int port, @Nonnull byte[] data) {
      var id = new String(data);
      var address = address.getHostAddress();
      if (type.equals(AppConstants.SERVICE_GROUP_TCP.getType())) {
        serviceAnnouncement(id, new RemoteServerConfig.Socket(address, port));
      } else if (type.equals(AppConstants.SERVICE_GROUP_SSL.getType())) {
        serviceAnnouncement(id, new RemoteServerConfig.SSLSocket(address, port));
      } else {
        // Should be unreachable since the finder checks the type matches first.
        throw new AssertionError(type);
      }
    }
  }

  @Nonnull private static MapToolServiceFinder instance = new MapToolServiceFinder();

  @Nonnull
  public static MapToolServiceFinder getInstance() {
    return instance;
  }

  @Nonnull private ServiceFinder sslFinder;
  @Nonnull private ServiceFinder tcpFinder;

  public MapToolServiceFinder() {
    sslFinder = new ServiceFinder(AppConstants.SERVICE_GROUP_SSL);
    tcpFinder = new ServiceFinder(AppConstants.SERVICE_GROUP_TCP);
  }

  public void addAnnouncementListener(@Nonnull MapToolAnnouncementListener listener) {
    sslFinder.addAnnouncementListener(listener);
    tcpFinder.addAnnouncementListener(listener);
  }

  public void removeAnnouncementListener(@Nonnull MapToolAnnouncementListener listener) {
    sslFinder.removeAnnouncementListener(listener);
    tcpFinder.removeAnnouncementListener(listener);
  }

  public void find() {
    sslFinder.find();
    tcpFinder.find();
  }
}
