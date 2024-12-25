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
package net.rptools.maptool.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.message.header.STAllHeader;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;
import org.jupnp.support.igd.callback.PortMappingAdd;
import org.jupnp.support.igd.callback.PortMappingDelete;
import org.jupnp.support.model.PortMapping;

/**
 * @author Phil Wright
 * @author Richard Maw - Rewritten to use jupnp
 */
public class UPnPUtil {
  private static final Logger log = LogManager.getLogger(UPnPUtil.class);

  private static final UDADeviceType INTERNET_GATEWAY_DEVICE_V1 =
      new UDADeviceType("InternetGatewayDevice", 1);
  private static final UDADeviceType INTERNET_GATEWAY_DEVICE_V2 =
      new UDADeviceType("InternetGatewayDevice", 2);
  private static final UDAServiceType WAN_IP_CONNECTION_V1 =
      new UDAServiceType("WANIPConnection", 1);
  private static final UDAServiceType WAN_IP_CONNECTION_V2 =
      new UDAServiceType("WANIPConnection", 2);
  private static final UDAServiceType WAN_PPP_CONNECTION_V1 =
      new UDAServiceType("WANPPPConnection", 1);

  private record MappingInfo(
      UpnpService upnpService, CompletableFuture<Boolean> somePortUnmapped) {}

  private static Map<Integer, MappingInfo> mappingServices = new HashMap<Integer, MappingInfo>();

  /**
   * Maps the provided port to a heuristically chosen address for every discovered IGD.
   *
   * @return true if any port was mapped within the timeout, false if none were discovered or
   *     weren't mappable within the timeout.
   */
  public static boolean openPort(int port) {
    UpnpService upnpService = new UpnpServiceImpl(new DefaultUpnpServiceConfiguration());
    upnpService.startup();

    var someDeviceFound = new CompletableFuture<Void>();
    var somePortMapped = new CompletableFuture<Void>();
    var somePortUnmapped = new CompletableFuture<Boolean>();
    var listener =
        new DefaultRegistryListener() {
          private record MappedServiceInfo(
              Service<?, ?> connectionService, PortMapping portMapping) {}

          private Map<RemoteDevice, MappedServiceInfo> mappedIgds = null;

          private Service<?, ?> getIgdService(RemoteDevice device) {
            var deviceType = device.getType();
            if (!deviceType.equals(INTERNET_GATEWAY_DEVICE_V1)
                && !deviceType.equals(INTERNET_GATEWAY_DEVICE_V2)) {
              return null;
            }

            Service<?, ?> connectionService = device.findService(WAN_IP_CONNECTION_V2);
            if (connectionService == null) {
              log.debug("Device {} does not have service: {}", device, WAN_IP_CONNECTION_V2);
              connectionService = device.findService(WAN_IP_CONNECTION_V1);
            }
            if (connectionService == null) {
              log.debug("Device {} does not have service: {}", device, WAN_IP_CONNECTION_V1);
              connectionService = device.findService(WAN_PPP_CONNECTION_V1);
            }
            if (connectionService == null) {
              log.debug("Device {} does not have service: {}", device, WAN_PPP_CONNECTION_V1);
            }

            return connectionService;
          }

          @Override
          public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            var connectionService = getIgdService(device);
            if (connectionService == null) {
              return;
            }
            var deviceIdentity = device.getIdentity();

            log.debug(
                "Added IGD {} with address {}",
                device,
                deviceIdentity.getDescriptorURL().getHost());

            // remoteDeviceAdded may be called multiple times for the same IGD
            // either because jupnp discovered it from multiple addresses
            // or because the service was brought down and reappeared.
            // Since it may or may not remember the port we must try to add it anyway.
            synchronized (this) {
              if (mappedIgds == null) {
                mappedIgds = new HashMap<RemoteDevice, MappedServiceInfo>();
              }
              someDeviceFound.complete(null);

              var portMapping =
                  new PortMapping(
                      port,
                      device.getIdentity().getDiscoveredOnLocalAddress().getHostAddress(),
                      PortMapping.Protocol.TCP,
                      "MapTool");
              new PortMappingAdd(
                  connectionService, registry.getUpnpService().getControlPoint(), portMapping) {
                @Override
                public void success(ActionInvocation invocation) {
                  log.debug("Mapped port {} on IGD {}", port, device);
                  mappedIgds.put(device, new MappedServiceInfo(connectionService, portMapping));
                  somePortMapped.complete(null);
                }

                @Override
                public void failure(
                    ActionInvocation invocation, UpnpResponse res, String defaultMsg) {
                  log.warn("Failed to map port {} on IGD {}: {}", port, device, defaultMsg);
                }
              }.run();
            }
          }

          @Override
          public void beforeShutdown(Registry registry) {
            log.debug("Shutting down port {} mapping service", port);
            // jupnp considers a device appearing to change IP address as a new device
            // and calls removed and added callbacks, and it may still have mappings after that
            // so we can't use remoteDeviceRemoved to remove already unmapped mappings
            // and have to just try removing everything we mapped
            for (var entry : mappedIgds.entrySet()) {
              var device = entry.getKey();
              var value = entry.getValue();
              new PortMappingDelete(
                  value.connectionService(),
                  registry.getUpnpService().getControlPoint(),
                  value.portMapping()) {
                @Override
                public void success(ActionInvocation invocation) {
                  log.debug("Unmapped port {} on IGD {}", port, device);
                  somePortUnmapped.complete(true);
                }

                @Override
                public void failure(
                    ActionInvocation invocation, UpnpResponse res, String defaultMsg) {
                  log.warn("Failed to unmap port {} on IGD {}: {}", port, device, defaultMsg);
                }
              }.run();
            }
          }
        };

    upnpService.getRegistry().addListener(listener);

    upnpService.getControlPoint().search(new STAllHeader());
    try {
      try {
        someDeviceFound.get(AppPreferences.upnpDiscoveryTimeout.get(), TimeUnit.MILLISECONDS);
      } catch (ExecutionException | InterruptedException | TimeoutException e) {
        MapTool.showError("msg.error.server.upnp.noigd");
        throw e;
      }
      try {
        somePortMapped.get(AppPreferences.upnpDiscoveryTimeout.get(), TimeUnit.MILLISECONDS);
      } catch (ExecutionException | InterruptedException | TimeoutException e) {
        MapTool.showError("UPnP: found some IGDs but no port mapping succeeded!?");
        throw e;
      }
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      upnpService.shutdown();
      return false;
    }
    mappingServices.put(port, new MappingInfo(upnpService, somePortUnmapped));
    return true;
  }

  /**
   * Unmap the provided port from discovered IGDs.
   *
   * @return true if any mapped ports were successfully unmapped or there were no mappings, false if
   *     there were mappings that couldn't be unmapped.
   */
  public static boolean closePort(int port) {
    if (!mappingServices.containsKey(port)) {
      return true;
    }

    var mappingInfo = mappingServices.get(port);
    mappingInfo.upnpService().shutdown();
    mappingServices.remove(port);
    return mappingInfo.somePortUnmapped().getNow(false);
  }
}
