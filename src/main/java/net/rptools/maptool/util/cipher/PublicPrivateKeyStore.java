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

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.NoSuchPaddingException;
import net.rptools.maptool.client.AppUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PublicPrivateKeyStore {

  private static final Logger log = LogManager.getLogger(PublicPrivateKeyStore.class);

  private static final File PUBLIC_KEY_FILE =
      AppUtil.getAppHome("config").toPath().resolve("public.key").toFile();
  private static final File PRIVATE_KEY_FILE =
      AppUtil.getAppHome("config").toPath().resolve("private.key").toFile();
  private static final File KEYSTORE_FILE =
      AppUtil.getAppHome("config").toPath().resolve("keystore.p12").toFile();
  private static final String KEYPAIR_ALIAS = "MapToolKeyPair";

  /**
   * Get the Application password, creating it if it does not exist.
   *
   * <p>This should be used to get the password for use with getKeyStore.
   *
   * @return an existing or newly created password
   * @throws Keyring.UnavailableException if the OS keyring is unusable
   * @throws Keyring.PasswordUnreadableException if the key is missing or unreadable
   * @throws Keyring.PasswordUnwritableException if the key somehow unwritable
   */
  @Nonnull
  public char[] getPassword()
      throws Keyring.UnavailableException,
          Keyring.PasswordUnreadableException,
          Keyring.PasswordUnwritableException {
    return KEYSTORE_FILE.exists() ? Keyring.readPassword() : Keyring.initPassword();
  }

  /**
   * Get the Application password, logging and returning null instead of exceptions.
   *
   * @return the password as a char array or null if the keyring is inaccessible.
   */
  @Nullable
  private char[] getKeyStorePassword() {
    try {
      return getPassword();
    } catch (Keyring.UnavailableException e) {
      log.warn("OS Keyring service not supported, falling back to password files", e);
      return null;
    } catch (Keyring.PasswordUnreadableException e) {
      log.warn(
          "Keystore {} exists but password unreadable from OS keyring, falling back to key files",
          KEYSTORE_FILE,
          e);
      return null;
    } catch (Keyring.PasswordUnwritableException e) {
      log.warn("Saving password to OS keyring failed, falling back to key files", e);
      return null;
    }
  }

  public static class KeyStoreUnreadableException extends Exception {
    KeyStoreUnreadableException(Throwable cause) {
      super("Key Store is unreadable", cause);
    }
  }

  @Nonnull
  private KeyStore createEmptyKeyStore(@Nonnull char[] password) {
    KeyStore ks;
    try {
      ks = KeyStore.getInstance("PKCS12");
    } catch (KeyStoreException e) {
      throw new AssertionError("PKCS12 should be a built-in KeyStore algorithm", e);
    }
    try {
      ks.load(null, password);
    } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new AssertionError("Loading an empty key store should be infallible", e);
    }
    return ks;
  }

  /**
   * Get an instance of the key store located in the config directory.
   *
   * <p>Creates an empty key store if it's missing so keys can be added later.
   *
   * @param password The password required to unlock the key store
   * @return The key store in the config directory or a new empty key store if it didn't exist.
   * @throws KeyStoreUnreadableException if the key store exists but can't be read
   */
  @Nonnull
  public KeyStore getKeyStore(@Nonnull char[] password) throws KeyStoreUnreadableException {
    if (!KEYSTORE_FILE.exists()) {
      return createEmptyKeyStore(password);
    }

    try {
      return KeyStore.getInstance(KEYSTORE_FILE, password);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new AssertionError(
          "A keystore loaded from a statically defined path should be infallible", e);
    } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new KeyStoreUnreadableException(e);
    }
  }

  /**
   * Get an instance of the key store located in the config directory.
   *
   * <p>Logs a warning if there is an unusable key store but creates an empty key store if it's
   * missing so keys can be added later.
   *
   * @param password The password required to open the key store
   * @return The key store in the config directory, a new empty key store if it didn't exist, or
   *     null if there's an unusuable key store.
   */
  @Nullable
  private KeyStore getKeyStoreOrNull(@Nonnull char[] password) {
    try {
      return getKeyStore(password);
    } catch (KeyStoreUnreadableException e) {
      log.warn("The key store {} is unusable, falling back to key files", KEYSTORE_FILE, e);
      return null;
    }
  }

  class UnreadableEntryException extends Exception {
    UnreadableEntryException(Throwable e) {
      super(e);
    }
  }

  class InappropriateEntryException extends Exception {
    InappropriateEntryException() {
      super("Not a key pair");
    }
  }

  @Nullable
  private KeyPair getKeyStoreKeys(@Nonnull KeyStore keyStore, @Nonnull char[] password)
      throws UnreadableEntryException, InappropriateEntryException {
    KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(password);
    KeyStore.Entry entry;
    try {
      entry = keyStore.getEntry(KEYPAIR_ALIAS, protParam);
    } catch (NullPointerException e) {
      throw new AssertionError("Statically defined key store aliases should be non-null", e);
    } catch (KeyStoreException e) {
      throw new AssertionError("getKeyStoreKeys should only be passed an initialized Key Store", e);
    } catch (NoSuchAlgorithmException | UnrecoverableEntryException e) {
      throw new UnreadableEntryException(e);
    }

    if (entry == null) {
      if (KEYSTORE_FILE.exists()) {
        log.warn(
            "Keystore {} exists but is missing alias {}, falling back to key files.",
            KEYSTORE_FILE,
            KEYPAIR_ALIAS);
      }
      return null;
    }

    if (!(entry instanceof KeyStore.PrivateKeyEntry pke)) {
      throw new InappropriateEntryException();
    }

    return new KeyPair(pke.getCertificate().getPublicKey(), pke.getPrivateKey());
  }

  /**
   * Returns the public and private keys for this client. If none exists it will attempt to create
   * them and save them to the key files.
   *
   * @return the keys.
   */
  public CompletableFuture<CipherUtil.Key> getKeys() {

    return CompletableFuture.supplyAsync(
        () -> {
          try {
            var keystorePassword = getKeyStorePassword();
            var keyStore = keystorePassword == null ? null : getKeyStoreOrNull(keystorePassword);

            KeyPair keyStorePair;
            try {
              keyStorePair = keyStore == null ? null : getKeyStoreKeys(keyStore, keystorePassword);
            } catch (UnreadableEntryException | InappropriateEntryException e) {
              log.warn(
                  "Keystore {} has an unreadable entry for {}, falling back to key files",
                  KEYSTORE_FILE,
                  KEYPAIR_ALIAS,
                  e);
              keyStorePair = null;
              // Treat a key store with unreadable keys as unusable.
              keyStore = null;
            }

            if (keyStorePair != null) {
              return CipherUtil.fromPublicPrivatePair(
                  keyStorePair.getPublic(), keyStorePair.getPrivate());
            }

            log.debug("Falling back to key files");

            CipherUtil.Key keys;
            if (!PUBLIC_KEY_FILE.exists() || !PRIVATE_KEY_FILE.exists()) {
              var keyPair = CipherUtil.generateKeyPair();
              CipherUtil.writeKeyPair(keyPair, PUBLIC_KEY_FILE, PRIVATE_KEY_FILE);
              keys = CipherUtil.fromPublicPrivatePair(keyPair.getPublic(), keyPair.getPrivate());
            } else {
              keys = CipherUtil.fromPublicPrivatePair(PUBLIC_KEY_FILE, PRIVATE_KEY_FILE);
            }

            return keys;
          } catch (NoSuchAlgorithmException
              | IOException
              | InvalidAlgorithmParameterException
              | InvalidKeySpecException
              | NoSuchPaddingException
              | InvalidKeyException e) {
            throw new CompletionException(e);
          }
        });
  }

  /**
   * Regenerates and returns a new public / private key pair. This will also save the new keys to
   * the key files.
   *
   * @return the newly generated keys.
   */
  public CompletableFuture<CipherUtil.Key> regenerateKeys() {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            KeyPair keyPair = CipherUtil.generateKeyPair();
            CipherUtil.writeKeyPair(keyPair, PUBLIC_KEY_FILE, PRIVATE_KEY_FILE);

            return CipherUtil.fromPublicPrivatePair(PUBLIC_KEY_FILE, PRIVATE_KEY_FILE);
          } catch (IOException
              | NoSuchAlgorithmException
              | InvalidAlgorithmParameterException
              | InvalidKeySpecException
              | NoSuchPaddingException
              | InvalidKeyException e) {
            throw new CompletionException(e);
          }
        });
  }
}
