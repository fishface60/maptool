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
package net.rptools.maptool.client.functions;

import com.google.gson.Gson;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.*;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ui.token.*;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.maptool.util.NetUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

public class NetworkFunctions extends AbstractFunction {

  private static final NetworkFunctions instance = new NetworkFunctions();

  private NetworkFunctions() {
    super(0, 0, false, "network.getExternalAddress", "network.getLocalAddresses");
  }

  public static NetworkFunctions getInstance() {
    return instance;
  }

  @Override
  public Object childEvaluate(
      Parser parser, VariableResolver resolver, String functionName, List<Object> param)
      throws ParserException {

    if (!MapTool.isHostingServer()) {
      throw new ParserException(
          I18N.getText("macro.function.network.notHostingServer", functionName));
    }

    FunctionUtil.blockUntrustedMacro(functionName);

    if (!AppPreferences.allowExternalMacroAccess.get()) {
      throw new ParserException(I18N.getText("macro.function.general.accessDenied", functionName));
    }

    var gson = new Gson();
    try {
      return switch (functionName) {
        case "network.getExternalAddress" ->
            gson.toJsonTree(NetUtil.getInstance().getExternalAddress().get(2, TimeUnit.SECONDS));
        case "network.getLocalAddresses" ->
            gson.toJsonTree(NetUtil.getInstance().getLocalAddresses().get(2, TimeUnit.SECONDS));
        default -> throw new ParserException("Unknown function name: " + functionName);
      };
    } catch (ExecutionException | InterruptedException e) {
      throw new ParserException(e.getMessage());
    } catch (TimeoutException e) {
      throw new ParserException(I18N.getText("macro.function.network.timeOut", functionName, 2));
    }
  }
}
