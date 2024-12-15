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
package net.rptools.maptool.client.script.javascript.api;

import java.util.*;
import javax.annotation.Nullable;
import net.rptools.maptool.client.MapTool;
import org.graalvm.polyglot.HostAccess;

public class JSAPIServerInfo implements MapToolJSAPIInterface {
  @Override
  public String serializeToString() {
    return "MapTool.serverInfo";
  }

  @HostAccess.Export
  public boolean isServer() {
    return MapTool.isServer();
  }

  @HostAccess.Export
  public boolean isHosting() {
    return MapTool.isHostingServer();
  }

  @HostAccess.Export
  public boolean isPersonal() {
    return MapTool.isPersonalServer();
  }

  /**
   * Get the name of the running server
   *
   * @return The name of the server, or null if there's no running server or it's not registered
   *     with the registry.
   */
  @HostAccess.Export
  @Nullable
  public String getServerName() {
    var server = MapTool.getServer();
    if (server == null) {
      return null;
    }

    if (!server.isServerRegistered()) {
      return null;
    }

    return server.getName();
  }
}
