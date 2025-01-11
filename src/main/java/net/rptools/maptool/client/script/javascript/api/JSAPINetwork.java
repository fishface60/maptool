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

import java.net.InetAddress;
import java.util.stream.Collectors;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.script.javascript.JSArray;
import net.rptools.maptool.client.script.javascript.JSObject;
import net.rptools.maptool.client.script.javascript.JSPromise;
import net.rptools.maptool.client.script.javascript.JSScriptEngine;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.util.NetUtil;
import net.rptools.parser.ParserException;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class JSAPINetwork implements MapToolJSAPIInterface {
  @Override
  public String serializeToString() {
    return "MapTool.network";
  }

  @HostAccess.Export
  public Value getExternalAddress() throws ParserException {
    var functionName = "MapTool.network.getExternalAddress";
    if (!MapTool.isHostingServer()) {
      throw new ParserException(
          I18N.getText("macro.function.network.notHostingServer", functionName));
    }

    if (!JSScriptEngine.inTrustedContext()) {
      throw new ParserException(I18N.getText("macro.function.general.noPerm", functionName));
    }

    if (!AppPreferences.allowExternalMacroAccess.get()) {
      throw new ParserException(I18N.getText("macro.function.general.accessDenied", functionName));
    }

    return JSPromise.wrapPromise(
        NetUtil.getInstance()
            .getExternalAddress()
            .thenApply(addr -> addr.getHostAddress())
            .toCompletableFuture());
  }

  @HostAccess.Export
  public Value getLocalAddresses() throws ParserException {
    var functionName = "MapTool.network.getLocalAddresses";
    if (!MapTool.isHostingServer()) {
      throw new ParserException(
          I18N.getText("macro.function.network.notHostingServer", functionName));
    }

    if (!JSScriptEngine.inTrustedContext()) {
      throw new ParserException(I18N.getText("macro.function.general.noPerm", functionName));
    }

    if (!AppPreferences.allowExternalMacroAccess.get()) {
      throw new ParserException(I18N.getText("macro.function.general.accessDenied", functionName));
    }

    return JSPromise.wrapPromise(
        NetUtil.getInstance()
            .getLocalAddresses()
            .thenApply(
                localAddresses -> {
                  var ret = new JSObject();
                  ret.put(
                      "ipv4",
                      new JSArray(
                          localAddresses.ipv4().stream()
                              .map(InetAddress::getHostAddress)
                              .collect(Collectors.toList())));
                  ret.put(
                      "ipv6",
                      new JSArray(
                          localAddresses.ipv6().stream()
                              .map(InetAddress::getHostAddress)
                              .collect(Collectors.toList())));
                  return ret;
                }));
  }
}
