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
package net.rptools.maptool.util.cipher;

import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.PasswordAccessException;
import javax.annotation.Nonnull;
import net.rptools.maptool.client.AppUtil;
import net.rptools.maptool.util.PasswordGenerator;

public class Keyring {

  public static class UnavailableException extends Exception {
    UnavailableException(Throwable cause) {
      super("OS Keyring service not supported", cause);
    }
  }

  public static class PasswordUnreadableException extends Exception {
    PasswordUnreadableException(Throwable cause) {
      super("Password is unreadable", cause);
    }
  }

  public static class PasswordUnwritableException extends Exception {
    PasswordUnwritableException(Throwable cause) {
      super("Password is unwritable", cause);
    }
  }

  @Nonnull
  private static com.github.javakeyring.Keyring create() throws UnavailableException {
    try {
      return com.github.javakeyring.Keyring.create();
    } catch (BackendNotSupportedException e) {
      throw new Keyring.UnavailableException(e);
    }
  }

  /**
   * Read the Application password form the keyring
   *
   * @return the password as a char array
   * @throws Keyring.UnavailableException if the OS keyring is unusable
   * @throws Keyring.PasswordUnreadableException if the key is missing or unreadable
   */
  @Nonnull
  public static char[] readPassword() throws UnavailableException, PasswordUnreadableException {
    var keyring = create();
    var service = AppUtil.getKeyringId();
    String password;
    try {
      password = keyring.getPassword(service, "");
    } catch (NullPointerException | PasswordAccessException e) {
      // TODO: Depending on the error it may be preferable to prompt for the
      // password or to reinitialize the key store with a new password but
      // javakeyring doesn't raise granular enough exceptions so the best
      // option is to prompt the user with the error message to determine
      // what to do.
      throw new Keyring.PasswordUnreadableException(e);
    }

    return password.toCharArray();
  }

  /**
   * Generate a new Application password and store it in the keyring
   *
   * @return the password as a char array
   * @throws Keyring.UnavailableException if the OS keyring is unusable
   * @throws Keyring.PasswordUnwritableException if the key somehow unwritable
   */
  @Nonnull
  public static char[] initPassword() throws UnavailableException, PasswordUnwritableException {
    var keyring = create();
    var service = AppUtil.getKeyringId();
    String password = new PasswordGenerator().getPassword();
    try {
      keyring.setPassword(service, "", password);
    } catch (PasswordAccessException e) {
      // TODO: This can fail in a recoverable way if a keyring has invalid
      // values and the user can remove them or it could be malfunctioning in
      // a way that needs the user to set a password they can rememeber, or
      // they can fall back to insecure key files, but without the ability to
      // prompt we can only fall back to insecure key files.
      throw new Keyring.PasswordUnwritableException(e);
    }

    return password.toCharArray();
  }
}
