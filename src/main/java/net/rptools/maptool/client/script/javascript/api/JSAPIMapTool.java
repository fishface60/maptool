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

import net.rptools.maptool.client.script.javascript.*;
import org.graalvm.polyglot.*;

@MapToolJSAPIDefinition(javaScriptVariableName = "MapTool")
public class JSAPIMapTool implements MapToolJSAPIInterface {
  @Override
  public String serializeToString() {
    return "MapTool";
  }

  @HostAccess.Export public final JSAPIClientInfo clientInfo = new JSAPIClientInfo();

  @HostAccess.Export public final JSAPIChat chat = new JSAPIChat();

  @HostAccess.Export public final JSAPINetwork network = new JSAPINetwork();

  @HostAccess.Export public final JSAPIPlayer player = new JSAPIPlayer();

  @HostAccess.Export public final JSAPIServerInfo serverInfo = new JSAPIServerInfo();

  @HostAccess.Export public final JSAPITokens tokens = new JSAPITokens();
}
