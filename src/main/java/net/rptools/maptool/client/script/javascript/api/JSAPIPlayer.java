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
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.player.Player;
import org.graalvm.polyglot.HostAccess;

public class JSAPIPlayer implements MapToolJSAPIInterface {
  @Override
  public String serializeToString() {
    return "MapTool.player";
  }

  @HostAccess.Export
  @Nonnull
  public String getLocalPlayerName() {
    return MapTool.getPlayer().getName();
  }

  @HostAccess.Export
  @Nonnull
  public List<String> getConnectedPlayerNames() {
    return MapTool.getPlayerList().stream()
        .map(player -> player.getName())
        .collect(Collectors.toList());
  }

  @Nullable
  private Player findPlayerByName(@Nonnull String playerName) {
    Player player = null;
    for (var p : MapTool.getPlayerList()) {
      if (p.getName().equalsIgnoreCase(playerName)) {
        player = p;
      }
    }
    return player;
  }

  /**
   * Get whether the named player is connected.
   *
   * @return true if the player is connected, false if not.
   */
  @HostAccess.Export
  public boolean isConnected(@Nonnull String playerName) {
    return findPlayerByName(playerName) != null;
  }

  /**
   * Get whether the named player is a Player.
   *
   * @return true if the player is connected and is a Player, false otherwise.
   */
  @HostAccess.Export
  public boolean isPlayer(@Nonnull String playerName) {
    var player = findPlayerByName(playerName);
    if (player == null) {
      return false;
    }
    return player.getRole() == Player.Role.PLAYER;
  }

  /**
   * Get whether the named player is a GM.
   *
   * @return true if the player is connected and is a GM, false otherwise.
   */
  @HostAccess.Export
  public boolean isGM(@Nonnull String playerName) {
    var player = findPlayerByName(playerName);
    if (player == null) {
      return false;
    }
    return player.isGM();
  }
}
