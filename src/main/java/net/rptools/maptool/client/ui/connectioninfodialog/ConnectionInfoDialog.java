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
package net.rptools.maptool.client.ui.connectioninfodialog;

import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.ServerAddress;
import net.rptools.maptool.client.swing.AbeillePanel;
import net.rptools.maptool.client.swing.SwingUtil;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.server.MapToolServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectionInfoDialog extends JDialog {
  private static JTextField externalAddressLabel;

  private static final Logger log = LogManager.getLogger(ConnectionInfoDialog.class);

  /**
   * This is the default constructor
   *
   * @param server the server instance for the connection dialog
   */
  public ConnectionInfoDialog(MapToolServer server) {
    super(MapTool.getFrame(), I18N.getText("ConnectionInfoDialog.title"), true);

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(275, 275);

    AbeillePanel panel = new AbeillePanel(new ConnectionInfoDialogView().getRootComponent());

    JTextField nameLabel = panel.getTextField("name");
    JTextField serviceIdentifierLabel = panel.getTextField("serviceIdentifier");
    JTextField localv4AddressLabel = panel.getTextField("localv4Address");
    JTextField localv6AddressLabel = panel.getTextField("localv6Address");
    JTextField portLabel = panel.getTextField("port");
    externalAddressLabel = panel.getTextField("externalAddress");

    String name = server.getName();
    if (name == null || name.isEmpty()) {
      name = "---";
    }
    String serviceIdentifier = Objects.toString(server.getServiceIdentifier(), "---");

    int port = server.getPort();
    String portString = port < 0 ? "---" : Integer.toString(port);

    nameLabel.setText(name);
    serviceIdentifierLabel.setText(serviceIdentifier);
    localv4AddressLabel.setText("Unknown");
    localv6AddressLabel.setText("Unknown");
    externalAddressLabel.setText(I18N.getText("ConnectionInfoDialog.discovering"));
    portLabel.setText(portString);

    if (!(panel.getButton("okButton") instanceof JButton okButton)) {
      throw new AssertionError("okButton should be a JButton");
    }
    okButton.addActionListener(e -> setVisible(false));

    setLayout(new GridLayout());
    if (!(getContentPane() instanceof JComponent contentPane)) {
      throw new AssertionError("contentPane should be a JComponent");
    }
    contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    add(panel);

    var serverAddresses = server.getServerAddresses();
    CompletableFuture<ServerAddress.Tcp> firstIpv4 =
        serverAddresses
            .localIpv4()
            .thenApply(
                addresses -> {
                  if (addresses.isEmpty()) return null;

                  return addresses.get(0);
                });
    CompletableFuture<ServerAddress.Tcp> firstIpv6 =
        serverAddresses
            .localIpv6()
            .thenApply(
                addresses -> {
                  if (addresses.isEmpty()) return null;

                  return addresses.get(0);
                });

    updateLabel(localv4AddressLabel, firstIpv4);
    updateLabel(localv6AddressLabel, firstIpv6);
    updateLabel(externalAddressLabel, serverAddresses.external());

    registerCopyButton(
        panel, "registryUriCopyButton", serverAddresses.registry(), ServerAddress::toUri);
    registerCopyButton(
        panel, "registryHttpUrlCopyButton", serverAddresses.registry(), ServerAddress::toHttpUrl);
    registerCopyButton(panel, "lanUriCopyButton", serverAddresses.lan(), ServerAddress::toUri);
    registerCopyButton(
        panel, "lanHttpUrlCopyButton", serverAddresses.lan(), ServerAddress::toHttpUrl);
    registerCopyButton(panel, "localIpv4UriCopyButton", firstIpv4, ServerAddress::toUri);
    registerCopyButton(panel, "localIpv4HttpUrlCopyButton", firstIpv4, ServerAddress::toHttpUrl);
    registerCopyButton(panel, "localIpv6UriCopyButton", firstIpv6, ServerAddress::toUri);
    registerCopyButton(panel, "localIpv6HttpUrlCopyButton", firstIpv6, ServerAddress::toHttpUrl);
    registerCopyButton(
        panel, "externalUriCopyButton", serverAddresses.external(), ServerAddress::toUri);
    registerCopyButton(
        panel, "externalHttpUrlCopyButton", serverAddresses.external(), ServerAddress::toHttpUrl);
  }

  private void updateLabel(
      @Nonnull JTextField label, @Nonnull CompletableFuture<ServerAddress.Tcp> addressFuture) {
    addressFuture.thenAccept(
        address -> {
          if (address != null) {
            SwingUtilities.invokeLater(() -> label.setText(address.address()));
          }
        });
  }

  private <T extends ServerAddress> void registerCopyButton(
      @Nonnull AbeillePanel panel,
      @Nonnull String buttonId,
      @Nonnull CompletableFuture<T> future,
      @Nonnull Function<T, URI> specToUri) {
    if (!(panel.getButton(buttonId) instanceof JButton button)) {
      return;
    }

    button.setEnabled(false); // TODO: Start disabled in form

    future.thenAccept(
        address -> {
          if (address != null) {
            button.setEnabled(true);
          }
        });

    button.addActionListener(
        e -> {
          future.thenAccept(
              connectionSpec -> {
                var url = specToUri.apply(connectionSpec).toString();
                Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(url), null);
              });
        });
  }

  @Override
  public void setVisible(boolean b) {
    if (b) {
      SwingUtil.centerOver(this, MapTool.getFrame());
    }
    super.setVisible(b);
  }
}
