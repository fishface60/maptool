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
package net.rptools.maptool.client.script.javascript;

import java.util.concurrent.CompletableFuture;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

public class JSPromise {
  public static <T> Value wrapPromise(CompletableFuture<T> future) {
    var js = JSScriptEngine.getCurrentContext().context().getBindings("js");
    var promise = js.getMember("Promise");
    return promise.newInstance(
        (ProxyExecutable)
            arguments -> {
              var resolve = arguments[0];
              var reject = arguments[1];
              future.whenComplete(
                  (result, exception) -> {
                    if (result != null) {
                      resolve.execute(result);
                    } else {
                      reject.execute(exception);
                    }
                  });
              return null;
            });
  }
}
